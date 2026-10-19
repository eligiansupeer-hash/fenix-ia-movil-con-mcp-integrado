package com.fenix.mobilemcp.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenix.mobilemcp.llm.AiProviderConfig
import com.fenix.mobilemcp.llm.DiscoveredModel
import com.fenix.mobilemcp.llm.DynamicModelDiscovery
import com.fenix.mobilemcp.llm.FenixLlmClient
import com.fenix.mobilemcp.llm.LlmMessage
import com.fenix.mobilemcp.llm.LlmResponse
import com.fenix.mobilemcp.mcp.registry.FenixToolRegistry
import com.fenix.mobilemcp.memory.FenixMemoryDb
import com.fenix.mobilemcp.mcp.oauth.OAuthApprovalCoordinator
import com.fenix.mobilemcp.mcp.oauth.PendingApproval
import com.fenix.mobilemcp.security.SecureKeyStore
import com.fenix.mobilemcp.security.StreetImmunityChecker
import com.fenix.mobilemcp.data.repository.OAuthClientRepository
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import android.net.Uri
import com.fenix.mobilemcp.voice.FenixVoiceActivationManager
import com.fenix.mobilemcp.voice.VoiceState
import java.util.UUID
import javax.inject.Inject

import com.fenix.mobilemcp.termux.TermuxBridgeManager
import com.fenix.mobilemcp.termux.TermuxBridgeStatus

data class OutOfProjectRequest(
    val id: String = UUID.randomUUID().toString(),
    val requestedPath: String,
    val toolName: String
)

enum class AgentMode(val displayName: String, val icon: String, val description: String) {
    GENERAL("Modo General", "⚡", "Asistente universal y tareas rápidas"),
    PLAN("Modo Plan", "📋", "Planificación previa, análisis y no modificación impulsiva"),
    BUILD("Modo Build", "🔨", "Programación, ejecución y llamadas MCP")
}

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
    private val voiceActivationManager: FenixVoiceActivationManager,
    private val memoryDb: FenixMemoryDb,
    private val oauthApprovalCoordinator: OAuthApprovalCoordinator,
    private val oauthClientRepository: OAuthClientRepository,
    private val termuxBridgeManager: TermuxBridgeManager,
) : ViewModel() {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val _messages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val messages: StateFlow<List<ChatMessageUi>> = _messages.asStateFlow()

    private val _sessions = MutableStateFlow<List<FenixMemoryDb.SessionRecord>>(emptyList())
    val sessions: StateFlow<List<FenixMemoryDb.SessionRecord>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

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

    val pendingApprovals: StateFlow<List<PendingApproval>> = oauthApprovalCoordinator.observePending()

    private val _activeSkills = MutableStateFlow<Set<com.fenix.mobilemcp.mcp.registry.FenixSkillCategory>>(emptySet())
    val activeSkills: StateFlow<Set<com.fenix.mobilemcp.mcp.registry.FenixSkillCategory>> = _activeSkills.asStateFlow()
    
    private val _agentMode = MutableStateFlow(AgentMode.GENERAL)
    val agentMode: StateFlow<AgentMode> = _agentMode.asStateFlow()

    private val _projectUri = MutableStateFlow<Uri?>(null)
    val projectUri: StateFlow<Uri?> = _projectUri.asStateFlow()

    private val _projectName = MutableStateFlow<String?>(null)
    val projectName: StateFlow<String?> = _projectName.asStateFlow()

    private val _pendingOutOfProjectPermission = MutableStateFlow<OutOfProjectRequest?>(null)
    val pendingOutOfProjectPermission: StateFlow<OutOfProjectRequest?> = _pendingOutOfProjectPermission.asStateFlow()

    val voiceState: StateFlow<VoiceState> = voiceActivationManager.voiceState

    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    private val _termuxStatus = MutableStateFlow(TermuxBridgeStatus())
    val termuxStatus: StateFlow<TermuxBridgeStatus> = _termuxStatus.asStateFlow()

    fun getDirectTermuxDownloadUri(): Uri = termuxBridgeManager.getDirectDownloadUri()

    fun refreshTermuxStatus() {
        viewModelScope.launch {
            val status = termuxBridgeManager.queryStatus()
            _termuxStatus.value = status
            if (status.isBridgeActive) {
                refreshModels()
            }
        }
    }

    fun activateTermux() {
        viewModelScope.launch {
            termuxBridgeManager.activateTermux()
            // Reintentar sondeo luego de 1.5s para dar tiempo a arranque del proceso
            kotlinx.coroutines.delay(1500)
            refreshTermuxStatus()
        }
    }

    fun installOpenCode() {
        viewModelScope.launch {
            termuxBridgeManager.installOpenCode()
            refreshTermuxStatus()
        }
    }

    fun installAntigravity() {
        viewModelScope.launch {
            termuxBridgeManager.installAntigravity()
            refreshTermuxStatus()
        }
    }

    suspend fun startAntigravityLogin(slot: String): String? {
        val url = termuxBridgeManager.startAntigravityLogin(slot)
        refreshTermuxStatus()
        return url
    }

    fun logoutAntigravity(slot: String) {
        viewModelScope.launch {
            termuxBridgeManager.logoutAntigravity(slot)
            refreshTermuxStatus()
        }
    }

    fun selectAntigravitySlot(slot: String) {
        viewModelScope.launch {
            termuxBridgeManager.selectAntigravitySlot(slot)
            refreshTermuxStatus()
        }
    }

    fun toggleAutoFailover(enabled: Boolean) {
        viewModelScope.launch {
            termuxBridgeManager.toggleFailover(enabled)
            refreshTermuxStatus()
        }
    }

    fun clearTranscribedText() {
        _transcribedText.value = ""
    }
    
    fun setAgentMode(mode: AgentMode) {
        _agentMode.value = mode
    }
    
    fun toggleSkillCategory(category: com.fenix.mobilemcp.mcp.registry.FenixSkillCategory) {
        val current = _activeSkills.value.toMutableSet()
        if (current.contains(category)) {
            current.remove(category)
        } else {
            current.add(category)
        }
        _activeSkills.value = current
    }

    fun approveOAuthClient(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            oauthApprovalCoordinator.approve(id, System.currentTimeMillis())
        }
    }

    fun denyOAuthClient(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            oauthApprovalCoordinator.deny(id)
        }
    }

    fun updateClientPermissions(clientId: String, permissions: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            oauthClientRepository.updatePermissions(clientId, permissions)
        }
    }

    fun setProject(uri: Uri, name: String) {
        _projectUri.value = uri
        _projectName.value = name
    }

    fun requestOutOfProjectPermission(path: String, toolName: String) {
        _pendingOutOfProjectPermission.value = OutOfProjectRequest(requestedPath = path, toolName = toolName)
    }

    fun approveOutOfProjectPermission() {
        // Here you would typically resolve a deferred or notify the tool to proceed
        _pendingOutOfProjectPermission.value = null
    }

    fun denyOutOfProjectPermission() {
        // Here you would typically resolve a deferred with an error or notify the tool to stop
        _pendingOutOfProjectPermission.value = null
    }

    init {
        checkStreetImmunity()
        refreshConfiguredProviders()
        refreshModels()
        refreshTermuxStatus()

        viewModelScope.launch(Dispatchers.IO) {
            refreshSessionsInternal()
        }

        viewModelScope.launch {
            voiceActivationManager.transcribedText.collect { recognizedText ->
                if (recognizedText.isNotBlank()) {
                    _transcribedText.value = recognizedText
                }
            }
        }
    }

    private suspend fun refreshSessionsInternal(selectSessionId: String? = null) {
        val sessionList = memoryDb.listarSesiones()
        _sessions.value = sessionList
        val targetId = selectSessionId ?: sessionList.firstOrNull()?.id
        if (targetId != null) {
            _currentSessionId.value = targetId
            loadSessionInternal(targetId)
        } else {
            val newId = UUID.randomUUID().toString()
            val rec = memoryDb.crearSesion(newId, "Conversación principal")
            _sessions.value = listOf(rec)
            _currentSessionId.value = newId
            loadSessionInternal(newId)
        }
    }

    private fun loadSessionInternal(sessionId: String) {
        val entries = memoryDb.listarEntradas(sessionId)
        val uiMessages = entries.map { entry ->
            val toolCalls = if (entry.detalle.isNotBlank()) {
                deserializeToolCalls(entry.detalle)
            } else null
            ChatMessageUi(
                id = entry.id.toString(),
                role = entry.tipo,
                content = entry.evento,
                toolCalls = if (toolCalls.isNullOrEmpty()) null else toolCalls,
            )
        }
        _messages.value = uiMessages
    }

    fun loadSession(sessionId: String) {
        _currentSessionId.value = sessionId
        viewModelScope.launch(Dispatchers.IO) {
            loadSessionInternal(sessionId)
        }
    }

    fun createNewSession(titulo: String = "Nueva conversación") {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = UUID.randomUUID().toString()
            memoryDb.crearSesion(newId, titulo)
            _activeSkills.value = emptySet()
            refreshSessionsInternal(selectSessionId = newId)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryDb.eliminarSesion(sessionId)
            val remaining = memoryDb.listarSesiones()
            _sessions.value = remaining
            if (_currentSessionId.value == sessionId) {
                val nextId = remaining.firstOrNull()?.id
                if (nextId != null) {
                    _currentSessionId.value = nextId
                    loadSessionInternal(nextId)
                } else {
                    createNewSession()
                }
            }
        }
    }

    fun toggleVoiceListening() {
        if (voiceActivationManager.voiceState.value == VoiceState.LISTENING) {
            voiceActivationManager.stopListening()
        } else {
            voiceActivationManager.startListening(source = "UI_MIC")
        }
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
            val current = _selectedModel.value
            if (models.isNotEmpty() && (current == null || models.none { it.id == current.id && it.providerId == current.providerId })) {
                // Preferir proveedor con API Key configurada o Fénix Cloud Free antes que sondas locales
                _selectedModel.value = models.firstOrNull { !it.isLocal } ?: models.first()
            }
        }
    }

    fun selectModel(model: DiscoveredModel) {
        _selectedModel.value = model
    }

    suspend fun validateAndSaveApiKey(providerId: String, apiKey: String, customBaseUrl: String? = null): DynamicModelDiscovery.ValidationResult {
        if (providerId.startsWith("connector_")) {
            secureKeyStore.saveApiKey(providerId, apiKey)
            refreshConfiguredProviders()
            return DynamicModelDiscovery.ValidationResult.Success
        }
        
        val result = modelDiscovery.validateProviderKey(providerId, apiKey, customBaseUrl)
        if (result is DynamicModelDiscovery.ValidationResult.Success) {
            secureKeyStore.saveApiKey(providerId, apiKey)
            if (customBaseUrl != null) {
                secureKeyStore.saveApiKey("${providerId}_baseurl", customBaseUrl)
            }
            refreshConfiguredProviders()
            val provider = modelDiscovery.standardProviders.firstOrNull { it.id == providerId }
                ?: AiProviderConfig(
                    id = providerId,
                    displayName = "Custom Provider",
                    baseUrl = customBaseUrl ?: "",
                    requiresKey = true,
                    isLocal = customBaseUrl?.contains("127.0.0.1") == true || customBaseUrl?.contains("localhost") == true
                )
            val newModels = modelDiscovery.discoverModelsForProvider(provider)
            val current = _availableModels.value.filter { it.providerId != providerId }
            val updated = current + newModels
            _availableModels.value = updated
            if (_selectedModel.value == null && updated.isNotEmpty()) {
                _selectedModel.value = updated.first()
            }
        }
        return result
    }

    fun saveApiKey(providerId: String, apiKey: String) {
        viewModelScope.launch {
            validateAndSaveApiKey(providerId, apiKey)
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
        val currentId = _currentSessionId.value
        if (currentId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                memoryDb.writableDatabase.delete("entradas", "session_id=?", arrayOf(currentId))
            }
        }
        _messages.value = emptyList()
        _activeSkills.value = emptySet()
    }

    fun sendMessage(userText: String, attachedFileRef: String? = null) {
        val trimmed = userText.trim()
        if (trimmed.isBlank() && attachedFileRef == null || _isGenerating.value) return
        
        val messageContent = if (attachedFileRef != null) {
            if (trimmed.isNotBlank()) "$trimmed\n\n[Archivo adjunto: $attachedFileRef]" else "[Archivo adjunto: $attachedFileRef]"
        } else {
            trimmed
        }

        val currentModel = _selectedModel.value
        if (currentModel == null) {
            _messages.value = _messages.value + ChatMessageUi(
                role = "assistant",
                content = "No hay ningún modelo de IA seleccionado. Toca el ícono de la llave (🔑) arriba para configurar una API Key gratuita (Groq, Gemini, OpenRouter, DeepSeek) o selecciona Fénix Free Cloud.",
            )
            return
        }

        val activeSessionId = _currentSessionId.value ?: run {
            val newId = UUID.randomUUID().toString()
            memoryDb.crearSesion(newId, messageContent.take(30))
            _currentSessionId.value = newId
            newId
        }

        // Guardar mensaje de usuario en SQLite
        viewModelScope.launch(Dispatchers.IO) {
            memoryDb.agregarEntrada(
                sessionId = activeSessionId,
                agente = "user",
                tipo = "user",
                evento = messageContent,
            )
            // Si el título es genérico, actualizarlo con el resumen del prompt
            val ses = memoryDb.obtenerSesion(activeSessionId)
            if (ses != null && (ses.titulo == "Conversación principal" || ses.titulo.startsWith("Nueva conversación"))) {
                val smartTitle = if (messageContent.length > 32) messageContent.take(29) + "..." else messageContent
                memoryDb.actualizarSesionTitulo(activeSessionId, smartTitle)
                _sessions.value = memoryDb.listarSesiones()
            }
        }

        // Activación de skills a demanda: detecta intenciones del usuario para pre-activar la skill necesaria
        val detected = FenixToolRegistry.detectRelevantCategories(messageContent)
        if (detected.isNotEmpty()) {
            _activeSkills.value = _activeSkills.value + detected
        }

        val userMessage = ChatMessageUi(role = "user", content = messageContent)
        _messages.value = _messages.value + userMessage
        _isGenerating.value = true

        viewModelScope.launch {
            executeConversationTurn(currentModel, activeSessionId)
        }
    }

    companion object {
        private const val MAX_TOOL_ITERATIONS = 5
        private const val MAX_HISTORY_MESSAGES_FOR_LLM = 20
    }

    private suspend fun executeConversationTurn(model: DiscoveredModel, sessionId: String) {
        // Enviar ventana de contexto histórico (hasta los últimos 20 mensajes) para no desbordar cuota ni contexto
        val contextWindow = _messages.value.takeLast(MAX_HISTORY_MESSAGES_FOR_LLM)
        val workingHistory = contextWindow.map { msg ->
            LlmMessage(
                role = msg.role,
                content = msg.content,
            )
        }.toMutableList()

        var iteration = 0
        while (iteration < MAX_TOOL_ITERATIONS) {
            val allowTools = iteration < MAX_TOOL_ITERATIONS - 1
            when (val response = llmClient.sendChatCompletion(
                model,
                workingHistory,
                includeTools = allowTools,
                activeCategories = _activeSkills.value,
            )) {
                is LlmResponse.Text -> {
                    _messages.value = _messages.value + ChatMessageUi(
                        role = "assistant",
                        content = response.content,
                    )
                    viewModelScope.launch(Dispatchers.IO) {
                        memoryDb.agregarEntrada(
                            sessionId = sessionId,
                            agente = model.providerName,
                            tipo = "assistant",
                            evento = response.content,
                        )
                    }
                    _isGenerating.value = false
                    return
                }

                is LlmResponse.ToolCalls -> {
                    val toolUiList = mutableListOf<ToolCallUi>()
                    val toolMessages = mutableListOf<LlmMessage>()

                    for (call in response.calls) {
                        val argumentsObj = parseJsonArgs(call.argumentsJson)

                        // Si la IA autodescubre o activa una skill dinámicamente, registrarla en la sesión
                        if (call.functionName.contains("skills_discovery")) {
                            val action = (argumentsObj?.get("action") as? kotlinx.serialization.json.JsonPrimitive)?.content
                            val skillName = (argumentsObj?.get("skill") as? kotlinx.serialization.json.JsonPrimitive)?.content
                            if (action == "activate" && !skillName.isNullOrBlank()) {
                                val cat = com.fenix.mobilemcp.mcp.registry.FenixSkillCategory.fromId(skillName)
                                _activeSkills.value = _activeSkills.value + cat
                            }
                        }

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

                    val assistantNotice = "Ejecuté las siguientes herramientas locales:"
                    _messages.value = _messages.value + ChatMessageUi(
                        role = "assistant",
                        content = assistantNotice,
                        toolCalls = toolUiList,
                    )

                    // Autoguardado persistente en SQLite de la ejecución de herramientas
                    viewModelScope.launch(Dispatchers.IO) {
                        val toolCallsJson = serializeToolCalls(toolUiList)
                        memoryDb.agregarEntrada(
                            sessionId = sessionId,
                            agente = model.providerName,
                            tipo = "assistant",
                            evento = assistantNotice,
                            detalle = toolCallsJson,
                        )
                    }

                    workingHistory.add(
                        LlmMessage(
                            role = "assistant",
                            content = "",
                            toolCalls = response.calls,
                        ),
                    )
                    workingHistory.addAll(toolMessages)
                    iteration++
                }

                is LlmResponse.RateLimited -> {
                    _messages.value = _messages.value + ChatMessageUi(
                        role = "assistant",
                        content = "RATE_LIMITED_PLACEHOLDER"
                    )
                    _isGenerating.value = false
                    return
                }

                is LlmResponse.Error -> {
                    val fallbackModel = _availableModels.value.firstOrNull {
                        it.providerId != model.providerId && !it.isLocal && !modelDiscovery.isProviderInCooldown(it.providerId)
                    }
                    if (fallbackModel != null) {
                        _selectedModel.value = fallbackModel
                        executeConversationTurn(fallbackModel, sessionId)
                    } else {
                        _messages.value = _messages.value + ChatMessageUi(
                            role = "assistant",
                            content = "Error: ${response.message}",
                        )
                        _isGenerating.value = false
                    }
                    return
                }
            }
        }

        _isGenerating.value = false
    }

    private fun serializeToolCalls(calls: List<ToolCallUi>): String {
        return buildJsonArray {
            calls.forEach { call ->
                add(
                    buildJsonObject {
                        put("toolName", call.toolName)
                        put("arguments", call.arguments)
                        put("output", call.output)
                        put("durationMs", call.durationMs)
                        put("isSuccess", call.isSuccess)
                    },
                )
            }
        }.toString()
    }

    private fun deserializeToolCalls(jsonStr: String): List<ToolCallUi> {
        return try {
            val element = jsonParser.parseToJsonElement(jsonStr)
            if (element is JsonArray) {
                element.mapNotNull { item ->
                    if (item is JsonObject) {
                        ToolCallUi(
                            toolName = item["toolName"]?.jsonPrimitive?.content ?: "",
                            arguments = item["arguments"]?.jsonPrimitive?.content ?: "{}",
                            output = item["output"]?.jsonPrimitive?.content ?: "",
                            durationMs = item["durationMs"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                            isSuccess = item["isSuccess"]?.jsonPrimitive?.booleanOrNull ?: true,
                        )
                    } else {
                        null
                    }
                }
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
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

