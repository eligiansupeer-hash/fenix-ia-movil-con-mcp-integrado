package com.fenix.mobilemcp.llm

import com.fenix.mobilemcp.mcp.registry.FenixToolRegistry
import com.fenix.mobilemcp.memory.FenixMemoryDb
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
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(45, TimeUnit.SECONDS)
                }
            }
        }
    }

    suspend fun sendChatCompletion(
        model: DiscoveredModel,
        conversation: List<LlmMessage>,
        includeTools: Boolean = true,
    ): LlmResponse = withContext(Dispatchers.IO) {
        val provider = modelDiscovery.standardProviders.firstOrNull { it.id == model.providerId }
            ?: return@withContext LlmResponse.Error("Proveedor no encontrado: ${model.providerId}")

        val apiKey = if (provider.requiresKey) keyStore.getApiKey(provider.id) else null
        val completionsUrl = "${provider.baseUrl.trimEnd('/')}/chat/completions"

        val messagesJsonArray = buildJsonArray {
            // Mensaje de sistema Fénix
            add(
                buildJsonObject {
                    put("role", "system")
                    put(
                        "content",
                        "Eres Fénix, el asistente de inteligencia artificial personal y soberano en Android. " +
                            "Tienes capacidades de ejecución local a través del MCP integrado. " +
                            "Responde con precisión, concisión y en español. " +
                            "Utiliza las herramientas provistas cuando necesites acceder al estado del dispositivo, " +
                            "guardar o recuperar memorias persistentes, o consultar zonas y métricas.",
                    )
                },
            )

            // Mensajes de la conversación
            conversation.forEach { msg ->
                add(
                    buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                        msg.toolCallId?.let { put("tool_call_id", it) }
                        msg.toolCalls?.let { calls ->
                            put(
                                "tool_calls",
                                buildJsonArray {
                                    calls.forEach { call ->
                                        add(
                                            buildJsonObject {
                                                put("id", call.id)
                                                put("type", "function")
                                                put(
                                                    "function",
                                                    buildJsonObject {
                                                        put("name", call.functionName)
                                                        put("arguments", call.argumentsJson)
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
                val tools = FenixToolRegistry.toOpenAiToolDefinitions()
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
                setBody(requestPayload.toString())
            }

            if (response.status == HttpStatusCode.TooManyRequests) {
                memoryDb.setProviderCooldown(provider.id, 60L, "HTTP 429 Rate Limit")
                return@withContext LlmResponse.RateLimited(provider.id, "Límite de tasa excedido en ${provider.displayName}")
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
                        val fnArgs = fn["arguments"]?.jsonPrimitive?.content ?: "{}"
                        LlmToolCall(id, fnName, fnArgs)
                    }
                    return@withContext LlmResponse.ToolCalls(calls)
                }

                val textContent = messageObj["content"]?.jsonPrimitive?.content ?: ""
                return@withContext LlmResponse.Text(textContent)
            } else {
                val errorBody = response.bodyAsText()
                memoryDb.reportProviderFailure(provider.id, "HTTP ${response.status.value}")
                return@withContext LlmResponse.Error("Error HTTP ${response.status.value}: $errorBody")
            }
        } catch (e: Exception) {
            memoryDb.reportProviderFailure(provider.id, e.message ?: "Fallo de conexión")
            return@withContext LlmResponse.Error("Error de comunicación con ${provider.displayName}: ${e.message}")
        }
    }
}

