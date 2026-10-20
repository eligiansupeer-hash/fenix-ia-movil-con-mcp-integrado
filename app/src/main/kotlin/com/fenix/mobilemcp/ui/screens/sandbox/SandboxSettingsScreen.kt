@file:Suppress("FunctionNaming", "LongMethod")

package com.fenix.mobilemcp.ui.screens.sandbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fenix.mobilemcp.sandbox.SandboxInstaller
import com.fenix.mobilemcp.sandbox.SandboxManifest
import com.fenix.mobilemcp.ui.viewmodels.SandboxSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxSettingsScreen(
    onBack: () -> Unit,
    onOpenConsole: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SandboxSettingsViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val output by viewModel.output.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Linux Sandbox") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (status.installed) "Debian 12 instalado" else "Sandbox no instalado",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Rootfs arm64 · ${SandboxManifest.VERSION}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("Tamaño: ${mb(status.rootfsBytes)} · Libres: ${mb(status.freeBytes)}")
                    Text(
                        text =
                            when {
                                !status.nativeProotPresent -> "Faltan binarios proot en el APK"
                                status.openCodeInstalled -> "OpenCode instalado"
                                else -> "OpenCode no instalado"
                            },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (!status.installed) {
                Text(
                    "Se descargan unos 64 MB. La extracción necesita ~200 MB en el " +
                        "almacenamiento interno. No requiere root.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (progress != null) {
                val current = progress!!
                Text(progressLabel(current), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { current.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (busy) {
                OutlinedButton(onClick = viewModel::cancelInstall, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancelar")
                }
            }

            if (!status.installed) {
                Button(
                    onClick = viewModel::install,
                    enabled = !busy && status.nativeProotPresent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Instalar sandbox")
                }
            } else {
                Button(
                    onClick = onOpenConsole,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Abrir consola")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::smokeTest, enabled = !busy) {
                        Text("Probar")
                    }
                    OutlinedButton(onClick = viewModel::installOpenCode, enabled = !busy) {
                        Text("Instalar OpenCode")
                    }
                }
                OutlinedButton(
                    onClick = viewModel::uninstall,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Eliminar sandbox")
                }
            }

            message?.let { text ->
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }

            if (output.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = output,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier =
                            Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                    )
                }
            }
        }
    }
}

private fun progressLabel(progress: SandboxInstaller.Progress): String =
    when (progress.phase) {
        SandboxInstaller.Progress.Phase.DOWNLOADING -> "Descargando ${progress.detail}"
        SandboxInstaller.Progress.Phase.VERIFYING -> "Verificando SHA-256"
        SandboxInstaller.Progress.Phase.EXTRACTING -> "Extrayendo ${progress.detail}"
        SandboxInstaller.Progress.Phase.CONFIGURING -> "Configurando Debian"
        SandboxInstaller.Progress.Phase.DONE -> "Listo"
    }

private fun mb(bytes: Long): String = "${bytes / (1024 * 1024)} MB"
