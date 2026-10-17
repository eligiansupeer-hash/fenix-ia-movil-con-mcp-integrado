@file:Suppress("FunctionNaming", "UnusedPrivateMember", "LongMethod", "LongParameterList")

package com.fenix.mobilemcp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fenix.mobilemcp.R
import com.fenix.mobilemcp.data.model.BindingAddress
import com.fenix.mobilemcp.data.model.ServerStatus
import com.fenix.mobilemcp.data.model.TunnelEndpoint
import com.fenix.mobilemcp.data.model.TunnelProviderType
import com.fenix.mobilemcp.data.model.TunnelStatus
import com.fenix.mobilemcp.ui.theme.AndroidRemoteControlMcpTheme
import com.fenix.mobilemcp.ui.theme.WarningAmber

private const val TOKEN_MASK = "••••••••-••••-••••-••••-••••••••••••"
private const val MCP_DOCS_URL = "https://modelcontextprotocol.io/"

/**
 * Visual content of the Public URL row when it is shown.
 */
internal sealed interface TunnelRowContent {
    data object Loading : TunnelRowContent

    data class Connected(
        val endpoints: List<TunnelEndpoint>,
    ) : TunnelRowContent

    data class Failed(
        val message: String,
    ) : TunnelRowContent
}

internal fun tunnelRowContent(
    tunnelEnabled: Boolean,
    serverStatus: ServerStatus,
    tunnelStatus: TunnelStatus,
): TunnelRowContent? {
    val serverActive =
        serverStatus is ServerStatus.Running || serverStatus is ServerStatus.Starting
    if (!tunnelEnabled || !serverActive) return null
    return when (tunnelStatus) {
        is TunnelStatus.Connected -> TunnelRowContent.Connected(tunnelStatus.endpoints)
        is TunnelStatus.Error -> TunnelRowContent.Failed(tunnelStatus.message)
        TunnelStatus.Connecting, TunnelStatus.Disconnected -> TunnelRowContent.Loading
    }
}

internal fun buildConnectionString(
    serverUrl: String,
    tunnelEndpoints: List<TunnelEndpoint>,
    bearerToken: String,
): String =
    buildString {
        append("URL: $serverUrl")
        tunnelEndpoints.forEach { append("\nTunnel: ${it.url}/mcp") }
        if (bearerToken.isNotEmpty()) {
            append("\nBearer Token: $bearerToken")
        }
    }

internal fun buildMcpJsonConfig(
    mcpUrl: String,
    bearerToken: String,
): String =
    buildString {
        append("{\n")
        append("  \"mcpServers\": {\n")
        append("    \"fenix-mobile\": {\n")
        append("      \"url\": \"$mcpUrl\"")
        if (bearerToken.isNotBlank()) {
            append(",\n      \"headers\": {\n")
            append("        \"Authorization\": \"Bearer $bearerToken\"\n")
            append("      }\n")
        } else {
            append("\n")
        }
        append("    }\n")
        append("  }\n")
        append("}")
    }

@Composable
fun ConnectionInfoCard(
    bindingAddress: BindingAddress,
    ipAddress: String,
    port: Int,
    httpsEnabled: Boolean,
    bearerToken: String,
    onCopyAll: (String) -> Unit,
    tunnelEnabled: Boolean = false,
    serverStatus: ServerStatus = ServerStatus.Stopped,
    tunnelStatus: TunnelStatus = TunnelStatus.Disconnected,
    onCopyUrl: ((String) -> Unit)? = null,
    onCopyToken: ((String) -> Unit)? = null,
    onCopyJson: ((String) -> Unit)? = null,
    onEnableTunnel: (() -> Unit)? = null,
    onShare: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showToken by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val displayAddress =
        when (bindingAddress) {
            BindingAddress.LOCALHOST -> "127.0.0.1"
            BindingAddress.NETWORK -> ipAddress
        }
    val scheme = if (httpsEnabled) "https" else "http"
    val localServerUrl = "$scheme://$displayAddress:$port/mcp"
    val displayToken = if (showToken) bearerToken else TOKEN_MASK

    val rowContent = tunnelRowContent(tunnelEnabled, serverStatus, tunnelStatus)
    val isTunnelConnected = rowContent is TunnelRowContent.Connected && rowContent.endpoints.isNotEmpty()
    val activePublicEndpoint = (rowContent as? TunnelRowContent.Connected)?.endpoints?.firstOrNull()
    val effectivePublicMcpUrl =
        if (isTunnelConnected && activePublicEndpoint != null) {
            "${activePublicEndpoint.url}/mcp"
        } else {
            localServerUrl
        }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Header con Icono
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.connection_info_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.connection_info_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sección 1: URL Pública para Gemini / Claude
            if (isTunnelConnected && activePublicEndpoint != null) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.connection_info_public_url_clean_label),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = effectivePublicMcpUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Botón de 1 toque para copiar URL limpia
                        Button(
                            onClick = {
                                onCopyUrl?.invoke(effectivePublicMcpUrl) ?: onCopyAll(effectivePublicMcpUrl)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.connection_info_copy_url_only))
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.connection_info_public_url_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        TextButton(
                            onClick = { uriHandler.openUri(MCP_DOCS_URL) },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(
                                text = stringResource(R.string.connection_info_docs_link),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            } else if (rowContent is TunnelRowContent.Loading) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.remote_access_status_connecting),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else if (rowContent is TunnelRowContent.Failed) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.remote_access_status_error, rowContent.message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            } else {
                // Túnel apagado o sin conexión externa activa
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = WarningAmber.copy(alpha = 0.12f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.connection_info_tunnel_offline_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        if (onEnableTunnel != null && serverStatus is ServerStatus.Running) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = onEnableTunnel,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.connection_info_tunnel_enable_quick))
                            }
                        }
                    }
                }
            }

            // Sección 2: Token de Acceso
            if (bearerToken.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.connection_info_token),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayToken,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = stringResource(
                                        if (showToken) R.string.config_token_hide else R.string.config_token_show,
                                    ),
                                )
                            }
                            IconButton(onClick = {
                                onCopyToken?.invoke(bearerToken) ?: onCopyAll(bearerToken)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.connection_info_copy_token_only),
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.connection_info_token_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sección 3: Opciones Avanzadas (Colapsable)
            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.connection_info_advanced_toggle))
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "${stringResource(R.string.connection_info_ip)}: $displayAddress",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "${stringResource(R.string.connection_info_port)}: $port",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "${stringResource(R.string.connection_info_url)}: $localServerUrl",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val connectionString =
                        buildConnectionString(
                            serverUrl = localServerUrl,
                            tunnelEndpoints = (rowContent as? TunnelRowContent.Connected)?.endpoints ?: emptyList(),
                            bearerToken = bearerToken,
                        )

                    val mcpJson = buildMcpJsonConfig(effectivePublicMcpUrl, bearerToken)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                onCopyJson?.invoke(mcpJson) ?: onCopyAll(mcpJson)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.connection_info_copy_json),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }

                        OutlinedButton(
                            onClick = { onShare(connectionString) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.connection_info_share),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionInfoCardPreview() {
    AndroidRemoteControlMcpTheme {
        ConnectionInfoCard(
            bindingAddress = BindingAddress.LOCALHOST,
            ipAddress = "192.168.1.100",
            port = 8080,
            httpsEnabled = false,
            bearerToken = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            tunnelEnabled = true,
            serverStatus = ServerStatus.Running(port = 8080, bindingAddress = "127.0.0.1"),
            tunnelStatus =
                TunnelStatus.Connected(
                    endpoints = listOf(TunnelEndpoint("https://ships-stamps-king-interstate.trycloudflare.com", valid = true)),
                    providerType = TunnelProviderType.CLOUDFLARE,
                ),
            onCopyAll = {},
            onShare = {},
        )
    }
}
