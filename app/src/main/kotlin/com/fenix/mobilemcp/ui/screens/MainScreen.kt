@file:Suppress("FunctionNaming", "LongMethod")

package com.fenix.mobilemcp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fenix.mobilemcp.R
import com.fenix.mobilemcp.ui.components.UpdateAvailableBanner
import com.fenix.mobilemcp.ui.navigation.SettingsRoute
import com.fenix.mobilemcp.ui.navigation.TopLevelRoute
import com.fenix.mobilemcp.ui.components.FloatingOverlayBanner
import com.fenix.mobilemcp.ui.viewmodels.MainViewModel
import com.fenix.mobilemcp.ui.viewmodels.UpdateViewModel

@Composable
fun MainScreen(
    onRequestNotificationPermission: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onRequestMicrophonePermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    var selectedTabRoute by rememberSaveable { mutableStateOf(TopLevelRoute.Chat.route) }
    var pendingSettingsRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val availableUpdate by updateViewModel.availableUpdate.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showFloatingBanner by rememberSaveable { mutableStateOf(true) } // Simulacion inicial para QA

    // Back on the other tabs returns to the Chat tab instead of leaving the app.
    BackHandler(enabled = selectedTabRoute != TopLevelRoute.Chat.route) {
        selectedTabRoute = TopLevelRoute.Chat.route
    }

    Scaffold(
        topBar = {
            Column {
                if (showFloatingBanner) {
                    FloatingOverlayBanner(
                        title = "\uD83D\uDD14 Solicitud de Conexión Externa",
                        scope = "Read-Only",
                        description = "Claude Desktop solicita vincularse al MCP local vía túnel persistente.",
                        onAllow = { showFloatingBanner = false },
                        onReject = { showFloatingBanner = false }
                    )
                }
                availableUpdate?.let { update ->
                    UpdateAvailableBanner(
                        versionName = update.versionName,
                        onClick = {
                            // No-op if the device has no browser to handle ACTION_VIEW (never crash).
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl))) }
                        },
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple(TopLevelRoute.Chat, Icons.Default.Forum, stringResource(R.string.tab_chat)),
                    Triple(TopLevelRoute.Studio, Icons.Default.Code, stringResource(R.string.tab_studio)),
                    Triple(TopLevelRoute.Marketplace, Icons.Default.Extension, "F-Market"),
                    Triple(TopLevelRoute.Server, Icons.Default.Dns, stringResource(R.string.tab_server)),
                    Triple(TopLevelRoute.Settings, Icons.Default.Settings, stringResource(R.string.tab_settings)),
                ).forEach { (route, icon, label) ->
                    NavigationBarItem(
                        selected = selectedTabRoute == route.route,
                        onClick = { selectedTabRoute = route.route },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, maxLines = 1, softWrap = false) },
                    )
                }
            }
        },
    ) { paddingValues ->
        when (selectedTabRoute) {
            TopLevelRoute.Chat.route -> {
                ChatScreen(modifier = Modifier.padding(paddingValues))
            }

            TopLevelRoute.Studio.route -> {
                StudioIdeScreen(modifier = Modifier.padding(paddingValues))
            }

            TopLevelRoute.Marketplace.route -> {
                MarketplaceScreen(
                    onOpenInStudio = {
                        selectedTabRoute = TopLevelRoute.Studio.route
                    },
                    modifier = Modifier.padding(paddingValues),
                )
            }

            TopLevelRoute.Server.route -> {
                ServerTabScreen(
                    onNavigateToPermissions = {
                        pendingSettingsRoute = SettingsRoute.Permissions.route
                        selectedTabRoute = TopLevelRoute.Settings.route
                    },
                    onNavigateToNetworkSettings = {
                        pendingSettingsRoute = SettingsRoute.General.route
                        selectedTabRoute = TopLevelRoute.Settings.route
                    },
                    onNavigateToTunnelSettings = {
                        pendingSettingsRoute = SettingsRoute.Tunnel.route
                        selectedTabRoute = TopLevelRoute.Settings.route
                    },
                    onOpenPrivacySettings = {
                        pendingSettingsRoute = SettingsRoute.Privacy.route
                        selectedTabRoute = TopLevelRoute.Settings.route
                    },
                    modifier = Modifier.padding(paddingValues),
                    viewModel = viewModel,
                )
            }

            TopLevelRoute.Settings.route -> {
                SettingsScreen(
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onRequestCameraPermission = onRequestCameraPermission,
                    onRequestMicrophonePermission = onRequestMicrophonePermission,
                    onRequestLocationPermission = onRequestLocationPermission,
                    pendingRoute = pendingSettingsRoute,
                    onPendingRouteConsumed = { pendingSettingsRoute = null },
                    modifier = Modifier.padding(paddingValues),
                    viewModel = viewModel,
                )
            }

            TopLevelRoute.About.route -> {
                AboutScreen(modifier = Modifier.padding(paddingValues))
            }

            else -> {
                ChatScreen(modifier = Modifier.padding(paddingValues))
            }
        }
    }
}
