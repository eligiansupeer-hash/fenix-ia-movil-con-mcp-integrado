package com.fenix.mobilemcp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuotaSolutionCard(
    onObtainKeyClick: () -> Unit,
    onChangeProviderClick: () -> Unit,
    onWaitClick: () -> Unit,
    onAddProviderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x14F59E0B)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x59F59E0B))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "⚠️ Cuota de API temporalmente agotada",
                color = Color(0xFFFBBF24),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "El proveedor ha devuelto límite de peticiones. Tienes las siguientes soluciones inmediatas:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onObtainKeyClick, modifier = Modifier.fillMaxWidth().height(36.dp)) {
                    Text("🔑 1. Obtener nueva clave en consola oficial", fontSize = 11.sp)
                }
                OutlinedButton(onClick = onChangeProviderClick, modifier = Modifier.fillMaxWidth().height(36.dp)) {
                    Text("🔄 2. Cambiar a modelo local ($0)", fontSize = 11.sp)
                }
                OutlinedButton(onClick = onWaitClick, modifier = Modifier.fillMaxWidth().height(36.dp)) {
                    Text("⏳ 3. Esperar Restablecimiento", fontSize = 11.sp)
                }
                OutlinedButton(onClick = onAddProviderClick, modifier = Modifier.fillMaxWidth().height(36.dp)) {
                    Text("➕ 4. Agregar otro Proveedor", fontSize = 11.sp)
                }
            }
        }
    }
}
