package com.fenix.mobilemcp.llm

import com.fenix.mobilemcp.memory.FenixMemoryDb
import com.fenix.mobilemcp.security.SecureKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AiProviderConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val requiresKey: Boolean = true,
    val isLocal: Boolean = false,
)

data class DiscoveredModel(
    val id: String,
    val name: String,
    val providerId: String,
    val providerName: String,
    val isLocal: Boolean,
)

/**
 * Descubridor y enrutador dinámico de modelos de IA sin nombres hardcodeados.
 * Consulta `/v1/models` en caliente para proveedores configurados, almacena
 * en caché SQLite (FenixMemoryDb) con TTL de 24h y gestiona enfriamientos (cooldown)
 * automáticos ante errores 429 (Too Many Requests).
 */
@Singleton
class DynamicModelDiscovery @Inject constructor(
    private val keyStore: SecureKeyStore,
    private val memoryDb: FenixMemoryDb,
) {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(5, TimeUnit.SECONDS)
                    readTimeout(10, TimeUnit.SECONDS)
                }
            }
        }
    }

    private val localProbeClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(350, TimeUnit.MILLISECONDS)
                    readTimeout(500, TimeUnit.MILLISECONDS)
                }
            }
        }
    }

    val standardProviders = listOf(
        AiProviderConfig(
            id = "groq",
            displayName = "Groq (Llama/Mixtral/Whisper)",
            baseUrl = "https://api.groq.com/openai/v1",
        ),
        AiProviderConfig(
            id = "openrouter",
            displayName = "OpenRouter (Multi-model)",
            baseUrl = "https://openrouter.ai/api/v1",
        ),
        AiProviderConfig(
            id = "cerebras",
            displayName = "Cerebras Inference",
            baseUrl = "https://api.cerebras.ai/v1",
        ),
        AiProviderConfig(
            id = "mistral",
            displayName = "Mistral AI (La Plateforme)",
            baseUrl = "https://api.mistral.ai/v1",
        ),
        AiProviderConfig(
            id = "cohere",
            displayName = "Cohere (Compatibility API)",
            baseUrl = "https://api.cohere.ai/compatibility/v1",
        ),
        AiProviderConfig(
            id = "sambanova",
            displayName = "SambaNova Systems",
            baseUrl = "https://api.sambanova.ai/v1",
        ),
        AiProviderConfig(
            id = "deepseek",
            displayName = "DeepSeek AI",
            baseUrl = "https://api.deepseek.com/v1",
        ),
        AiProviderConfig(
            id = "gemini",
            displayName = "Google Gemini (OpenAI Endpoint)",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        ),
        AiProviderConfig(
            id = "pollinations_free",
            displayName = "Fénix Cloud Free (Sin API Key • Costo $0)",
            baseUrl = "https://text.pollinations.ai/openai",
            requiresKey = false,
            isLocal = false,
        ),
        AiProviderConfig(
            id = "local_ollama",
            displayName = "Motor Local (127.0.0.1:11434)",
            baseUrl = "http://127.0.0.1:11434/v1",
            requiresKey = false,
            isLocal = true,
        ),
        AiProviderConfig(
            id = "local_termux",
            displayName = "Puente Local (127.0.0.1:8081)",
            baseUrl = "http://127.0.0.1:8081/v1",
            requiresKey = false,
            isLocal = true,
        ),
    )

    /**
     * Comprueba si un proveedor está listo para ser usado (tiene clave o es local/gratuito).
     */
    fun isProviderReady(provider: AiProviderConfig): Boolean {
        return !provider.requiresKey || keyStore.hasApiKey(provider.id)
    }

    /**
     * Comprueba si un proveedor se encuentra actualmente en período de enfriamiento (cooldown).
     */
    fun isProviderInCooldown(providerId: String): Boolean {
        return memoryDb.isProviderInCooldown(providerId)
    }

    sealed class ValidationResult {
        data object Success : ValidationResult()
        data class Error(val message: String, val helpUrl: String? = null) : ValidationResult()
    }

    /**
     * Realiza una validación en tiempo real de la clave (o endpoint) consultando `/models`.
     * Retorna un ValidationResult que la UI puede usar para mostrar errores claros.
     */
    suspend fun validateProviderKey(providerId: String, apiKey: String, customBaseUrl: String? = null): ValidationResult = withContext(Dispatchers.IO) {
        val provider = standardProviders.firstOrNull { it.id == providerId } 
            ?: AiProviderConfig(
                id = providerId,
                displayName = "Custom Provider",
                baseUrl = customBaseUrl ?: return@withContext ValidationResult.Error("URL base requerida para proveedor personalizado."),
                requiresKey = true,
                isLocal = customBaseUrl?.contains("127.0.0.1") == true || customBaseUrl?.contains("localhost") == true
            )
            
        val modelsUrl = "${provider.baseUrl.trimEnd('/')}/models"
        val client = if (provider.isLocal) localProbeClient else httpClient
        
        try {
            val response = client.get(modelsUrl) {
                header("Accept", "application/json")
                if (apiKey.isNotBlank()) {
                    header("Authorization", "Bearer $apiKey")
                }
                if (provider.id == "openrouter") {
                    header("HTTP-Referer", "https://fenix.axiom.local")
                    header("X-Title", "Fenix Mobile MCP")
                }
            }

            return@withContext when (response.status) {
                HttpStatusCode.OK -> ValidationResult.Success
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> 
                    ValidationResult.Error(
                        "Clave inválida o no autorizada.",
                        getProviderDocsUrl(provider.id)
                    )
                HttpStatusCode.TooManyRequests -> 
                    ValidationResult.Error(
                        "Límite de cuota agotado en su proveedor. Recargue saldo o actualice su plan.",
                        getProviderDocsUrl(provider.id)
                    )
                else -> ValidationResult.Error("Error del proveedor (${response.status.value}): ${response.status.description}")
            }
        } catch (e: Exception) {
            return@withContext ValidationResult.Error("No se pudo contactar al proveedor. Verifique su conexión a internet o la URL.")
        }
    }
    
    private fun getProviderDocsUrl(providerId: String): String? {
        return when (providerId) {
            "groq" -> "https://console.groq.com/keys"
            "openrouter" -> "https://openrouter.ai/keys"
            "gemini" -> "https://aistudio.google.com/app/apikey"
            "deepseek" -> "https://platform.deepseek.com/api_keys"
            "cerebras" -> "https://cloud.cerebras.ai/"
            "sambanova" -> "https://cloud.sambanova.ai/"
            "mistral" -> "https://console.mistral.ai/api-keys/"
            else -> null
        }
    }

    /**
     * Descubre modelos dinámicamente consultando `/models` en el proveedor.
     */
    suspend fun discoverModelsForProvider(provider: AiProviderConfig): List<DiscoveredModel> =
        withContext(Dispatchers.IO) {
            if (provider.requiresKey && !keyStore.hasApiKey(provider.id)) {
                return@withContext emptyList()
            }

            if (provider.id == "pollinations_free") {
                return@withContext listOf(
                    DiscoveredModel(
                        id = "openai",
                        name = "Fénix Free Cloud (GPT-4o-mini Bridge)",
                        providerId = provider.id,
                        providerName = provider.displayName,
                        isLocal = false,
                    ),
                    DiscoveredModel(
                        id = "mistral",
                        name = "Fénix Free Cloud (Mistral Bridge)",
                        providerId = provider.id,
                        providerName = provider.displayName,
                        isLocal = false,
                    ),
                )
            }

            // Si está en enfriamiento por 429 (solo para proveedores cloud), retornar la caché previa si existe
            if (!provider.isLocal && memoryDb.isProviderInCooldown(provider.id)) {
                val cached = getCachedModels(provider)
                if (cached.isNotEmpty()) return@withContext cached
            }

            val apiKey = if (provider.requiresKey) keyStore.getApiKey(provider.id) else null
            val modelsUrl = "${provider.baseUrl.trimEnd('/')}/models"
            val client = if (provider.isLocal) localProbeClient else httpClient

            try {
                val response = client.get(modelsUrl) {
                    header("Accept", "application/json")
                    if (!apiKey.isNullOrBlank()) {
                        header("Authorization", "Bearer $apiKey")
                    }
                    if (provider.id == "openrouter") {
                        header("HTTP-Referer", "https://fenix.axiom.local")
                        header("X-Title", "Fenix Mobile MCP")
                    }
                }

                if (response.status == HttpStatusCode.TooManyRequests) {
                    memoryDb.setProviderCooldown(provider.id, 60L, "HTTP 429 Rate Limit")
                    return@withContext if (provider.isLocal) emptyList() else getCachedModels(provider)
                }

                if (response.status == HttpStatusCode.OK) {
                    val responseBody = response.bodyAsText()
                    val models = parseModelsResponse(responseBody, provider)
                    if (models.isNotEmpty()) {
                        if (!provider.isLocal) {
                            val expireIso = java.time.Instant.now().plusSeconds(24 * 3600L).toString()
                            memoryDb.kvGuardar(
                                clave = "models_${provider.id}",
                                ambito = "ai_models",
                                valor = responseBody,
                                categoria = "ai",
                                expiraEn = expireIso,
                                autor = "dynamic_discovery",
                            )
                        }
                        memoryDb.reportProviderSuccess(provider.id)
                        return@withContext models
                    }
                } else {
                    memoryDb.reportProviderFailure(
                        provider.id,
                        "HTTP ${response.status.value}: ${response.status.description}",
                    )
                }
            } catch (e: Exception) {
                memoryDb.reportProviderFailure(provider.id, e.message ?: "Conexión fallida")
            }

            // CRÍTICO: Si un servidor local (127.0.0.1) no está encendido en este instante,
            // JAMÁS devolver modelos cacheados viejos porque el puerto está cerrado.
            if (provider.isLocal) {
                emptyList()
            } else {
                getCachedModels(provider)
            }
        }

    /**
     * Descubre todos los modelos disponibles en paralelo en todos los proveedores configurados.
     */
    suspend fun discoverAllAvailableModels(): List<DiscoveredModel> = coroutineScope {
        val readyProviders = standardProviders.filter { isProviderReady(it) }
        val deferreds = readyProviders.map { provider ->
            async { discoverModelsForProvider(provider) }
        }
        deferreds.awaitAll().flatten()
    }

    private fun parseModelsResponse(jsonText: String, provider: AiProviderConfig): List<DiscoveredModel> {
        return try {
            val root = jsonParser.parseToJsonElement(jsonText).jsonObject
            val dataArray = root["data"]?.jsonArray ?: return emptyList()
            dataArray.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                // Filtrar modelos no conversacionales/TTS/embeddings si el ID lo indica claramente
                if (id.contains("whisper") || id.contains("embed") || id.contains("moderation")) {
                    null
                } else {
                    val displayName = if (id.contains("/")) {
                        id.substringAfterLast("/").removeSuffix(".gguf")
                    } else {
                        id
                    }
                    DiscoveredModel(
                        id = id,
                        name = displayName,
                        providerId = provider.id,
                        providerName = provider.displayName,
                        isLocal = provider.isLocal,
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getCachedModels(provider: AiProviderConfig): List<DiscoveredModel> {
        val cachedJson = memoryDb.kvLeer("models_${provider.id}", "ai_models") ?: return emptyList()
        return parseModelsResponse(cachedJson, provider)
    }
}

