package com.fenix.mobilemcp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenix.mobilemcp.mcp.oauth.PendingApproval
import java.text.DateFormat
import java.util.Date

@Composable
fun ClientAuthorizationCard(
    approval: PendingApproval,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onSavePermissions: (Set<String>) -> Unit,
    initialIsAccessGranted: Boolean = false
) {
    // Tracks if access has been granted so we can show permissions.
    var isAccessGranted by remember(initialIsAccessGranted) { mutableStateOf(initialIsAccessGranted) }

    // State for granular permissions
    var accessibilityGranted by remember { mutableStateOf(false) }
    var micGranted by remember { mutableStateOf(false) }
    var screenGranted by remember { mutableStateOf(false) }
    var storageGranted by remember { mutableStateOf(false) }
    var controlGranted by remember { mutableStateOf(false) }

    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val dateString = dateFormat.format(Date())
    
    // Attempt to infer connection type:
    val connectionType = if (approval.redirectHost == "127.0.0.1" || approval.redirectHost == "localhost") {
        "Local"
    } else if (approval.redirectHost.isNotEmpty()) {
        "Red / Túnel"
    } else {
        "Desconocido"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Solicitud de Conexión Externa",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text("Cliente: ${approval.clientName}")
            Text("Tipo: $connectionType")
            Text("Fecha: $dateString")

            Spacer(modifier = Modifier.height(16.dp))

            if (!isAccessGranted) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            isAccessGranted = true
                            onApprove()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Permitir Conexión")
                    }
                    OutlinedButton(
                        onClick = onDeny,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Rechazar")
                    }
                }
            } else {
                Text(
                    text = "Configurar Permisos del Cliente",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )

                PermissionSwitchWithHelp("Accesibilidad", accessibilityGranted) { accessibilityGranted = it }
                PermissionSwitchWithHelp("Micrófono / Audio", micGranted) { micGranted = it }
                PermissionSwitchWithHelp("Pantalla / Capturas", screenGranted) { screenGranted = it }
                PermissionSwitchWithHelp("Almacenamiento / Archivos", storageGranted) { storageGranted = it }
                PermissionSwitchWithHelp("Control del Dispositivo", controlGranted) { controlGranted = it }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val permissions = mutableSetOf<String>()
                        if (accessibilityGranted) permissions.add("accessibility")
                        if (micGranted) permissions.add("microphone")
                        if (screenGranted) permissions.add("screen")
                        if (storageGranted) permissions.add("storage")
                        if (controlGranted) permissions.add("control")
                        onSavePermissions(permissions)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar Permisos")
                }
            }
        }
    }
}

@Composable
private fun PermissionSwitchWithHelp(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
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
        if (label == "Accesibilidad") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { /* Open Help */ }, contentPadding = PaddingValues(0.dp)) {
                    Text("[Más información]", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { /* Open Legals */ }, contentPadding = PaddingValues(0.dp)) {
                    Text("[Legales]", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}
