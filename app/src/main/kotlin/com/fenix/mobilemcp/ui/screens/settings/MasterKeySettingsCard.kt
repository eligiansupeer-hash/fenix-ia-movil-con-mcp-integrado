package com.fenix.mobilemcp.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun MasterKeySettingsCard(
    modifier: Modifier = Modifier
) {
    var masterKey by remember { mutableStateOf("master_fenix_key_hash_8f992a") } // Placeholder, en un futuro se integraria el KeyStore.

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = Color(0x0F8B5CF6)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x668B5CF6))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = Color(0xFFC084FC),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Llave Maestra del Propietario",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC084FC)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = masterKey,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Solo extensiones con esta firma pueden enlazar al servicio de Accesibilidad de Fénix.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}
