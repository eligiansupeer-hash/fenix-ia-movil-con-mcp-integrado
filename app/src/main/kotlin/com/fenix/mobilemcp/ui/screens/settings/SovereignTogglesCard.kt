package com.fenix.mobilemcp.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SovereignTogglesCard(modifier: Modifier = Modifier) {
    var micEnabled by remember { mutableStateOf(true) }
    var camEnabled by remember { mutableStateOf(false) }
    var notifEnabled by remember { mutableStateOf(true) }
    var storageEnabled by remember { mutableStateOf(true) }
    
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "INTERRUPTORES DE ACCESO SOBERANO",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            ToggleRow("🎙️ Acceso a Micrófono (Voz)", micEnabled) { micEnabled = it }
            ToggleRow("📷 Acceso a Cámara (Visión)", camEnabled) { camEnabled = it }
            ToggleRow("🔔 Interceptación de Notificaciones", notifEnabled) { notifEnabled = it }
            ToggleRow("📁 Acceso a Almacenamiento Local", storageEnabled) { storageEnabled = it }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.8f))
    }
}

private fun Modifier.scale(scale: Float) = this.then(Modifier.graphicsLayer {
    scaleX = scale
    scaleY = scale
})
