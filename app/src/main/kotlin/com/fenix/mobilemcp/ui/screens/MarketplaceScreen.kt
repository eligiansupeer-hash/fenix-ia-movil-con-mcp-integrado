@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber", "MaxLineLength")

package com.fenix.mobilemcp.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fenix.mobilemcp.hardware.HardwareCapabilitiesDetector
import com.fenix.mobilemcp.hardware.ModelCompatibilityTier
import com.fenix.mobilemcp.marketplace.AxiomCriticonAuditor
import com.fenix.mobilemcp.marketplace.CriticonAuditReport
import com.fenix.mobilemcp.marketplace.CriticonVerdictTier
import com.fenix.mobilemcp.marketplace.ExtensionCategory
import com.fenix.mobilemcp.marketplace.FenixExtensionRegistry
import com.fenix.mobilemcp.marketplace.FenixMarketplaceExtension
import com.fenix.mobilemcp.marketplace.InformedConsentRecord
import com.fenix.mobilemcp.studio.DocumentActionType
import com.fenix.mobilemcp.studio.LiveWorkspaceBus
import com.fenix.mobilemcp.studio.PolyglotExecutionEngine
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    onOpenInStudio: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allExtensionsRaw by FenixExtensionRegistry.extensionsFlow.collectAsStateWithLifecycle()
    val allExtensions = if (allExtensionsRaw.isEmpty()) FenixExtensionRegistry.builtInCatalog() else allExtensionsRaw

    var selectedCategory by remember { mutableStateOf<ExtensionCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedExtensionDetail by remember { mutableStateOf<FenixMarketplaceExtension?>(null) }
    var showCreateVibeModal by remember { mutableStateOf(false) }
    var isSyncingGitHubCommunity by remember { mutableStateOf(false) }
    var instantRunnerTarget by remember { mutableStateOf<FenixMarketplaceExtension?>(null) }
    var instantRunnerOutput by remember { mutableStateOf<String?>(null) }

    val hwProfile = remember { HardwareCapabilitiesDetector.inspectHardware(context) }

    val filteredExtensions = remember(allExtensions, selectedCategory, searchQuery) {
        allExtensions.filter { ext ->
            val catMatch = selectedCategory == null || ext.category == selectedCategory
            val q = searchQuery.trim().lowercase()
            val queryMatch = q.isEmpty() ||
                ext.name.lowercase().contains(q) ||
                ext.shortDescription.lowercase().contains(q) ||
                ext.publisher.lowercase().contains(q)
            catMatch && queryMatch
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "F-Market • Tienda & AXIOM",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            actions = {
                OutlinedButton(
                    onClick = { showCreateVibeModal = true },
                    modifier = Modifier.padding(end = 8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Crear / Subir", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                }
            },
            windowInsets = WindowInsets(0),
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header_controls") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Tarjeta Diagnóstica de Hardware + Estado de Auditoría AXIOM Criticón
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Hardware & Guardia AXIOM Criticón (${hwProfile.deviceModel})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = "• RAM Libre: ${hwProfile.availableRamMb} MB / ${hwProfile.totalRamMb} MB (Presupuesto IA: ${hwProfile.safeAiBudgetMb} MB)\n" +
                                    "• Topología CPU (${hwProfile.totalCores} núcleos): ${hwProfile.efficiencyCoreIndices.size} Efficiency + ${hwProfile.performanceCoreIndices.size + hwProfile.primeCoreIndices.size} Performance/Prime\n" +
                                    "• 🛡️ AXIOM Criticón Activo ($0): Auditoría automática por métricas OQSI (Seguridad SAST, Documentación y Eficiencia).",
                                style = MaterialTheme.typography.bodySmall,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (!isSyncingGitHubCommunity) {
                                            isSyncingGitHubCommunity = true
                                            scope.launch {
                                                val discovered = AxiomCriticonAuditor.discoverCommunityGitHubCreations()
                                                FenixExtensionRegistry.mergeDiscoveredCommunityExtensions(discovered)
                                                isSyncingGitHubCommunity = false
                                                val msg = if (discovered.isNotEmpty()) {
                                                    "Se sincronizaron ${discovered.size} creaciones comunitarias de GitHub verificadas por AXIOM Criticón."
                                                } else {
                                                    "Catálogo comunitario al día (Topic GitHub: fenix-instant-app / fenix-mcp-extension)."
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                selectedCategory = ExtensionCategory.INSTANT_APPS_COMMUNITY
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isSyncingGitHubCommunity) "Auditando GitHub..." else "🌍 Explorar Comunidad GitHub ($0)",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }

                    // Buscador estilo VS Code Extensions
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Buscar Instant Apps, agentes AXIOM, modelos, VMs...",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Filtros de Categoría
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("Todas (${allExtensions.size})") },
                        )
                        ExtensionCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = if (selectedCategory == category) null else category },
                                label = { Text(category.titleEs) },
                            )
                        }
                    }
                }
            }

            // Lista Virtualizada de Extensiones e Instant Apps con Métricas del AXIOM Criticón
            items(filteredExtensions, key = { it.id }) { ext ->
                val compatibilityTier = if (ext.ramFootprintMb > 0L) {
                    when {
                        ext.ramFootprintMb <= hwProfile.availableRamMb - 600L -> ModelCompatibilityTier.OPTIMAL
                        ext.ramFootprintMb <= hwProfile.availableRamMb - 200L -> ModelCompatibilityTier.TIGHT_FIT
                        else -> ModelCompatibilityTier.EXCEEDS_RAM
                    }
                } else {
                    ModelCompatibilityTier.OPTIMAL
                }

                val auditReport = remember(ext.id, ext.version, ext.shortDescription, ext.sourceCodeJs) {
                    AxiomCriticonAuditor.auditExtension(ext)
                }

                VsCodeExtensionCard(
                    extension = ext,
                    compatibilityTier = compatibilityTier,
                    auditReport = auditReport,
                    onOpenDetail = { selectedExtensionDetail = ext },
                    onToggleInstall = { install ->
                        if (install && !auditReport.passedGate) {
                            Toast.makeText(
                                context,
                                "AXIOM Criticón bloqueó esta extensión por no cumplir el umbral mínimo de seguridad/documentación.",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            FenixExtensionRegistry.installOrToggleExtension(ext.id, install = install, enable = install)
                            Toast.makeText(
                                context,
                                if (install) "Activada: ${ext.name}" else "Desactivada: ${ext.name}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onRunInstantApp = if (!ext.sourceCodeJs.isNullOrBlank()) {
                        {
                            instantRunnerTarget = ext
                            instantRunnerOutput = "Ejecutando Instant App en Sandbox aislado (0 datos personales expuestos)..."
                            scope.launch {
                                val execResult = PolyglotExecutionEngine.executeCode(
                                    language = "javascript",
                                    code = ext.sourceCodeJs,
                                    preferredProvider = "local",
                                    context = context,
                                )
                                instantRunnerOutput = buildString {
                                    appendLine("🛡️ Veredicto AXIOM Criticón: ${auditReport.verdictTier.badgeEs} (OQSI ${auditReport.overallScoreOqsi}/100)")
                                    appendLine("🔒 Hash Integridad SHA-256: ${auditReport.sha256IntegrityHash}")
                                    appendLine("⏱️ Tiempo en Sandbox: ${execResult.executionTimeMs} ms (${execResult.engineUsed})")
                                    appendLine("────────────────────────────────────────")
                                    appendLine(execResult.stdout.ifBlank { execResult.stderr })
                                }
                            }
                        }
                    } else {
                        null
                    },
                    onOpenInEditor = {
                        val jsFile = File(LiveWorkspaceBus.getWorkspaceRootPath(), "${ext.id}.js")
                        val code = ext.sourceCodeJs ?: "// Extensión: ${ext.name}\n// Categoría: ${ext.category.titleEs}\n// Endpoint: ${ext.endpointUrl ?: "Local"}\n"
                        LiveWorkspaceBus.notifyFileTouched(
                            file = jsFile,
                            action = DocumentActionType.OPENED_BY_USER,
                            actor = "Marketplace",
                            contentOverride = code,
                        )
                        onOpenInStudio()
                    },
                )
            }
        }
    }

    // Modal de Ejecución Instantánea ("Fenix Instant App Sandbox Runner")
    instantRunnerTarget?.let { ext ->
        AlertDialog(
            onDismissRequest = {
                instantRunnerTarget = null
                instantRunnerOutput = null
            },
            title = {
                Column {
                    Text("⚡ Instant App Sandbox: ${ext.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Ejecución sin instalación • Aislamiento Zero-Data-Leak",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32),
                    )
                }
            },
            text = {
                Surface(
                    color = Color(0xFF101418),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = instantRunnerOutput ?: "Iniciando...",
                            color = Color(0xFFE6EDF3),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val jsFile = File(LiveWorkspaceBus.getWorkspaceRootPath(), "${ext.id}.js")
                        LiveWorkspaceBus.notifyFileTouched(
                            file = jsFile,
                            action = DocumentActionType.OPENED_BY_USER,
                            actor = "InstantAppRunner",
                            contentOverride = ext.sourceCodeJs.orEmpty(),
                        )
                        instantRunnerTarget = null
                        onOpenInStudio()
                    },
                ) {
                    Text("Inspeccionar Código en F-Studio")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        instantRunnerTarget = null
                        instantRunnerOutput = null
                    },
                ) {
                    Text("Cerrar")
                }
            },
        )
    }

    // Modal de Ficha Completa estilo VS Code Extension README + Auditoría AXIOM Criticón
    selectedExtensionDetail?.let { ext ->
        val audit = remember(ext.id, ext.version) { AxiomCriticonAuditor.auditExtension(ext) }

        AlertDialog(
            onDismissRequest = { selectedExtensionDetail = null },
            title = {
                Column {
                    Text(ext.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${ext.publisher} • v${ext.version} • ${ext.category.titleEs}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(390.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Tarjeta de Auditoría Automática por Métricas (AXIOM Criticón)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "${audit.verdictTier.badgeEs} — Score OQSI: ${audit.overallScoreOqsi}/100",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "• 🛡️ Seguridad SAST: ${audit.securityScore}/100 | 📖 Documentación: ${audit.documentationScore}/100 | ⚡ Eficiencia: ${audit.efficiencyScore}/100\n" +
                                    "• 🔒 Hash SHA-256: ${audit.sha256IntegrityHash} (Zero-Knowledge: 0 datos de usuario expuestos)",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            audit.securityFindings.forEach { finding ->
                                Text(finding, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "⚙️ Perfil de Recursos y Núcleos CPU",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "• Consumo RAM estimado: ${if (ext.ramFootprintMb == 0L) "0 MB (Cloud / Zero-Idle)" else "${ext.ramFootprintMb} MB"}\n" +
                                    "• Afinidad de Núcleos: ${ext.cpuClusterPreference}\n" +
                                    "• Capacidades declaradas: ${ext.capabilitiesProvided.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    HorizontalDivider()

                    ext.readmeMarkdown.lines().forEach { line ->
                        when {
                            line.startsWith("# ") -> Text(
                                text = line.removePrefix("# "),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            line.startsWith("**") -> Text(
                                text = line.replace("**", ""),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            else -> Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (!ext.downloadUrl.isNullOrBlank()) {
                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ext.downloadUrl)))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Descargar Paquete / Modelo Oficial ↗")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ext.learnMoreUrl)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Para más información y documentación oficial ↗")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nextState = !ext.isEnabled
                        FenixExtensionRegistry.installOrToggleExtension(ext.id, install = nextState, enable = nextState)
                        selectedExtensionDetail = null
                    },
                ) {
                    Text(if (ext.isEnabled) "Desactivar Extensión" else "Instalar / Activar")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedExtensionDetail = null }) {
                    Text("Cerrar")
                }
            },
        )
    }

    if (showCreateVibeModal) {
        CreateAndShareInstantAppDialog(
            onDismiss = { showCreateVibeModal = false },
            onSaveLocal = { newExt ->
                FenixExtensionRegistry.registerUserOrVibeExtension(newExt)
                showCreateVibeModal = false
                Toast.makeText(context, "Guardada en tu Marketplace: ${newExt.name}", Toast.LENGTH_SHORT).show()
            },
            onShareToGitHubCommunity = { newExt, audit, consent ->
                FenixExtensionRegistry.registerUserOrVibeExtension(newExt)
                showCreateVibeModal = false
                val shareUrl = AxiomCriticonAuditor.buildGitHubShareUrl(newExt, audit, consent)
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(shareUrl)))
                }
                Toast.makeText(
                    context,
                    "Certificado AXIOM Criticón generado (OQSI ${audit.overallScoreOqsi}/100). Abriendo GitHub Community...",
                    Toast.LENGTH_LONG,
                ).show()
            },
        )
    }
}

@Composable
private fun VsCodeExtensionCard(
    extension: FenixMarketplaceExtension,
    compatibilityTier: ModelCompatibilityTier,
    auditReport: CriticonAuditReport,
    onOpenDetail: () -> Unit,
    onToggleInstall: (Boolean) -> Unit,
    onRunInstantApp: (() -> Unit)? = null,
    onOpenInEditor: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${extension.publisher} • v${extension.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Switch(
                    checked = extension.isEnabled,
                    onCheckedChange = { onToggleInstall(it) },
                )
            }

            Text(
                text = extension.shortDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Barra de Métricas Objetivas del AXIOM Criticón (Zero-Knowledge)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = when (auditReport.verdictTier) {
                                CriticonVerdictTier.CERTIFIED -> Color(0xFF2E7D32)
                                CriticonVerdictTier.APPROVED_WITH_NOTES -> Color(0xFFEF6C00)
                                CriticonVerdictTier.REJECTED -> Color(0xFFC62828)
                            },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Criticón OQSI: ${auditReport.overallScoreOqsi}/100",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "🛡️Seg:${auditReport.securityScore} • 📖Doc:${auditReport.documentationScore} • ⚡Efic:${auditReport.efficiencyScore}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Semáforo de RAM + Botones de Acción (Instant App / F-Studio / Detalles)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val badgeText = when {
                    extension.ramFootprintMb == 0L -> "🟢 0 MB RAM Local"
                    compatibilityTier == ModelCompatibilityTier.OPTIMAL -> "🟢 Óptimo (${extension.ramFootprintMb} MB)"
                    compatibilityTier == ModelCompatibilityTier.TIGHT_FIT -> "🟡 Ajustado (${extension.ramFootprintMb} MB)"
                    else -> "🔴 Excede RAM (${extension.ramFootprintMb} MB)"
                }

                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = when (compatibilityTier) {
                        ModelCompatibilityTier.OPTIMAL -> Color(0xFF2E7D32)
                        ModelCompatibilityTier.TIGHT_FIT -> Color(0xFFEF6C00)
                        ModelCompatibilityTier.EXCEEDS_RAM -> Color(0xFFC62828)
                    },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onRunInstantApp != null) {
                        TextButton(onClick = onRunInstantApp) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("⚡ Instant App", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = onOpenInEditor) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("F-Studio", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onOpenDetail) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Ficha", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateAndShareInstantAppDialog(
    onDismiss: () -> Unit,
    onSaveLocal: (FenixMarketplaceExtension) -> Unit,
    onShareToGitHubCommunity: (FenixMarketplaceExtension, CriticonAuditReport, InformedConsentRecord) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var authorAlias by remember { mutableStateOf("") }
    var shortDesc by remember {
        mutableStateOf("Micro-aplicación instantánea que procesa datos localmente en el Sandbox de Fénix sin exponer información privada.")
    }
    var detailedReadme by remember {
        mutableStateOf(
            """
            # Propósito de la Instant App
            Describe aquí con claridad qué problema resuelve tu creación, qué datos de entrada recibe y qué resultado devuelve.
            
            **Permisos y Seguridad:**
            - Ejecución 100% en Sandbox local sin tocar datos personales ni aplicaciones bancarias.
            """.trimIndent(),
        )
    }
    var endpointUrl by remember { mutableStateOf("") }
    var jsCode by remember {
        mutableStateOf(
            """
            // Código JS de la Instant App (Auditado en vivo por AXIOM Criticón)
            function runInstantCreation(inputData) {
                return {
                    ok: true,
                    message: "Instant App ejecutada en Sandbox seguro",
                    timestamp: Date.now()
                };
            }
            return runInstantCreation(typeof args !== 'undefined' ? args : {});
            """.trimIndent(),
        )
    }

    // 3 Checkboxes de Consentimiento Informado Explícito
    var consentZeroPrivateData by remember { mutableStateOf(false) }
    var consentOpenLicense by remember { mutableStateOf(false) }
    var consentPublicCriticonMetrics by remember { mutableStateOf(false) }

    val draftExtension = remember(name, authorAlias, shortDesc, detailedReadme, endpointUrl, jsCode) {
        val cleanName = name.trim().ifEmpty { "Instant App Comunitaria" }
        val cleanId = "instant-" + cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "-")
        FenixMarketplaceExtension(
            id = cleanId,
            name = "⚡ Instant App: $cleanName",
            publisher = if (authorAlias.isBlank()) "Usuario Local" else "@${authorAlias.trim()}",
            version = "1.0.0",
            category = if (endpointUrl.isNotBlank()) ExtensionCategory.CLOUD_VMS_STORAGE else ExtensionCategory.INSTANT_APPS_COMMUNITY,
            shortDescription = shortDesc.trim(),
            readmeMarkdown = detailedReadme.trim(),
            learnMoreUrl = endpointUrl.ifBlank { "https://github.com/topics/fenix-instant-app" },
            endpointUrl = endpointUrl.takeIf { it.isNotBlank() },
            sourceCodeJs = jsCode,
            capabilitiesProvided = listOf("instant_app_sandbox", "criticon_audited"),
            isBuiltIn = false,
            isInstalled = true,
            isEnabled = true,
        )
    }

    val liveAudit = remember(draftExtension) {
        AxiomCriticonAuditor.auditExtension(draftExtension)
    }

    val consentRecord = remember(consentZeroPrivateData, consentOpenLicense, consentPublicCriticonMetrics, authorAlias) {
        InformedConsentRecord(
            confirmsZeroPrivateDataOrKeys = consentZeroPrivateData,
            acceptsOpenCommunityLicense = consentOpenLicense,
            acceptsPublicCriticonMetrics = consentPublicCriticonMetrics,
            authorAlias = authorAlias,
            timestampEpochMs = System.currentTimeMillis(),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Crear / Compartir Instant App o Servidor VM", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Auditoría en vivo por AXIOM Criticón + Consentimiento Informado (Costo $0)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Semáforo en tiempo real del AXIOM Criticón
                Surface(
                    color = if (liveAudit.passedGate) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${liveAudit.verdictTier.badgeEs} (OQSI: ${liveAudit.overallScoreOqsi}/100)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (liveAudit.passedGate) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                        )
                        Text(
                            text = "🛡️ Seguridad: ${liveAudit.securityScore}/100 | 📖 Descripción: ${liveAudit.documentationScore}/100 | ⚡ Eficiencia: ${liveAudit.efficiencyScore}/100",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF263238),
                        )
                        (liveAudit.securityFindings + liveAudit.documentationFindings + liveAudit.efficiencyFindings)
                            .take(3)
                            .forEach { msg ->
                                Text(msg, style = MaterialTheme.typography.labelSmall, color = Color(0xFF37474F))
                            }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la Instant App / Extensión (Obligatorio)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = authorAlias,
                    onValueChange = { authorAlias = it },
                    label = { Text("Tu Alias o Usuario de GitHub (ej: eli-dev)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = shortDesc,
                    onValueChange = { shortDesc = it },
                    label = { Text("Resumen corto (Mín. 40 caracteres)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = detailedReadme,
                    onValueChange = { detailedReadme = it },
                    label = { Text("Descripción detallada de funciones y seguridad (Mín. 120 caracteres)") },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                )
                OutlinedTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                    label = { Text("URL HTTPS Servidor Virtual (Opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = jsCode,
                    onValueChange = { jsCode = it },
                    label = { Text("Código Fuente Instant App (JS Sandbox)") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )

                HorizontalDivider()

                Text(
                    text = "📋 Consentimiento Informado (Obligatorio para compartir en la tienda comunitaria GitHub):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consentZeroPrivateData, onCheckedChange = { consentZeroPrivateData = it })
                    Text(
                        text = "1. Confirmo que esta creación NO contiene datos personales, contraseñas ni API Keys privadas.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consentOpenLicense, onCheckedChange = { consentOpenLicense = it })
                    Text(
                        text = "2. Acepto compartir voluntariamente esta creación bajo licencia abierta comunitaria (Costo $0).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consentPublicCriticonMetrics, onCheckedChange = { consentPublicCriticonMetrics = it })
                    Text(
                        text = "3. Acepto que el certificado de métricas y seguridad de AXIOM Criticón sea público junto a mi creación.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    enabled = liveAudit.passedGate && name.trim().length >= 3,
                    onClick = { onSaveLocal(draftExtension) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Guardar Solo en Mi Móvil (Local)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    enabled = liveAudit.passedGate && consentRecord.isFullyGranted && name.trim().length >= 3,
                    onClick = { onShareToGitHubCommunity(draftExtension, liveAudit, consentRecord) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🌍 Compartir en GitHub (Criticón OK)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Cerrar / Cancelar")
                }
            }
        },
    )
}
