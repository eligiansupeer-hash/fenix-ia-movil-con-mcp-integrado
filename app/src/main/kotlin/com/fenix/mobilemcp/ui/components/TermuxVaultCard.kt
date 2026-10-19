package com.fenix.mobilemcp.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fenix.mobilemcp.termux.TermuxBridgeStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TermuxVaultCard(
    status: TermuxBridgeStatus,
    onDirectDownloadTermux: () -> Unit,
    onActivateTermux: () -> Unit,
    onRefreshStatus: () -> Unit,
    onInstallOpenCode: () -> Unit,
    onInstallAntigravity: () -> Unit,
    onStartLoginAntigravity: (slot: String) -> Unit,
    onLogoutAntigravity: (slot: String) -> Unit,
    onSelectAntigravitySlot: (slot: String) -> Unit,
    onToggleFailover: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var isDownloadingTermux by remember { mutableStateOf(false) }
    var isActivatingTermux by remember { mutableStateOf(false) }
    var isDownloadingOpenCode by remember { mutableStateOf(false) }
    var isConnectingOpenCode by remember { mutableStateOf(false) }
    var isDownloadingAntigravity by remember { mutableStateOf(false) }
    var isConnectingAntigravity by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        // Encabezado de Sección
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Motores Locales Soberanos (Termux)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onRefreshStatus,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Actualizar estado",
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // 1. TARJETA TERMUX BASE
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📟 Entorno Local Termux",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (status.isBridgeActive) "● Activo (127.0.0.1:8081)" else if (status.isAppInstalled) "○ En espera" else "○ No instalado",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (status.isBridgeActive) Color(0xFF2E7D32) else if (status.isAppInstalled) Color(0xFFE65100) else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Termux permite ejecutar agentes de IA (OpenCode y Antigravity) directamente en el procesador de tu móvil, pudiendo usar sus cuotas gratuitas amplias en el chat. También permite realizar tareas de programación en distintos lenguajes de código, instalar librerías, etc.; es una herramienta pensada para la programación y la ejecución de código.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botón Descargar Termux APK
                    if (status.isAppInstalled) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Instalado ✓", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                isDownloadingTermux = true
                                onDirectDownloadTermux()
                                coroutineScope.launch {
                                    delay(4000)
                                    isDownloadingTermux = false
                                    onRefreshStatus()
                                }
                            },
                            enabled = !isDownloadingTermux,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isDownloadingTermux) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(4.dp))
                                Text("Descargando...", fontSize = 11.sp)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Descargar APK", fontSize = 12.sp)
                            }
                        }
                    }

                    // Botón Activar Termux
                    Button(
                        onClick = {
                            isActivatingTermux = true
                            onActivateTermux()
                            coroutineScope.launch {
                                delay(2000)
                                isActivatingTermux = false
                                onRefreshStatus()
                            }
                        },
                        enabled = status.isAppInstalled && !isActivatingTermux,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (status.isBridgeActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isActivatingTermux) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text("Activando...", fontSize = 11.sp)
                        } else if (status.isBridgeActive) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Activo ●", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Activar Termux", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 2. TARJETA OPENCODE
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🤖 OpenCode CLI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (status.opencodeConnected) "● Conectado" else if (status.opencodeInstalled) "○ Instalado" else "○ No instalado",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (status.opencodeConnected) Color(0xFF2E7D32) else if (status.opencodeInstalled) Color(0xFF1565C0) else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Motor CLI multi-agente para análisis de código, ejecución y tareas locales sin depender de cuotas externas.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!status.isAppInstalled) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Debes instalar Termux primero", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Botón Descargar / Instalado
                        if (status.opencodeInstalled) {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Instalado ✓", fontSize = 11.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    isDownloadingOpenCode = true
                                    onInstallOpenCode()
                                    coroutineScope.launch {
                                        delay(3000)
                                        isDownloadingOpenCode = false
                                        onRefreshStatus()
                                    }
                                },
                                enabled = status.isBridgeActive && !isDownloadingOpenCode,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isDownloadingOpenCode) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Descargando...", fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Descargar", fontSize = 11.sp)
                                }
                            }
                        }

                        // Botón Conectar / Conectado
                        Button(
                            onClick = {
                                isConnectingOpenCode = true
                                onRefreshStatus()
                                coroutineScope.launch {
                                    delay(1000)
                                    isConnectingOpenCode = false
                                    onRefreshStatus()
                                }
                            },
                            enabled = status.opencodeInstalled && !isConnectingOpenCode,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (status.opencodeConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isConnectingOpenCode) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text("Conectando...", fontSize = 11.sp)
                            } else if (status.opencodeConnected) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Conectado ●", fontSize = 11.sp)
                            } else {
                                Text("Conectar", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // 3. TARJETA ANTIGRAVITY
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🌌 Antigravity CLI",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (status.antigravityConnected) "● Conectado" else if (status.antigravityInstalled) "○ Instalado" else "○ No instalado",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (status.antigravityConnected) Color(0xFF2E7D32) else if (status.antigravityInstalled) Color(0xFF1565C0) else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Agente oficial Google DeepMind / Antigravity con soporte de inferencia avanzada y ejecución en Bionic ARM64.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!status.isAppInstalled) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Debes instalar Termux primero", fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Botón Descargar / Instalado
                        if (status.antigravityInstalled) {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Instalado ✓", fontSize = 11.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    isDownloadingAntigravity = true
                                    onInstallAntigravity()
                                    coroutineScope.launch {
                                        delay(3000)
                                        isDownloadingAntigravity = false
                                        onRefreshStatus()
                                    }
                                },
                                enabled = status.isBridgeActive && !isDownloadingAntigravity,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isDownloadingAntigravity) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Descargando...", fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Descargar", fontSize = 11.sp)
                                }
                            }
                        }

                        // Botón Conectar / Conectado
                        Button(
                            onClick = {
                                isConnectingAntigravity = true
                                onRefreshStatus()
                                coroutineScope.launch {
                                    delay(1000)
                                    isConnectingAntigravity = false
                                    onRefreshStatus()
                                }
                            },
                            enabled = status.antigravityInstalled && !isConnectingAntigravity,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (status.antigravityConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isConnectingAntigravity) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text("Conectando...", fontSize = 11.sp)
                            } else if (status.antigravityConnected) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Conectado ●", fontSize = 11.sp)
                            } else {
                                Text("Conectar", fontSize = 11.sp)
                            }
                        }
                    }

                    // SLOTS DE CUENTAS (SOLO VISIBLES TRAS CONECTARSE EFECTIVAMENTE)
                    if (status.antigravityConnected) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Pool Multicuenta Antigravity (3 Cuentas)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(6.dp))

                                status.slots.forEach { slot ->
                                    val isSelected = slot.id == status.activeSlot
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable { onSelectAntigravitySlot(slot.id) },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isSelected) "● " else "○ ",
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "${slot.label} ${if (isSelected) "(Activa)" else ""}",
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                        Text(
                                            text = if (slot.hasToken) "Registrada ✓" else "Sin configurar",
                                            fontSize = 11.sp,
                                            color = if (slot.hasToken) Color(0xFF2E7D32) else Color.Gray
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onStartLoginAntigravity(status.activeSlot) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Login Google", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { onLogoutAntigravity(status.activeSlot) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cerrar Sesión", fontSize = 11.sp)
                                    }
                                }

                                Spacer(Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Auto-failover ante límite de cuota (429)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Switch(
                                        checked = status.autoFailover,
                                        onCheckedChange = { onToggleFailover(it) },
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
