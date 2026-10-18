@file:Suppress("FunctionNaming", "LongMethod")

package com.fenix.mobilemcp.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fenix.mobilemcp.R
import com.fenix.mobilemcp.data.model.CertificateSource
import com.fenix.mobilemcp.data.model.ServerStatus
import com.fenix.mobilemcp.ui.viewmodels.MainViewModel
import com.fenix.mobilemcp.ui.screens.settings.MasterKeySettingsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val hostnameInput by viewModel.hostnameInput.collectAsStateWithLifecycle()
    val hostnameError by viewModel.hostnameError.collectAsStateWithLifecycle()

    val isEnabled =
        serverStatus !is ServerStatus.Running &&
            serverStatus !is ServerStatus.Starting

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_security_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            windowInsets = WindowInsets(0),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            // Tarjeta 1: Firewall Bancario y Regla de Oro
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_security_info_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_security_info_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            uriHandler.openUri("https://developer.android.com/privacy-and-security")
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_security_docs_link),
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

            Spacer(modifier = Modifier.height(16.dp))

            MasterKeySettingsCard()

            Spacer(modifier = Modifier.height(16.dp))

            // Tarjeta 2: Acceso Rápido a Opciones de Desarrollador / ADB
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_security_dev_options_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_security_dev_options_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                            } catch (_: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_security_dev_options_button))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tarjeta 3: Interruptor Físico de Emergencia (Hardware Kill-Switch / Anti-Bucle)
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Interruptor Físico de Emergencia (Kill-Switch)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Si un agente de IA entra en un bucle infinito de toques o la pantalla responde lento, puedes abortar TODOS los procesos al instante sin tocar la pantalla:\n" +
                            "• Gesto Físico 1: Presiona 3 veces rápido el botón físico [Volumen Abajo].\n" +
                            "• Gesto Físico 2: Presiona al mismo tiempo [Volumen Arriba + Volumen Abajo].\n" +
                            "El teléfono vibrará dos veces, cancelará toda herramienta en curso y apagará el servidor MCP.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            com.fenix.mobilemcp.security.EmergencyKillSwitchManager.triggerEmergencyHalt(
                                context = context,
                                triggerReason = "Botón Manual en Seguridad",
                            )
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("🛑 Ejecutar Corte de Emergencia Ahora")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // HTTPS Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.config_https_enabled_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = serverConfig.httpsEnabled,
                    onCheckedChange = viewModel::updateHttpsEnabled,
                    enabled = isEnabled,
                )
            }

            // HTTPS Certificate Section (visible only when HTTPS is enabled)
            AnimatedVisibility(visible = serverConfig.httpsEnabled) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.config_certificate_title),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Certificate source selector
                    Column(modifier = Modifier.selectableGroup()) {
                        CertificateSource.entries.forEach { source ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = source == serverConfig.certificateSource,
                                            onClick = { viewModel.updateCertificateSource(source) },
                                            role = Role.RadioButton,
                                            enabled = isEnabled,
                                        ).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = source == serverConfig.certificateSource,
                                    onClick = null,
                                    enabled = isEnabled,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text =
                                        when (source) {
                                            CertificateSource.AUTO_GENERATED -> {
                                                stringResource(R.string.config_cert_auto_generated)
                                            }

                                            CertificateSource.CUSTOM -> {
                                                stringResource(R.string.config_cert_custom)
                                            }
                                        },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }

                    if (serverConfig.certificateSource == CertificateSource.AUTO_GENERATED) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = hostnameInput,
                            onValueChange = viewModel::updateCertificateHostname,
                            label = { Text(stringResource(R.string.config_hostname_label)) },
                            isError = hostnameError != null,
                            supportingText = hostnameError?.let { { Text(it) } },
                            singleLine = true,
                            enabled = isEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
