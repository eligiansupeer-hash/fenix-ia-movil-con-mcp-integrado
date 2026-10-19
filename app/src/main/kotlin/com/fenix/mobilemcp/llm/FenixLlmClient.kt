package com.fenix.mobilemcp.llm

import com.fenix.mobilemcp.mcp.registry.FenixToolRegistry
import com.fenix.mobilemcp.memory.FenixMemoryDb
import com.fenix.mobilemcp.privacy.LocalNerSanitizer
import com.fenix.mobilemcp.security.SecureKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class LlmMessage(
    val role: String,
    val content: String,
    val toolCallId: String? = null,
    val toolCalls: List<LlmToolCall>? = null,
)

data class LlmToolCall(
    val id: String,
    val functionName: String,
    val argumentsJson: String,
)

sealed class LlmResponse {
    data class Text(val content: String) : LlmResponse()
    data class ToolCalls(val calls: List<LlmToolCall>) : LlmResponse()
    data class RateLimited(val providerId: String, val message: String) : LlmResponse()
    data class Error(val message: String) : LlmResponse()
}

/**
 * Cliente LLM multi-proveedor compatible con OpenAI Chat Completions API y llamadas a herramientas (Tool Calling).
 */
@Singleton
class FenixLlmClient @Inject constructor(
    private val keyStore: SecureKeyStore,
    private val memoryDb: FenixMemoryDb,
    private val modelDiscovery: DynamicModelDiscovery,
    private val nerSanitizer: LocalNerSanitizer,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(20, TimeUnit.SECONDS)
                    readTimeout(120, TimeUnit.SECONDS)
                }
            }
        }
    }

    suspend fun sendChatCompletion(
        model: DiscoveredModel,
        conversation: List<LlmMessage>,
        includeTools: Boolean = true,
        activeCategories: Set<com.fenix.mobilemcp.mcp.registry.FenixSkillCategory>? = null,
    ): LlmResponse = withContext(Dispatchers.IO) {
        val provider = modelDiscovery.standardProviders.firstOrNull { it.id == model.providerId }
            ?: return@withContext LlmResponse.Error("Proveedor no encontrado: ${model.providerId}")

        val apiKey = if (provider.requiresKey) keyStore.getApiKey(provider.id) else null
        val completionsUrl = "${provider.baseUrl.trimEnd('/')}/chat/completions"

        val messagesJsonArray = buildJsonArray {
            // Mensaje de sistema Fénix Soberano con arquitectura de Skills a Demanda
            add(
                buildJsonObject {
                    put("role", "system")
                    put(
                        "content",
                        "Eres Fénix IA, el asistente de inteligencia artificial personal y soberano en Android. " +
                            "Operas con una arquitectura de habilidades a demanda para proteger tu contexto y optimizar recursos. " +
                            "Responde con precisión, concisión y en español. " +
                            "Utiliza las herramientas provistas cuando se requiera interactuar con el dispositivo, archivos, memoria persistente o red. " +
                            "Si necesitas una capacidad que no esté en tus herramientas activas, utiliza 'fenix_skills_discovery' para explorar o activar la habilidad requerida.",
                    )
                },
            )

            // Mensajes de la conversación (sanitizados con LocalNerSanitizer antes de salir a proveedores cloud)
            conversation.forEach { msg ->
                val safeContent = if (provider.isLocal) msg.content else nerSanitizer.sanitize(msg.content)
                add(
                    buildJsonObject {
                        put("role", msg.role)
                        put("content", safeContent)
                        msg.toolCallId?.let { put("tool_call_id", it) }
                        msg.toolCalls?.let { calls ->
                            put(
                                "tool_calls",
                                buildJsonArray {
                                    calls.forEach { call ->
                                        val safeArgs = if (provider.isLocal) call.argumentsJson else nerSanitizer.sanitize(call.argumentsJson)
                                        add(
                                            buildJsonObject {
                                                put("id", call.id)
                                                put("type", "function")
                                                put(
                                                    "function",
                                                    buildJsonObject {
                                                        put("name", call.functionName)
                                                        put("arguments", safeArgs)
                                                    },
                                                )
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    },
                )
            }
        }

        val requestPayload = buildJsonObject {
            put("model", model.id)
            put("messages", messagesJsonArray)
            if (includeTools) {
                val tools = FenixToolRegistry.toOpenAiToolDefinitions(activeCategories = activeCategories)
                if (tools.isNotEmpty()) {
                    put("tools", JsonArray(tools))
                    put("tool_choice", "auto")
                }
            }
        }

        try {
            val response = httpClient.post(completionsUrl) {
                contentType(ContentType.Application.Json)
                if (!apiKey.isNullOrBlank()) {
                    header("Authorization", "Bearer $apiKey")
                }
                if (provider.id == "openrouter") {
                    header("HTTP-Referer", "https://fenix.axiom.local")
                    header("X-Title", "Fenix Mobile MCP")
                }
                setBody(requestPayload.toString())
            }

            if (response.status == HttpStatusCode.TooManyRequests || response.status == HttpStatusCode.PaymentRequired) {
                val reason = if (response.status == HttpStatusCode.TooManyRequests) "HTTP 429 Rate Limit" else "HTTP 402 Payment Required"
                memoryDb.setProviderCooldown(provider.id, 60L, reason)
                return@withContext LlmResponse.RateLimited(provider.id, "Limite de cuota excedido en ${provider.displayName}")
            }

            if (response.status == HttpStatusCode.OK) {
                memoryDb.reportProviderSuccess(provider.id)
                val bodyText = response.bodyAsText()
                val root = json.parseToJsonElement(bodyText).jsonObject
                val choices = root["choices"]?.jsonArray
                val firstChoice = choices?.firstOrNull()?.jsonObject
                val messageObj = firstChoice?.get("message")?.jsonObject

                if (messageObj == null) {
                    return@withContext LlmResponse.Error("Respuesta vacía o formato inesperado")
                }

                val toolCallsElement = messageObj["tool_calls"]?.jsonArray
                if (toolCallsElement != null && toolCallsElement.isNotEmpty()) {
                    val calls = toolCallsElement.mapNotNull { elem ->
                        val obj = elem.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.content ?: "call_${System.currentTimeMillis()}"
                        val fn = obj["function"]?.jsonObject ?: return@mapNotNull null
                        val fnName = fn["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val rawArgs = fn["arguments"]
                        val fnArgs = when (rawArgs) {
                            is JsonPrimitive -> rawArgs.content
                            is JsonObject -> rawArgs.toString()
                            null -> "{}"
                            else -> rawArgs.toString()
                        }
                        LlmToolCall(id, fnName, fnArgs)
                    }
                    return@withContext LlmResponse.ToolCalls(calls)
                }

                val textContent = (messageObj["content"] as? JsonPrimitive)?.content ?: ""
                return@withContext LlmResponse.Text(textContent)
            } else {
                // Si un modelo SLM local o endpoint sin soporte de tools devuelve 400/500 con includeTools=true, reintentar sin tools
                if (includeTools && (provider.isLocal || response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.InternalServerError)) {
                    return@withContext sendChatCompletion(model, conversation, includeTools = false)
                }
                memoryDb.reportProviderFailure(provider.id, "HTTP ${response.status.value}")
                val userErrorMessage = when (response.status) {
                    HttpStatusCode.InternalServerError -> "Error interno del servidor. Por favor, intenta de nuevo mas tarde."
                    HttpStatusCode.BadRequest -> "Solicitud invalida. Verifica los parametros."
                    HttpStatusCode.Unauthorized -> "No autorizado. Verifica tu API Key."
                    else -> "Hubo un error de comunicacion (${response.status.value}). Por favor, intenta nuevamente."
                }
                return@withContext LlmResponse.Error(userErrorMessage)
            }
        } catch (e: Exception) {
            if (includeTools && provider.isLocal) {
                return@withContext sendChatCompletion(model, conversation, includeTools = false)
            }
            memoryDb.reportProviderFailure(provider.id, e.message ?: "Fallo de conexión")
            return@withContext LlmResponse.Error("Error de comunicación con ${provider.displayName}: ${e.message}")
        }
    }
}

