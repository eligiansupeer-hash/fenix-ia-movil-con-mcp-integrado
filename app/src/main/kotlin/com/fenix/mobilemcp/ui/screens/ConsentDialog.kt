package com.fenix.mobilemcp.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun NetworkConsentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Activar Servidor MCP",
    message: String = "Al activar el servidor MCP y conectarlo remotamente (Túnel), expondrás este servicio a internet. Asegúrate de entender los riesgos de exposición.\n\nElige 'Modo Local Seguro' para enlace exclusivo en loopback 127.0.0.1, o confirma para habilitar Modo Red/Túnel Remoto."
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Modo Red / Túnel Remoto")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Modo Local Seguro (Cancelar)")
            }
        }
    )
}
