package com.fenix.mobilemcp.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fenix.mobilemcp.data.model.BindingAddress
import com.fenix.mobilemcp.data.model.ServerStatus
import com.fenix.mobilemcp.security.EmergencyKillSwitchManager
import com.fenix.mobilemcp.ui.components.ServerLogsSection
import com.fenix.mobilemcp.ui.components.EmergencyLatchCard
import com.fenix.mobilemcp.ui.viewmodels.LogsViewModel
import com.fenix.mobilemcp.ui.viewmodels.MainViewModel
import com.fenix.mobilemcp.utils.NetworkUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkServerPanel(
    onDismiss: () -> Unit,
    mainViewModel: MainViewModel = hiltViewModel(),
    logsViewModel: LogsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val serverStatus by mainViewModel.serverStatus.collectAsStateWithLifecycle()
    val serverConfig by mainViewModel.serverConfig.collectAsStateWithLifecycle()
    val isEmergencyHalted by EmergencyKillSwitchManager.isEmergencyHaltedFlow.collectAsStateWithLifecycle()
    val recentServerLogs by logsViewModel.recentServerLogs.collectAsStateWithLifecycle()
    
    val isServerRunning = serverStatus is ServerStatus.Running
    val deviceIp = remember(context) { NetworkUtils.getDeviceIpAddress(context) ?: "N/A" }
    
    val serverUrl = if (serverConfig.bindingAddress == BindingAddress.NETWORK) {
        "http${if (serverConfig.httpsEnabled) "s" else ""}://$deviceIp:${serverConfig.port}"
    } else {
        "http${if (serverConfig.httpsEnabled) "s" else ""}://127.0.0.1:${serverConfig.port}"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Panel de Red y Servidor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Quick Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Servidor MCP", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (isServerRunning) "Activo" else "Inactivo",
                        color = if (isServerRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = isServerRunning,
                    onCheckedChange = { checked ->
                        if (checked) {
                            mainViewModel.startServer(context)
                        } else {
                            mainViewModel.stopServer(context)
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // URL
            OutlinedTextField(
                value = serverUrl,
                onValueChange = {},
                readOnly = true,
                label = { Text("URL del Servidor") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(serverUrl))
                        Toast.makeText(context, "URL Copiada", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar URL")
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Advanced Options
            var showAdvanced by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showAdvanced) "Ocultar Opciones Avanzadas" else "Opciones Avanzadas de Conexión")
            }

            if (showAdvanced) {
                AdvancedOptionsSection(
                    mainViewModel = mainViewModel,
                    context = context,
                    clipboardManager = clipboardManager
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // KillSwitch
            EmergencyLatchCard(
                isHalted = isEmergencyHalted,
                onHaltClick = { EmergencyKillSwitchManager.triggerEmergencyHalt(context, "Panel de Red") },
                onResetClick = { EmergencyKillSwitchManager.resetEmergencyState() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Logs
            ServerLogsSection(
                logs = recentServerLogs,
                onShowMore = { /* Would navigate to full logs screen if possible, but keep simple here */ }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AdvancedOptionsSection(
    mainViewModel: MainViewModel,
    context: android.content.Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    val serverConfig by mainViewModel.serverConfig.collectAsStateWithLifecycle()
    
    // Bearer Token
    var showToken by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Bearer Token", style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { showToken = !showToken }) {
                Icon(
                    if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Mostrar Token"
                )
            }
        }
        if (showToken) {
            OutlinedTextField(
                value = serverConfig.bearerToken,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(serverConfig.bearerToken))
                        Toast.makeText(context, "Token Copiado", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar Token")
                    }
                }
            )
            // Note: Token generation might need an action in MainViewModel
        }
        ContextualHelpButton("Token de seguridad para autorizar clientes externos.", showLegal = false)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Binding Address
        Text("Dirección de Escucha", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = serverConfig.bindingAddress == BindingAddress.LOCALHOST,
                onClick = { mainViewModel.updateBindingAddress(BindingAddress.LOCALHOST) }
            )
            Text("Solo Local (127.0.0.1)")
            Spacer(Modifier.width(16.dp))
            RadioButton(
                selected = serverConfig.bindingAddress == BindingAddress.NETWORK,
                onClick = { mainViewModel.updateBindingAddress(BindingAddress.NETWORK) }
            )
            Text("Red (0.0.0.0)")
        }
        ContextualHelpButton("127.0.0.1 solo permite conexiones desde el propio teléfono. 0.0.0.0 permite que otros dispositivos en la misma red local o mediante túneles se conecten.", showLegal = false)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Device Slug (Needs MainViewModel support, using placeholder UI for now)
        var deviceSlug by remember { mutableStateOf("fenix-mobile") }
        OutlinedTextField(
            value = deviceSlug,
            onValueChange = { deviceSlug = it },
            label = { Text("Apodo del equipo (Device Slug)") },
            modifier = Modifier.fillMaxWidth()
        )
        ContextualHelpButton("Nombre con el que este dispositivo se identifica en la red.", showLegal = false)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Toggles
        ToggleWithHelp(
            label = "Mostrar avisos de control",
            checked = true, // Replace with actual state
            onCheckedChange = { },
            helpText = "Muestra notificaciones y avisos cuando herramientas externas controlan el dispositivo."
        )

        ToggleWithHelp(
            label = "Iniciar al prender el teléfono",
            checked = serverConfig.autoStartOnBoot,
            onCheckedChange = { mainViewModel.updateAutoStartOnBoot(it) },
            helpText = "El servidor MCP se iniciará automáticamente cuando el dispositivo termine de arrancar."
        )

        ToggleWithHelp(
            label = "Ocultar de recientes",
            checked = false, // Replace with actual state
            onCheckedChange = { },
            helpText = "Oculta la aplicación de la lista de aplicaciones recientes del sistema para mayor privacidad.",
            showLegal = true
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        // Tunnel Config
        Text("Túnel (Cloudflare / Ngrok)", style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = serverConfig.tunnelEnabled,
            onCheckedChange = { mainViewModel.updateTunnelEnabled(it) }
        )
        ContextualHelpButton("Permite acceso seguro desde internet al servidor MCP sin necesidad de IP pública.", showLegal = false)
    }
}

@Composable
fun ContextualHelpButton(helpText: String, showLegal: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(helpText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = { /* Open Help */ }) {
            Text("[Más información]", style = MaterialTheme.typography.labelSmall)
        }
        if (showLegal) {
            TextButton(onClick = { /* Open Legals */ }) {
                Text("[Legales]", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ToggleWithHelp(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    helpText: String,
    showLegal: Boolean = false
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        ContextualHelpButton(helpText, showLegal)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}
