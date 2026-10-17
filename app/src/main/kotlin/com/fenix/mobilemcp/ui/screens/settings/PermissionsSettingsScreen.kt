@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber", "LongParameterList")

package com.fenix.mobilemcp.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fenix.mobilemcp.R
import com.fenix.mobilemcp.ui.viewmodels.MainViewModel
import com.fenix.mobilemcp.utils.PermissionUtils

@Composable
private fun enabledColor(): Color = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF4CAF50)

@Composable
private fun disabledColor(): Color = if (isSystemInDarkTheme()) Color(0xFFEF5350) else Color(0xFFF44336)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsSettingsScreen(
    onBack: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onRequestMicrophonePermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current

    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsStateWithLifecycle()
    val isNotificationPermissionGranted by viewModel.isNotificationPermissionGranted.collectAsStateWithLifecycle()
    val isNotificationListenerEnabled by viewModel.isNotificationListenerEnabled.collectAsStateWithLifecycle()
    val isCameraPermissionGranted by viewModel.isCameraPermissionGranted.collectAsStateWithLifecycle()
    val isMicrophonePermissionGranted by viewModel.isMicrophonePermissionGranted.collectAsStateWithLifecycle()
    val isLocationPermissionGranted by viewModel.isLocationPermissionGranted.collectAsStateWithLifecycle()

    // Refresh permissions on ON_RESUME
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshPermissionStatus(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_permissions_title)) },
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
            // Tarjeta exhaustiva explicativa de permisos del sistema (> 5 líneas y enlaces oficiales)
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_permissions_info_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_permissions_info_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            uriHandler.openUri("https://developer.android.com/guide/topics/ui/accessibility")
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_permissions_docs_link),
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

            // ---- Required ----
            PermissionSectionHeader(
                title = stringResource(R.string.permission_section_required_title),
                subtitle = stringResource(R.string.permission_section_required_subtitle),
            )
            val hasWriteSecure = PermissionUtils.hasWriteSecureSettings(context)
            PermissionRow(
                label = stringResource(R.string.permission_accessibility),
                rationale = stringResource(R.string.permission_accessibility_rationale) +
                    if (hasWriteSecure) " (Control a demanda activo)" else "",
                isEnabled = isAccessibilityEnabled,
                buttonText =
                    when {
                        hasWriteSecure && isAccessibilityEnabled -> "Desactivar"
                        hasWriteSecure && !isAccessibilityEnabled -> "Activar"
                        isAccessibilityEnabled -> "Configurar"
                        else -> stringResource(R.string.permission_enable)
                    },
                onAction = {
                    if (hasWriteSecure) {
                        PermissionUtils.setAccessibilityServiceEnabled(
                            context,
                            com.fenix.mobilemcp.services.accessibility.McpAccessibilityService::class.java,
                            !isAccessibilityEnabled,
                        )
                        viewModel.refreshPermissionStatus(context)
                    } else {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                },
                actionEnabled = true,
            )

            if (!isAccessibilityEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "¿El interruptor aparece en gris (Ajuste restringido)?",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "En Android 13+ las apps instaladas manualmente requieren autorización previa: toca el botón abajo, pulsa el menú de 3 puntos (⋮) en la esquina superior derecha y selecciona 'Permitir ajustes restringidos'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("Abrir Información de la App")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Required if you use a feature ----
            PermissionSectionHeader(
                title = stringResource(R.string.permission_section_required_if_title),
                subtitle = stringResource(R.string.permission_section_required_if_subtitle),
            )
            PermissionRow(
                label = stringResource(R.string.permission_notifications),
                rationale = stringResource(R.string.permission_notifications_rationale),
                isEnabled = isNotificationPermissionGranted,
                buttonText =
                    if (isNotificationPermissionGranted) {
                        stringResource(R.string.permission_granted)
                    } else {
                        stringResource(R.string.permission_grant)
                    },
                onAction = onRequestNotificationPermission,
                actionEnabled = !isNotificationPermissionGranted,
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionRow(
                label = stringResource(R.string.permission_notification_listener),
                rationale = stringResource(R.string.permission_notification_listener_rationale),
                isEnabled = isNotificationListenerEnabled,
                buttonText =
                    if (isNotificationListenerEnabled) {
                        stringResource(R.string.permission_enabled)
                    } else {
                        stringResource(R.string.permission_enable)
                    },
                onAction = { PermissionUtils.openNotificationListenerSettings(context) },
                actionEnabled = !isNotificationListenerEnabled,
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionRow(
                label = stringResource(R.string.permission_camera),
                rationale = stringResource(R.string.permission_camera_rationale),
                isEnabled = isCameraPermissionGranted,
                buttonText =
                    if (isCameraPermissionGranted) {
                        stringResource(R.string.permission_granted)
                    } else {
                        stringResource(R.string.permission_grant)
                    },
                onAction = onRequestCameraPermission,
                actionEnabled = !isCameraPermissionGranted,
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionRow(
                label = stringResource(R.string.permission_location),
                rationale = stringResource(R.string.permission_location_rationale),
                isEnabled = isLocationPermissionGranted,
                buttonText =
                    if (isLocationPermissionGranted) {
                        stringResource(R.string.permission_granted)
                    } else {
                        stringResource(R.string.permission_grant)
                    },
                onAction = onRequestLocationPermission,
                actionEnabled = !isLocationPermissionGranted,
            )

            // Background Location is geofence-only; rendered via a flavor seam (gms only).
            // The gms seam owns its own leading spacer so foss shows no dangling gap.
            BackgroundLocationPermissionRow()

            Spacer(modifier = Modifier.height(8.dp))

            val hasAllFiles = PermissionUtils.hasAllFilesAccess()
            PermissionRow(
                label = "Gestión total de archivos",
                rationale = "Permite a la IA buscar, clasificar y organizar documentos, código y memoria en todo el teléfono sin Root.",
                isEnabled = hasAllFiles,
                buttonText = if (hasAllFiles) "Concedido" else "Conceder",
                onAction = { PermissionUtils.openAllFilesAccessSettings(context) },
                actionEnabled = !hasAllFiles,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Optional ----
            PermissionSectionHeader(
                title = stringResource(R.string.permission_section_optional_title),
                subtitle = stringResource(R.string.permission_section_optional_subtitle),
            )
            PermissionRow(
                label = stringResource(R.string.permission_microphone),
                rationale = stringResource(R.string.permission_microphone_rationale),
                isEnabled = isMicrophonePermissionGranted,
                buttonText =
                    if (isMicrophonePermissionGranted) {
                        stringResource(R.string.permission_granted)
                    } else {
                        stringResource(R.string.permission_grant)
                    },
                onAction = onRequestMicrophonePermission,
                actionEnabled = !isMicrophonePermissionGranted,
            )
        }
    }
}

@Composable
internal fun PermissionRow(
    label: String,
    rationale: String,
    isEnabled: Boolean,
    buttonText: String,
    onAction: () -> Unit,
    actionEnabled: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = if (isEnabled) "$label enabled" else "$label disabled",
            tint = if (isEnabled) enabledColor() else disabledColor(),
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = rationale,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onAction, enabled = actionEnabled) {
            Text(text = buttonText)
        }
    }
}

@Composable
private fun PermissionSectionHeader(
    title: String,
    subtitle: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
}
