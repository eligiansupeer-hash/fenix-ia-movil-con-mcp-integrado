package com.fenix.mobilemcp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FloatingOverlayBanner(
    title: String,
    scope: String,
    description: String,
    onAllow: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(14.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827).copy(alpha = 0.98f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    color = Color(0xFF93C5FD),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0x263B82F6),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4D3B82F6)),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "Scope: $scope",
                        color = Color(0xFF60A5FA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAllow,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("✓ Permitir Conexión", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = onReject,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("✕ Rechazar", fontSize = 11.sp)
                }
            }
        }
    }
}
