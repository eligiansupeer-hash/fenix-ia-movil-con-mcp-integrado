package com.fenix.mobilemcp.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenix.mobilemcp.llm.DiscoveredModel
import com.fenix.mobilemcp.llm.DynamicModelDiscovery
import com.fenix.mobilemcp.llm.FenixLlmClient
import com.fenix.mobilemcp.llm.LlmMessage
import com.fenix.mobilemcp.llm.LlmResponse
import com.fenix.mobilemcp.mcp.registry.FenixToolRegistry
import com.fenix.mobilemcp.security.SecureKeyStore
import com.fenix.mobilemcp.security.StreetImmunityChecker
import com.fenix.mobilemcp.security.StreetImmunityReport
import com.fenix.mobilemcp.ui.components.ToolCallUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import javax.inject.Inject

data class ChatMessageUi(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val toolCalls: List<ToolCallUi>? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val modelDiscovery: DynamicModelDiscovery,
    private val llmClient: FenixLlmClient,
    private val secureKeyStore: SecureKeyStore,
    private val streetImmunityChecker: StreetImmunityChecker,
) : ViewModel() {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val _messages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val messages: StateFlow<List<ChatMessageUi>> = _messages.asStateFlow()

    private val _availableModels = MutableStateFlow<List<DiscoveredModel>>(emptyList())
    val availableModels: StateFlow<List<DiscoveredModel>> = _availableModels.asStateFlow()

    private val _selectedModel = MutableStateFlow<DiscoveredModel?>(null)
    val selectedModel: StateFlow<DiscoveredModel?> = _selectedModel.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streetImmunity = MutableStateFlow<StreetImmunityReport?>(null)
    val streetImmunity: StateFlow<StreetImmunityReport?> = _streetImmunity.asStateFlow()

    private val _configuredProviders = MutableStateFlow<Set<String>>(emptySet())
    val configuredProviders: StateFlow<Set<String>> = _configuredProviders.asStateFlow()

    init {
        checkStreetImmunity()
        refreshConfiguredProviders()
        refreshModels()
    }

    fun checkStreetImmunity() {
        viewModelScope.launch(Dispatchers.IO) {
            val report = streetImmunityChecker.auditStreetImmunity()
            _streetImmunity.value = report
        }
    }

    fun refreshConfiguredProviders() {
        _configuredProviders.value = secureKeyStore.listConfiguredProviders().toSet()
    }

    fun refreshModels() {
        viewModelScope.launch {
            val models = modelDiscovery.discoverAllAvailableModels()
            _availableModels.value = models
            if (_selectedModel.value == null && models.isNotEmpty()) {
                // Preferir modelo local o el primero disponible
                _selectedModel.value = models.firstOrNull { it.isLocal } ?: models.first()
            }
        }
    }

    fun selectModel(model: DiscoveredModel) {
        _selectedModel.value = model
    }

    fun saveApiKey(providerId: String, apiKey: String) {
        secureKeyStore.saveApiKey(providerId, apiKey)
        refreshConfiguredProviders()
        viewModelScope.launch {
            val provider = modelDiscovery.standardProviders.firstOrNull { it.id == providerId }
            if (provider != null) {
                val newModels = modelDiscovery.discoverModelsForProvider(provider)
                val current = _availableModels.value.filter { it.providerId != providerId }
                val updated = current + newModels
                _availableModels.value = updated
                if (_selectedModel.value == null && updated.isNotEmpty()) {
                    _selectedModel.value = updated.first()
                }
            }
        }
    }

    fun deleteApiKey(providerId: String) {
        secureKeyStore.deleteApiKey(providerId)
        refreshConfiguredProviders()
        _availableModels.value = _availableModels.value.filter { it.providerId != providerId }
        if (_selectedModel.value?.providerId == providerId) {
            _selectedModel.value = _availableModels.value.firstOrNull()
        }
    }

    fun clearConversation() {
        _messages.value = emptyList()
    }

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isBlank() || _isGenerating.value) return

        val currentModel = _selectedModel.value
        if (currentModel == null) {
            _messages.value = _messages.value + ChatMessageUi(
                role = "assistant",
                content = "No hay ningún modelo de IA configurado. Por favor configure una clave de API (Groq, OpenRouter, SambaNova, DeepSeek o Gemini) o inicie el servidor local en Termux.",
            )
            return
        }

        val userMessage = ChatMessageUi(role = "user", content = trimmed)
        _messages.value = _messages.value + userMessage
        _isGenerating.value = true

        viewModelScope.launch {
            executeConversationTurn(currentModel)
        }
    }

    private suspend fun executeConversationTurn(model: DiscoveredModel) {
        val conversationHistory = _messages.value.map { msg ->
            LlmMessage(
                role = msg.role,
                content = msg.content,
            )
        }

        when (val response = llmClient.sendChatCompletion(model, conversationHistory)) {
            is LlmResponse.Text -> {
                _messages.value = _messages.value + ChatMessageUi(
                    role = "assistant",
                    content = response.content,
                )
                _isGenerating.value = false
            }

            is LlmResponse.ToolCalls -> {
                // Ejecución de herramientas in-memory
                val toolUiList = mutableListOf<ToolCallUi>()
                val toolMessages = mutableListOf<LlmMessage>()

                for (call in response.calls) {
                    val argumentsObj = parseJsonArgs(call.argumentsJson)
                    val execResult = FenixToolRegistry.executeInProcess(call.functionName, argumentsObj)

                    toolUiList.add(
                        ToolCallUi(
                            toolName = call.functionName,
                            arguments = call.argumentsJson,
                            output = execResult.outputText,
                            durationMs = execResult.durationMs,
                            isSuccess = execResult.isSuccess,
                        ),
                    )

                    toolMessages.add(
                        LlmMessage(
                            role = "tool",
                            content = execResult.outputText,
                            toolCallId = call.id,
                        ),
                    )
                }

                // Añadir mensaje con tarjetas de herramientas
                _messages.value = _messages.value + ChatMessageUi(
                    role = "assistant",
                    content = "Ejecuté las siguientes herramientas locales:",
                    toolCalls = toolUiList,
                )

                // Segunda llamada al LLM con los resultados de las herramientas
                val updatedHistory = conversationHistory + LlmMessage(
                    role = "assistant",
                    content = "",
                    toolCalls = response.calls,
                ) + toolMessages

                when (val followUp = llmClient.sendChatCompletion(model, updatedHistory, includeTools = false)) {
                    is LlmResponse.Text -> {
                        _messages.value = _messages.value + ChatMessageUi(
                            role = "assistant",
                            content = followUp.content,
                        )
                    }
                    is LlmResponse.Error -> {
                        _messages.value = _messages.value + ChatMessageUi(
                            role = "assistant",
                            content = "Error al sintetizar respuesta final: ${followUp.message}",
                        )
                    }
                    else -> Unit
                }

                _isGenerating.value = false
            }

            is LlmResponse.RateLimited -> {
                // Auto-rotación de proveedor por 429
                val nextModel = _availableModels.value.firstOrNull { it.providerId != model.providerId }
                if (nextModel != null) {
                    _messages.value = _messages.value + ChatMessageUi(
                        role = "assistant",
                        content = "Límite de tasa (429) en ${model.providerName}. Rotando automáticamente a ${nextModel.providerName} (${nextModel.id})...",
                    )
                    _selectedModel.value = nextModel
                    executeConversationTurn(nextModel)
                } else {
                    _messages.value = _messages.value + ChatMessageUi(
                        role = "assistant",
                        content = "Límite de tasa (429) alcanzado y no hay proveedores alternativos disponibles. Espere 60 segundos.",
                    )
                    _isGenerating.value = false
                }
            }

            is LlmResponse.Error -> {
                _messages.value = _messages.value + ChatMessageUi(
                    role = "assistant",
                    content = "Error: ${response.message}",
                )
                _isGenerating.value = false
            }
        }
    }

    private fun parseJsonArgs(jsonString: String): JsonObject? {
        return try {
            jsonParser.parseToJsonElement(jsonString).jsonObject
        } catch (_: Exception) {
            null
        }
    }
}

