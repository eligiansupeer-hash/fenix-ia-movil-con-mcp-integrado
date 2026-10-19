@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber", "MaxLineLength", "TooGenericExceptionCaught")

package com.fenix.mobilemcp.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fenix.mobilemcp.R
import com.fenix.mobilemcp.connectors.storage.EphemeralCloudStorageClient
import com.fenix.mobilemcp.hardware.HardwareCapabilitiesDetector
import com.fenix.mobilemcp.services.storage.FenixFileManager
import com.fenix.mobilemcp.studio.DocumentActionType
import com.fenix.mobilemcp.studio.LiveWorkspaceBus
import com.fenix.mobilemcp.studio.PolyglotExecutionEngine
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun StudioIdeScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val openTabs by LiveWorkspaceBus.openTabsFlow.collectAsStateWithLifecycle()
    val activePath by LiveWorkspaceBus.activeTabPathFlow.collectAsStateWithLifecycle()
    val aiBanner by LiveWorkspaceBus.lastAiActivityBanner.collectAsStateWithLifecycle()
    val consoleOutput by LiveWorkspaceBus.consoleOutputFlow.collectAsStateWithLifecycle()

    val activeTab = openTabs.firstOrNull { it.path == activePath } ?: openTabs.firstOrNull()

    var isPreviewMode by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf("auto") }
    var showProviderMenu by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showOpenFileDialog by remember { mutableStateOf(false) }

    var hwProfile by remember { mutableStateOf(HardwareCapabilitiesDetector.inspectHardware(context)) }

    LaunchedEffect(activePath, isRunning) {
        hwProfile = HardwareCapabilitiesDetector.inspectHardware(context)
    }

    val syntaxTransformation = remember(activeTab?.language) {
        VsCodeSyntaxHighlightTransformation(activeTab?.language ?: "plaintext")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E2E)),
    ) {
        // 1. Barra superior compacta (1 sola línea garantizada, sin desborde vertical)
        Surface(
            color = Color(0xFF181825),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = Color(0xFF89B4FA),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "F-Code IDE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCDD6F4),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Surface(
                        color = Color(0xFF313244),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = if (hwProfile.isLowMemoryState) Color(0xFFF38BA8) else Color(0xFFA6E3A1),
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "RAM ${hwProfile.availableRamMb}MB • ${hwProfile.efficiencyCoreIndices.size}E+${hwProfile.performanceCoreIndices.size + hwProfile.primeCoreIndices.size}P",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFBAC2DE),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }

                // Banner en vivo cuando la IA toca un documento
                if (!aiBanner.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF313244),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "$aiBanner — Sincronizado en vivo",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF9E2AF),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // 2. Barra de Pestañas de Archivos Abiertos (Estilo VS Code) + Botones Nuevo / Abrir
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF11111B))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            openTabs.forEach { tab ->
                val isSelected = tab.path == activeTab?.path
                Surface(
                    color = if (isSelected) Color(0xFF1E1E2E) else Color(0xFF181825),
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                    modifier = Modifier.clickable {
                        LiveWorkspaceBus.selectTab(tab.path)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (tab.isDirty) "● ${tab.name}" else tab.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) Color(0xFF89B4FA) else Color(0xFF9399B2),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar pestaña",
                            tint = Color(0xFF6C7086),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { LiveWorkspaceBus.closeTab(tab.path) },
                        )
                    }
                }
            }

            IconButton(
                onClick = { showNewFileDialog = true },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nuevo archivo",
                    tint = Color(0xFFA6E3A1),
                    modifier = Modifier.size(18.dp),
                )
            }

            IconButton(
                onClick = { showOpenFileDialog = true },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Abrir archivo del teléfono",
                    tint = Color(0xFFF9E2AF),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // 3. Barra de Herramientas del Editor (Compilar/Ejecutar, Motor VM, Guardar, Vista Previa, Cloud Share)
        if (activeTab != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181825))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Botón Ejecutar / Compilar
                Button(
                    onClick = {
                        isRunning = true
                        scope.launch {
                            try {
                                PolyglotExecutionEngine.executeCode(
                                    language = activeTab.language,
                                    code = activeTab.content,
                                    preferredProvider = selectedProvider,
                                    context = context,
                                )
                            } finally {
                                isRunning = false
                            }
                        }
                    },
                    enabled = !isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF40A02B)),
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ejecutar (${activeTab.language.uppercase()})",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                // Selector de Motor (Auto / Local / Wandbox / Piston / Godbolt / H2.nexus)
                Box {
                    OutlinedButton(onClick = { showProviderMenu = true }) {
                        Text(
                            text = "VM: ${selectedProvider.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCDD6F4),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    DropdownMenu(
                        expanded = showProviderMenu,
                        onDismissRequest = { showProviderMenu = false },
                    ) {
                        listOf(
                            "auto" to "⚡ Auto Inteligente (Cuidado RAM + Cloud VM)",
                            "local" to "📱 Local (Núcleos Performance del Móvil)",
                            "wandbox" to "☁️ Wandbox VM Gratis (Clang 17 / Python 3.12 / Rust / Go)",
                            "piston" to "🐳 Piston API Container Sandbox",
                            "godbolt" to "🔬 Godbolt Compiler Explorer (ASM + Exec)",
                            "h2.nexus" to "🌐 H2.nexus Virtual Server",
                        ).forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedProvider = id
                                    showProviderMenu = false
                                },
                            )
                        }
                    }
                }

                // Alternar Código vs Vista Previa en Vivo (para Markdown, HTML, JSON, Texto)
                OutlinedButton(
                    onClick = { isPreviewMode = !isPreviewMode },
                ) {
                    Icon(
                        imageVector = if (isPreviewMode) Icons.Default.Code else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFFF9E2AF),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPreviewMode) "Código" else "Vista Previa",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCDD6F4),
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                // Botón Guardar en el almacenamiento del móvil
                OutlinedButton(
                    onClick = {
                        runCatching {
                            FenixFileManager.writeUniversal(
                                path = activeTab.path,
                                content = activeTab.content,
                                append = false,
                            )
                            Toast.makeText(context, "Guardado: ${activeTab.name}", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            Toast.makeText(context, "Error al guardar: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF89B4FA))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Guardar", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCDD6F4), maxLines = 1, softWrap = false)
                }

                // Subir archivo efímero sin login (file.io / storage.to)
                OutlinedButton(
                    onClick = {
                        isUploading = true
                        scope.launch {
                            try {
                                FenixFileManager.writeUniversal(activeTab.path, activeTab.content, false)
                                val res = EphemeralCloudStorageClient.uploadFile(activeTab.path, "file.io")
                                if (res.success && res.downloadUrl.isNotBlank()) {
                                    copyToClipboard(context, "Fenix Ephemeral Link", res.downloadUrl)
                                    Toast.makeText(
                                        context,
                                        "Link efímero copiado: ${res.downloadUrl}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                isUploading = false
                            }
                        }
                    },
                    enabled = !isUploading,
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFFCBA6F7))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isUploading) "Subiendo..." else "Cloud Share",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCDD6F4),
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                // Botón de Pantalla Dividida bajo demanda
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val res = com.fenix.mobilemcp.services.accessibility.SplitScreenController.executeCommand(
                                context = context,
                                action = "toggle",
                            )
                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text(
                        text = "◫ Dividir",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF89DCEB),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        // 4. Cuerpo Principal: Editor de Código (con scroll vertical unificado) o Vista Previa en Vivo
        if (activeTab == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No hay documentos abiertos. Toca (+) arriba para crear uno.", color = Color(0xFF6C7086))
            }
        } else if (isPreviewMode) {
            LiveDocumentPreviewPane(
                language = activeTab.language,
                content = activeTab.content,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            val verticalScroll = rememberScrollState()
            val linesCount = remember(activeTab.content) { activeTab.content.count { it == '\n' } + 1 }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E2E))
                    .verticalScroll(verticalScroll),
            ) {
                // Columna de numeración de líneas sincronizada dentro del mismo scroll
                Column(
                    modifier = Modifier
                        .width(42.dp)
                        .background(Color(0xFF181825))
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    for (lineNum in 1..linesCount) {
                        Text(
                            text = lineNum.toString(),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = Color(0xFF585B70),
                            ),
                        )
                    }
                }

                // Área editable con resaltado de sintaxis VS Code Dark+
                BasicTextField(
                    value = activeTab.content,
                    onValueChange = { newText ->
                        LiveWorkspaceBus.updateActiveTabContent(activeTab.path, newText)
                    },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFFCDD6F4),
                    ),
                    cursorBrush = SolidColor(Color(0xFF89B4FA)),
                    visualTransformation = syntaxTransformation,
                    modifier = Modifier
                        .weight(1f)
                        .padding(10.dp),
                )
            }

            // 5. Barra Táctil de Símbolos Rápidos para Programadores Móviles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181825))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("    ", "{", "}", "(", ")", "[", "]", "=", ";", "\"", "'", ":", "->", "#", "//").forEach { sym ->
                    AssistChip(
                        onClick = {
                            LiveWorkspaceBus.updateActiveTabContent(activeTab.path, activeTab.content + sym)
                        },
                        label = {
                            Text(
                                text = if (sym == "    ") "TAB" else sym,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF89DCEB),
                            )
                        },
                    )
                }
            }
        }

        // 6. Consola Inferior de Compilación y Ejecución en Vivo
        if (consoleOutput != null) {
            val out = consoleOutput!!
            HorizontalDivider(color = Color(0xFF45475A))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11111B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${if (out.success) "✅" else "❌"} ${out.title} • ${out.engineUsed} (${out.durationMs} ms)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (out.success) Color(0xFFA6E3A1) else Color(0xFFF38BA8),
                        )
                        TextButton(onClick = { LiveWorkspaceBus.clearConsole() }) {
                            Text("Limpiar", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9399B2))
                        }
                    }
                    Text(
                        text = "Afinidad Hardware: ${out.coresAssigned}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF89B4FA),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (out.stdout.isNotBlank()) {
                            Text(
                                text = out.stdout,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFCDD6F4),
                            )
                        }
                        if (out.stderr.isNotBlank()) {
                            Text(
                                text = out.stderr,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFF38BA8),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewFileDialog) {
        NewFileDialog(
            onDismiss = { showNewFileDialog = false },
            onCreate = { fileName, templateContent ->
                val target = File(LiveWorkspaceBus.getWorkspaceRootPath(), fileName)
                runCatching {
                    FenixFileManager.writeUniversal(target.absolutePath, templateContent, false)
                }.onFailure {
                    LiveWorkspaceBus.notifyFileTouched(
                        file = target,
                        action = DocumentActionType.CREATED,
                        actor = "Usuario (F-Code)",
                        contentOverride = templateContent,
                    )
                }
                showNewFileDialog = false
            },
        )
    }

    if (showOpenFileDialog) {
        OpenWorkspaceFileDialog(
            onDismiss = { showOpenFileDialog = false },
            onSelectFile = { file ->
                LiveWorkspaceBus.notifyFileTouched(
                    file = file,
                    action = DocumentActionType.OPENED_BY_USER,
                    actor = "Usuario",
                )
                showOpenFileDialog = false
            },
        )
    }
}

@Composable
private fun LiveDocumentPreviewPane(
    language: String,
    content: String,
    modifier: Modifier = Modifier,
) {
    if (language == "html") {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)
            },
            modifier = modifier,
        )
    } else {
        Column(
            modifier = modifier
                .background(Color(0xFF1E1E2E))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "📄 Vista Previa Documental en Vivo ($language)",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF89B4FA),
            )
            HorizontalDivider(color = Color(0xFF313244))
            content.lines().forEach { line ->
                when {
                    line.startsWith("# ") -> Text(
                        text = line.removePrefix("# "),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCBA6F7),
                    )
                    line.startsWith("## ") -> Text(
                        text = line.removePrefix("## "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF89B4FA),
                    )
                    line.startsWith("### ") -> Text(
                        text = line.removePrefix("### "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF9E2AF),
                    )
                    else -> Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCDD6F4),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewFileDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var fileName by remember { mutableStateOf("script_nuevo.py") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Nuevo Archivo en F-Code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Escribe el nombre con su extensión (.py, .c, .cpp, .js, .rs, .go, .md, .html, .sql):",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    singleLine = true,
                    label = { Text("Nombre del archivo") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = fileName.trim().ifEmpty { "nuevo.py" }
                    onCreate(clean, "")
                },
            ) {
                Text("Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun OpenWorkspaceFileDialog(
    onDismiss: () -> Unit,
    onSelectFile: (File) -> Unit,
) {
    val foundFiles = remember {
        runCatching {
            FenixFileManager.searchFiles(startPath = "", maxResults = 40, maxDepth = 4)
                .filter { !it.isDirectory }
                .map { File(it.path) }
        }.getOrDefault(emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abrir Documento o Código del Teléfono") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (foundFiles.isEmpty()) {
                    Text("No se encontraron archivos recientes en el almacenamiento.")
                } else {
                    foundFiles.forEach { file ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectFile(file) },
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(file.name, fontWeight = FontWeight.Bold)
                                Text(
                                    file.absolutePath,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

/**
 * Transformación visual de resaltado de sintaxis multi-lenguaje estilo VS Code Dark+ (Rosemoe / TextMate).
 */
private class VsCodeSyntaxHighlightTransformation(
    private val language: String,
) : VisualTransformation {

    private val keywordRegex = Regex(
        "\\b(def|class|import|from|return|if|else|elif|for|while|try|except|finally|with|as|async|await|fun|val|var|object|interface|package|private|public|suspend|override|when|int|long|float|double|char|void|struct|include|printf|fn|let|mut|pub|use|SELECT|FROM|WHERE|INSERT|INTO|CREATE|TABLE|function|const)\\b",
    )
    private val numberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
    private val stringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
    private val commentRegex = Regex("(//.*|#.*)", RegexOption.MULTILINE)

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val styled = buildAnnotatedString {
            append(raw)
            if (raw.length <= 25_000) {
                keywordRegex.findAll(raw).forEach { m ->
                    addStyle(
                        SpanStyle(color = Color(0xFFCBA6F7), fontWeight = FontWeight.SemiBold),
                        m.range.first,
                        m.range.last + 1,
                    )
                }
                numberRegex.findAll(raw).forEach { m ->
                    addStyle(SpanStyle(color = Color(0xFFFAB387)), m.range.first, m.range.last + 1)
                }
                stringRegex.findAll(raw).forEach { m ->
                    addStyle(SpanStyle(color = Color(0xFFA6E3A1)), m.range.first, m.range.last + 1)
                }
                commentRegex.findAll(raw).forEach { m ->
                    addStyle(SpanStyle(color = Color(0xFF6C7086)), m.range.first, m.range.last + 1)
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
