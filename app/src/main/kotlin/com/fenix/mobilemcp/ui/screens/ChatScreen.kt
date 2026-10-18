@file:Suppress("FunctionNaming", "LongMethod")

package com.fenix.mobilemcp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fenix.mobilemcp.llm.DiscoveredModel
import com.fenix.mobilemcp.security.StreetImmunityReport
import com.fenix.mobilemcp.ui.components.ToolCallCard
import com.fenix.mobilemcp.ui.components.ModesBar
import com.fenix.mobilemcp.ui.components.CompactBar
import com.fenix.mobilemcp.ui.components.QuotaSolutionCard
import com.fenix.mobilemcp.ui.viewmodels.ChatMessageUi
import com.fenix.mobilemcp.ui.viewmodels.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streetImmunity by viewModel.streetImmunity.collectAsStateWithLifecycle()
    val configuredProviders by viewModel.configuredProviders.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val activeSkills by viewModel.activeSkills.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showModelDropdown by remember { mutableStateOf(false) }
    var showKeyVaultDialog by remember { mutableStateOf(false) }
    var showImmunityDialog by remember { mutableStateOf(false) }
    var showSessionsDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Barra Superior Fénix IA Soberana
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Brush.radialGradient(
                                    listOf(
                                        Color(0xFFFFB300),
                                        Color(0xFFFF5722),
                                        Color(0xFFD50000),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Fénix IA Logo",
                            modifier = Modifier.size(18.dp),
                            tint = Color.White,
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Fénix IA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showModelDropdown = true },
                    ) {
                        Text(
                            text = selectedModel?.let { if (it.isLocal) "${it.name} (Local)" else it.name } ?: "Seleccionar modelo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = "Desplegar modelos",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    DropdownMenu(
                        expanded = showModelDropdown,
                        onDismissRequest = { showModelDropdown = false },
                    ) {
                        if (availableModels.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Sin modelos disponibles (Configure llaves)") },
                                onClick = {
                                    showModelDropdown = false
                                    showKeyVaultDialog = true
                                },
                            )
                        } else {
                            availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.name, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${model.providerName}${if (model.isLocal) " (Local)" else ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectModel(model)
                                        showModelDropdown = false
                                    },
                                    trailingIcon = if (model.id == selectedModel?.id) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
            },
            actions = {
                // Escudo de Inmunidad de Calle
                IconButton(onClick = {
                    viewModel.checkStreetImmunity()
                    showImmunityDialog = true
                }) {
                    val isSafe = streetImmunity?.isFullyImmune == true
                    Icon(
                        imageVector = if (isSafe) Icons.Default.Security else Icons.Default.Warning,
                        contentDescription = "Estado Inmunidad Calle",
                        tint = if (isSafe) Color(0xFF2E7D32) else Color(0xFFF57C00),
                    )
                }

                // Configuración de llaves de API (Verde si hay al menos una API, Rojo si no hay ninguna)
                val hasConfiguredApis = configuredProviders.isNotEmpty()
                IconButton(onClick = { showKeyVaultDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Bóveda de Claves",
                        tint = if (hasConfiguredApis) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                    )
                }

                // Gestor de Sesiones y Memoria persistente
                IconButton(onClick = { showSessionsDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = "Sesiones y Memoria",
                    )
                }

                // Limpiar conversación
                IconButton(onClick = { viewModel.clearConversation() }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Limpiar Chat",
                    )
                }
            },
        )

        HorizontalDivider()

        // Lista de mensajes
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Logotipo central Fénix IA Soberana
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Brush.radialGradient(
                                    listOf(
                                        Color(0xFFFFD54F),
                                        Color(0xFFFF7043),
                                        Color(0xFFD84315),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Fénix Soberana",
                            modifier = Modifier.size(42.dp),
                            tint = Color.White,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Fénix IA Soberana",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Habilidades a demanda con costo 0, ejecución local y soberanía de datos.\nEscribe tu instrucción para activar herramientas automáticamente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = "Habilidades del Sistema (activación a demanda):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = "📁 Archivos   •   🧠 Memoria   •   📱 Dispositivo\n🛡️ Seguridad   •   🌐 Red Zero-Trust   •   ⚡ Accesibilidad",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            lineHeight = 20.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatMessageBubble(message = msg)
                    }

                    if (isGenerating) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Fénix procesando...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Barra inferior de entrada
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Mensaje para Fénix...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
            )

            Spacer(Modifier.width(8.dp))

            // Botón de activación F-Voz (Micrófono)
            IconButton(
                onClick = { viewModel.toggleVoiceListening() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (voiceState == com.fenix.mobilemcp.voice.VoiceState.LISTENING) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "F-Voz",
                    tint = if (voiceState == com.fenix.mobilemcp.voice.VoiceState.LISTENING) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = {
                    val text = inputText
                    inputText = ""
                    viewModel.sendMessage(text)
                },
                enabled = inputText.isNotBlank() && !isGenerating,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank() && !isGenerating) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = if (inputText.isNotBlank() && !isGenerating) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }

    // Diálogo de Bóveda de Claves
    if (showKeyVaultDialog) {
        KeyVaultDialog(
            configuredProviders = configuredProviders,
            onSaveKey = { provider, key -> viewModel.saveApiKey(provider, key) },
            onDeleteKey = { provider -> viewModel.deleteApiKey(provider) },
            onDismiss = { showKeyVaultDialog = false },
        )
    }

    // Diálogo de Inmunidad Calle
    if (showImmunityDialog) {
        ImmunityReportDialog(
            report = streetImmunity,
            onDismiss = { showImmunityDialog = false },
        )
    }

    // Diálogo de Sesiones Persistentes y Memoria
    if (showSessionsDialog) {
        SessionsDialog(
            sessions = sessions,
            currentSessionId = currentSessionId,
            onSelectSession = { id ->
                viewModel.loadSession(id)
                showSessionsDialog = false
            },
            onNewSession = {
                viewModel.createNewSession()
                showSessionsDialog = false
            },
            onDeleteSession = { id ->
                viewModel.deleteSession(id)
            },
            onDismiss = { showSessionsDialog = false },
        )
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessageUi) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (message.content == "RATE_LIMITED_PLACEHOLDER") {
            QuotaSolutionCard(
                onObtainKeyClick = { /* Accion para obtener key */ },
                onChangeProviderClick = { /* Accion de rotacion de proveedor */ },
                onWaitClick = { /* Esperar restablecimiento */ },
                onAddProviderClick = { /* Agregar proveedor */ }
            )
        } else {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp,
                ),
                color = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.95f),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (isUser) "Tú" else "Fénix",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )

                    // Renderizar llamadas a herramientas si existen
                    message.toolCalls?.let { tools ->
                        Spacer(Modifier.height(8.dp))
                        tools.forEach { toolCall ->
                            ToolCallCard(toolCall = toolCall)
                        }
                    }
                }
            }
        }
    }
}

private data class ProviderVaultItem(
    val id: String,
    val name: String,
    val description: String,
    val docsUrl: String,
)

@Composable
fun KeyVaultDialog(
    configuredProviders: Set<String>,
    onSaveKey: (String, String) -> Unit,
    onDeleteKey: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    val providers = listOf(
        ProviderVaultItem(
            id = "groq",
            name = "Groq (Llama / Mixtral / Gemma)",
            description = "Inferencia ultrarrápida mediante LPUs de baja latencia.",
            docsUrl = "https://console.groq.com/keys",
        ),
        ProviderVaultItem(
            id = "openrouter",
            name = "OpenRouter (Multi-Proveedor)",
            description = "Pasarela unificada a modelos comerciales y de código abierto.",
            docsUrl = "https://openrouter.ai/keys",
        ),
        ProviderVaultItem(
            id = "gemini",
            name = "Google Gemini",
            description = "Modelos multimodales y de razonamiento de Google.",
            docsUrl = "https://aistudio.google.com/app/apikey",
        ),
        ProviderVaultItem(
            id = "deepseek",
            name = "DeepSeek AI",
            description = "Modelos especializados en código y razonamiento matemático.",
            docsUrl = "https://platform.deepseek.com/api_keys",
        ),
        ProviderVaultItem(
            id = "cerebras",
            name = "Cerebras Inference",
            description = "Velocidad de generación ultra alta sobre chips Wafer-Scale.",
            docsUrl = "https://cloud.cerebras.ai/",
        ),
        ProviderVaultItem(
            id = "sambanova",
            name = "SambaNova Systems",
            description = "Inferencia para modelos Llama 3 a máxima precisión.",
            docsUrl = "https://cloud.sambanova.ai/",
        ),
        ProviderVaultItem(
            id = "mistral",
            name = "Mistral AI",
            description = "Modelos avanzados de frontera desarrollados en Europa.",
            docsUrl = "https://console.mistral.ai/api-keys/",
        ),
    )

    var editingProvider by remember { mutableStateOf<ProviderVaultItem?>(null) }
    var keyInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Bóveda Segura de Llaves IA",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Cifrado local por hardware con AndroidKeyStore (AES-256-GCM). Las claves nunca salen de este teléfono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                if (editingProvider != null) {
                    Text(
                        "Clave para ${editingProvider?.name}:",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        placeholder = { Text("Pega tu API key aquí…") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            editingProvider = null
                            keyInput = ""
                        }) {
                            Text("Cancelar")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                editingProvider?.let { provider ->
                                    if (keyInput.isNotBlank()) {
                                        onSaveKey(provider.id, keyInput.trim())
                                    }
                                }
                                editingProvider = null
                                keyInput = ""
                            },
                        ) {
                            Text("Guardar")
                        }
                    }
                } else {
                    providers.forEach { provider ->
                        val isConfigured = configuredProviders.contains(provider.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(provider.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(
                                            if (isConfigured) "● Conectada" else "○ No configurada",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isConfigured) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    if (isConfigured) {
                                        IconButton(onClick = { onDeleteKey(provider.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Borrar clave", tint = MaterialTheme.colorScheme.error)
                                        }
                                    } else {
                                        OutlinedButton(onClick = {
                                            editingProvider = provider
                                            keyInput = ""
                                        }) {
                                            Text("Configurar", fontSize = 12.sp)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text = provider.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                )

                                Spacer(Modifier.height(2.dp))

                                TextButton(
                                    onClick = { uriHandler.openUri(provider.docsUrl) },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text("Conseguir API Key", fontSize = 11.sp)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
fun ImmunityReportDialog(
    report: StreetImmunityReport?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isSafe = report?.isFullyImmune == true
                Icon(
                    imageVector = if (isSafe) Icons.Default.Security else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isSafe) Color(0xFF2E7D32) else Color(0xFFF57C00),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isSafe) "Inmunidad Total Activa" else "Alerta de Modo Taller",
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column {
                Text(
                    text = if (report?.isFullyImmune == true) {
                        "El dispositivo está 100% seguro para abrir aplicaciones de delivery (Rappi, PedidosYa, Uber Driver). Sin puertos abiertos ni depuración activa."
                    } else {
                        "ATENCIÓN: Existen funciones de taller o depuración activas en el teléfono. Para salir a la calle a trabajar de reparto, ejecute el script 'fenix-modo-calle.sh' o desactive depuración USB."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))

                report?.details?.forEach { (k, v) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = k,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = v,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (v.contains("Seguro") || v.contains("Activo") || v.contains("CERRADO")) {
                                Color(0xFF2E7D32)
                            } else {
                                Color(0xFFC62828)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendido")
            }
        },
    )
}

@Composable
fun SessionsDialog(
    sessions: List<com.fenix.mobilemcp.memory.FenixMemoryDb.SessionRecord>,
    currentSessionId: String?,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sesiones y Memoria",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onNewSession) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nueva Sesión",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Memoria persistente SQLite 100% local. Cada sesión preserva su propio contexto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                if (sessions.isEmpty()) {
                    Text(
                        text = "No hay sesiones guardadas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                    ) {
                        items(sessions) { session ->
                            val isSelected = session.id == currentSessionId
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onSelectSession(session.id) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.titulo,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                        Text(
                                            text = session.actualizado.take(16).replace("T", " "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteSession(session.id) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar conversación",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}


