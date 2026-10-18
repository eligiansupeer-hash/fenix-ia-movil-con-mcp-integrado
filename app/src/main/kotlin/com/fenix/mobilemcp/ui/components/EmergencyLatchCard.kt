package com.fenix.mobilemcp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun EmergencyLatchCard(
    isHalted: Boolean,
    onHaltClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHalted) Color(0x33EF4444) else MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isHalted) Color(0x66EF4444) else MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cerrojo de Emergencia (Emergency Latch)",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isHalted) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isHalted) "SISTEMA BLOQUEADO. Tráfico cortado." else "Corta todo tráfico y bloquea reinicios.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            if (isHalted) {
                Button(
                    onClick = onResetClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("REARMAR")
                }
            } else {
                Button(
                    onClick = onHaltClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("PARAR AHORA", color = Color.White)
                }
            }
        }
    }
}
