package com.fenix.mobilemcp.services.mcp

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fenix.mobilemcp.McpApplication
import com.fenix.mobilemcp.R
import com.fenix.mobilemcp.data.model.OptionalToolPermissions
import com.fenix.mobilemcp.data.model.ServerConfig
import com.fenix.mobilemcp.data.model.ServerLogEntry
import com.fenix.mobilemcp.data.model.ServerStatus
import com.fenix.mobilemcp.data.model.ToolPermissionsConfig
import com.fenix.mobilemcp.data.model.TunnelStatus
import com.fenix.mobilemcp.data.repository.OAuthClientRepository
import com.fenix.mobilemcp.data.repository.ServerLogRepository
import com.fenix.mobilemcp.data.repository.SettingsRepository
import com.fenix.mobilemcp.geo.GeoIpResolver
import com.fenix.mobilemcp.mcp.CertificateManager
import com.fenix.mobilemcp.mcp.HttpsMaterial
import com.fenix.mobilemcp.mcp.McpServer
import com.fenix.mobilemcp.mcp.oauth.AuthorizationCodeStore
import com.fenix.mobilemcp.mcp.oauth.JwtTokenService
import com.fenix.mobilemcp.mcp.oauth.OAuthApprovalCoordinator
import com.fenix.mobilemcp.mcp.oauth.OAuthServerDeps
import com.fenix.mobilemcp.mcp.tools.LoggedToolRegistrar
import com.fenix.mobilemcp.mcp.tools.McpToolUtils
import com.fenix.mobilemcp.mcp.tools.ReferenceCountedToolCallIndicator
import com.fenix.mobilemcp.mcp.tools.ToolCallIndicator
import com.fenix.mobilemcp.mcp.tools.registerAppManagementTools
import com.fenix.mobilemcp.mcp.tools.registerCameraTools
import com.fenix.mobilemcp.mcp.tools.registerFileTools
import com.fenix.mobilemcp.mcp.tools.registerGestureTools
import com.fenix.mobilemcp.mcp.tools.registerIntentTools
import com.fenix.mobilemcp.mcp.tools.registerLocationTools
import com.fenix.mobilemcp.mcp.tools.registerNodeActionTools
import com.fenix.mobilemcp.mcp.tools.registerNotificationTools
import com.fenix.mobilemcp.mcp.tools.registerScreenIntrospectionTools
import com.fenix.mobilemcp.mcp.tools.registerSharingTools
import com.fenix.mobilemcp.mcp.tools.registerSystemActionTools
import com.fenix.mobilemcp.mcp.tools.registerTextInputTools
import com.fenix.mobilemcp.mcp.tools.registerTouchActionTools
import com.fenix.mobilemcp.mcp.tools.registerUtilityTools
import com.fenix.mobilemcp.privacy.PlaceholderSubstitutor
import com.fenix.mobilemcp.privacy.PrivacyModeManager
import com.fenix.mobilemcp.privacy.PrivacyModeStatus
import com.fenix.mobilemcp.privacy.PrivacyToolGate
import com.fenix.mobilemcp.privacy.PseudonymStore
import com.fenix.mobilemcp.privacy.ner.NerCache
import com.fenix.mobilemcp.services.accessibility.AccessibilityNodeCache
import com.fenix.mobilemcp.services.accessibility.AccessibilityServiceProvider
import com.fenix.mobilemcp.services.accessibility.AccessibilityToolCallIndicator
import com.fenix.mobilemcp.services.accessibility.AccessibilityTreeParser
import com.fenix.mobilemcp.services.accessibility.ActionExecutor
import com.fenix.mobilemcp.services.accessibility.CompactTreeFormatter
import com.fenix.mobilemcp.services.accessibility.ElementFinder
import com.fenix.mobilemcp.services.accessibility.ScreenStateSnapshotCache
import com.fenix.mobilemcp.services.accessibility.TypeInputController
import com.fenix.mobilemcp.services.accessibility.WebViewNodeMerger
import com.fenix.mobilemcp.services.apps.AppManager
import com.fenix.mobilemcp.services.camera.CameraProvider
import com.fenix.mobilemcp.services.intents.IntentDispatcher
import com.fenix.mobilemcp.services.location.LocationProvider
import com.fenix.mobilemcp.services.notifications.McpNotificationListenerService
import com.fenix.mobilemcp.services.notifications.NotificationProvider
import com.fenix.mobilemcp.services.screencapture.ScreenCaptureProvider
import com.fenix.mobilemcp.services.screencapture.ScreenshotAnnotator
import com.fenix.mobilemcp.services.screencapture.ScreenshotEncoder
import com.fenix.mobilemcp.services.screencapture.ScreenshotRedactor
import com.fenix.mobilemcp.services.sharing.EphemeralFileLinkService
import com.fenix.mobilemcp.services.sharing.SharedContentInbox
import com.fenix.mobilemcp.services.storage.FileOperationProvider
import com.fenix.mobilemcp.services.storage.StorageLocationProvider
import com.fenix.mobilemcp.services.tunnel.TunnelManager
import com.fenix.mobilemcp.ui.MainActivity
import com.fenix.mobilemcp.utils.NetworkUtils
import com.fenix.mobilemcp.utils.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Foreground service that runs the MCP server (HTTP by default, optional HTTPS).
 *
 * Lifecycle:
 * 1. Started via intent from MainActivity (start/stop button)
 * 2. Calls startForeground() with persistent notification
 * 3. Reads configuration from SettingsRepository
 * 4. Creates and starts McpServer (Ktor HTTP, optionally HTTPS)
 * 5. Updates ServerStatus via companion-level StateFlow (collected by MainViewModel)
 * 6. On stop: gracefully shuts down server, clears singleton
 */
@AndroidEntryPoint
class McpServerService : Service() {
    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var certificateManager: CertificateManager

    @Inject lateinit var actionExecutor: ActionExecutor

    @Inject lateinit var accessibilityServiceProvider: AccessibilityServiceProvider

    @Inject lateinit var screenCaptureProvider: ScreenCaptureProvider

    @Inject lateinit var treeParser: AccessibilityTreeParser

    @Inject lateinit var elementFinder: ElementFinder

    @Inject lateinit var compactTreeFormatter: CompactTreeFormatter

    @Inject lateinit var screenshotAnnotator: ScreenshotAnnotator

    @Inject lateinit var screenshotEncoder: ScreenshotEncoder

    @Inject lateinit var tunnelManager: TunnelManager

    @Inject lateinit var storageLocationProvider: StorageLocationProvider

    @Inject lateinit var fileOperationProvider: FileOperationProvider

    @Inject lateinit var appManager: AppManager

    @Inject lateinit var typeInputController: TypeInputController

    @Inject lateinit var nodeCache: AccessibilityNodeCache

    @Inject lateinit var screenStateSnapshotCache: ScreenStateSnapshotCache

    @Inject lateinit var webViewNodeMerger: WebViewNodeMerger

    @Inject lateinit var cameraProvider: CameraProvider

    @Inject lateinit var intentDispatcher: IntentDispatcher

    @Inject lateinit var notificationProvider: NotificationProvider

    @Inject lateinit var locationProvider: LocationProvider

    @Inject lateinit var ephemeralFileLinkService: EphemeralFileLinkService

    @Inject lateinit var sharedContentInbox: SharedContentInbox

    @Inject lateinit var jwtTokenService: JwtTokenService

    @Inject lateinit var oauthClientRepository: OAuthClientRepository

    @Inject lateinit var authorizationCodeStore: AuthorizationCodeStore

    @Inject lateinit var approvalCoordinator: OAuthApprovalCoordinator

    @Inject lateinit var geoIpResolver: GeoIpResolver

    @Inject lateinit var serverLogRepository: ServerLogRepository

    @Inject lateinit var privacyToolGate: PrivacyToolGate

    @Inject lateinit var placeholderSubstitutor: PlaceholderSubstitutor

    @Inject lateinit var screenshotRedactor: ScreenshotRedactor

    @Inject lateinit var privacyModeManager: PrivacyModeManager

    @Inject lateinit var pseudonymStore: PseudonymStore

    @Inject lateinit var nerCache: NerCache

    /** Config of the currently running server; used to build capability-link base URLs. */
    @Volatile
    private var activeConfig: ServerConfig? = null

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serverActive = AtomicBoolean(false)
    private var mcpServer: McpServer? = null
    private var tunnelObserverJob: Job? = null
    private var approvalObserverJob: Job? = null

    /**
     * Tracks whether the LAST [onStartCommand] action was an explicit stop. Lets [onDestroy] re-commit
     * `server_running=false` when the user explicitly stopped (a second bounded attempt in case the
     * first write in [onStartCommand] timed out), WITHOUT clearing the flag on an OEM/system kill —
     * where the last action was a start, so this stays `false` and the flag is left `true` for restart.
     */
    @Volatile
    private var lastIntentWasStop = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "McpServerService created")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        when (intent?.action) {
            ACTION_STOP -> {
                lastIntentWasStop = true
                persistServerRunning(settingsRepository, false)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                lastIntentWasStop = false
                persistServerRunning(settingsRepository, true)
                if (!serverActive.compareAndSet(false, true)) {
                    Log.w(TAG, "Server already starting or running, ignoring duplicate start request")
                } else {
                    coroutineScope.launch {
                        startServer()
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        restartMcpServerIfForeground(this, _serverStatus.value is ServerStatus.Running)
        super.onTaskRemoved(rootIntent)
    }

    @Suppress("TooGenericExceptionCaught", "LongMethod")
    private suspend fun startServer() {
        try {
            updateStatus(ServerStatus.Starting)

            // getServerConfig() guarantees ensureAuthModelMigrated() has run before the server reads
            // the auth model (prevents the cleared-token-user open-server regression).
            val config = settingsRepository.getServerConfig()
            activeConfig = config
            val toolNamePrefix = McpToolUtils.buildToolNamePrefix(config.deviceSlug)
            Log.i(
                TAG,
                "Starting MCP server with config: port=${config.port}, " +
                    "binding=${config.bindingAddress.address}, toolNamePrefix=$toolNamePrefix",
            )

            // Only get/create SSL keystore when HTTPS is enabled
            val keyStore =
                if (config.httpsEnabled) {
                    certificateManager.getOrCreateKeyStore(config)
                } else {
                    null
                }
            val keyStorePassword =
                if (config.httpsEnabled) {
                    certificateManager.getKeyStorePassword()
                } else {
                    null
                }

            // Create SDK Server instance and register all tools
            val sdkServer =
                Server(
                    serverInfo =
                        Implementation(
                            name = McpToolUtils.buildServerName(config.deviceSlug),
                            version = com.fenix.mobilemcp.BuildConfig.VERSION_NAME,
                        ),
                    options =
                        ServerOptions(
                            capabilities =
                                ServerCapabilities(
                                    tools = ServerCapabilities.Tools(listChanged = false),
                                ),
                        ),
                )
            // ALL four signals MUST come from PermissionUtils — the SAME source the UI uses
            // (MainViewModel.refreshPermissionStatus) — so the server-start gate and the settings grey-out
            // never diverge. Do NOT use cameraProvider.isCameraPermissionGranted()/isMicrophonePermissionGranted()
            // or notificationProvider.isReady() here.
            val grantedOptionalPermissions =
                OptionalToolPermissions.grantedPermissions(
                    camera = PermissionUtils.isCameraPermissionGranted(this@McpServerService),
                    microphone = PermissionUtils.isMicrophonePermissionGranted(this@McpServerService),
                    location = PermissionUtils.isLocationPermissionGranted(this@McpServerService),
                    notificationListener =
                        PermissionUtils.isNotificationListenerEnabled(
                            this@McpServerService,
                            McpNotificationListenerService::class.java,
                        ),
                )
            val effectivePerms =
                OptionalToolPermissions.effectivePermissions(config.toolPermissionsConfig, grantedOptionalPermissions)
            registerAllTools(
                sdkServer,
                toolNamePrefix,
                effectivePerms,
                config.fileSizeLimitMb,
                config.toolCallIndicatorEnabled,
            )

            // Create and start the Ktor server
            mcpServer =
                McpServer(
                    config = config,
                    httpsMaterial = buildHttpsMaterial(keyStore, keyStorePassword),
                    mcpSdkServer = sdkServer,
                    ephemeralFileLinkService = ephemeralFileLinkService,
                    oauth =
                        OAuthServerDeps(
                            jwtTokenService = jwtTokenService,
                            oauthClientRepository = oauthClientRepository,
                            authorizationCodeStore = authorizationCodeStore,
                            approvalCoordinator = approvalCoordinator,
                            geoIpResolver = geoIpResolver,
                        ),
                    serverLog = serverLogRepository,
                )
            mcpServer?.start()

            // Warm the geolocation DB off the request path so the first /authorize doesn't pay the
            // one-time gzip-inflate + mmap cost. Best-effort; a failure just leaves it lazy.
            coroutineScope.launch { geoIpResolver.resolve("8.8.8.8") }

            // Surface Privacy Mode readiness at start so the user learns of a failure now, not at the
            // first data-returning tool call. Off the request path; the result updates the manager status.
            if (config.privacyModeConfig.enabled) {
                coroutineScope.launch {
                    val message =
                        privacyStatusLogMessage(privacyModeManager.selfCheck(), applicationContext)
                    serverLogRepository.log(ServerLogEntry.Type.PRIVACY, message)
                }
            }

            updateStatus(
                ServerStatus.Running(
                    port = config.port,
                    bindingAddress = config.bindingAddress.address,
                ),
            )

            // Start tunnel if remote access is enabled. A tunnel always targets an
            // http://localhost origin, so it MUST NOT run while the server serves HTTPS.
            if (config.httpsEnabled) {
                Log.i(TAG, "Remote access tunnel disabled while HTTPS is enabled")
                tunnelManager.stop()
            } else {
                @Suppress("TooGenericExceptionCaught")
                try {
                    tunnelManager.start(config.port)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start tunnel (server continues without tunnel)", e)
                }
            }

            // Observe tunnel status for logging
            tunnelObserverJob =
                coroutineScope.launch {
                    tunnelManager.tunnelStatus.collect { status ->
                        tunnelStatusLogMessage(status)?.let {
                            serverLogRepository.log(ServerLogEntry.Type.TUNNEL, it)
                        }
                        when (status) {
                            is TunnelStatus.Connected -> {
                                Log.i(
                                    TAG,
                                    "Tunnel connected: ${status.endpoints.joinToString { it.url }} " +
                                        "(provider: ${status.providerType})",
                                )
                            }

                            is TunnelStatus.Error -> {
                                Log.w(TAG, "Tunnel error: ${status.message}")
                            }

                            is TunnelStatus.Connecting -> {
                                Log.i(TAG, "Tunnel connecting...")
                            }

                            is TunnelStatus.Disconnected -> {
                                // No-op for initial state; logged at stop time
                            }
                        }
                    }
                }

            // Observe pending OAuth approvals and surface a single heads-up notification (the service
            // is already foregrounded by onStartCommand; this is a separate, collapsed notification).
            approvalObserverJob =
                coroutineScope.launch {
                    approvalCoordinator.observePending().collect { pending ->
                        if (pending.isEmpty()) {
                            OAuthApprovalNotifier.cancel(this@McpServerService)
                        } else {
                            OAuthApprovalNotifier.post(this@McpServerService, pending.size)
                        }
                    }
                }

            Log.i(TAG, "MCP server started successfully on ${config.bindingAddress.address}:${config.port}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MCP server", e)
            updateStatus(ServerStatus.Error(e.message ?: "Unknown error starting server"))
            serverActive.set(false)
        }
    }

    /**
     * Externally-reachable base URL for capability links: the tunnel URL when a tunnel is connected,
     * otherwise the device LAN URL (`scheme://<device-ip>:<port>`).
     */
    private val currentBaseUrl: () -> String = {
        val tunnel = tunnelManager.tunnelStatus.value
        val tunnelUrl = (tunnel as? TunnelStatus.Connected)?.endpoints?.firstOrNull { it.valid }?.url
        if (tunnelUrl != null) {
            tunnelUrl
        } else {
            val cfg = activeConfig
            val scheme = if (cfg?.httpsEnabled == true) "https" else "http"
            val host =
                NetworkUtils.getDeviceIpAddress(applicationContext) ?: cfg?.bindingAddress?.address ?: "127.0.0.1"
            val port = cfg?.port ?: ServerConfig.DEFAULT_PORT
            "$scheme://$host:$port"
        }
    }

    private fun registerAllTools(
        server: Server,
        toolNamePrefix: String,
        perms: ToolPermissionsConfig,
        fileSizeLimitMb: Int,
        toolCallIndicatorEnabled: Boolean,
    ) {
        val toolCallIndicator = ReferenceCountedToolCallIndicator(AccessibilityToolCallIndicator())
        toolCallIndicator.setEnabled(toolCallIndicatorEnabled)
        val registrar =
            LoggedToolRegistrar(
                server,
                serverLogRepository,
                toolCallIndicator,
            )
        registerAccessibilityToolBundle(registrar, toolNamePrefix, perms)
        registerFileTools(registrar, storageLocationProvider, fileOperationProvider, toolNamePrefix, perms)
        registerAppManagementTools(registrar, appManager, privacyToolGate, toolNamePrefix, perms)
        registerCameraTools(registrar, cameraProvider, fileOperationProvider, toolNamePrefix, perms)
        registerIntentTools(registrar, intentDispatcher, toolNamePrefix, perms)
        registerNotificationTools(
            registrar,
            notificationProvider,
            privacyToolGate,
            placeholderSubstitutor,
            toolNamePrefix,
            perms,
        )
        registerLocationTools(registrar, locationProvider, privacyToolGate, toolNamePrefix, perms)
        registerSharingBundle(registrar, toolNamePrefix, perms, fileSizeLimitMb)
        registerFenixBundle(registrar, toolNamePrefix, perms)
    }

    /** Bundle Fénix (memoria + zonas + turnos). Construcción manual sin Hilt. */
    private fun registerFenixBundle(
        registrar: LoggedToolRegistrar,
        toolNamePrefix: String,
        perms: ToolPermissionsConfig,
    ) {
        val ctx = applicationContext
        val memDb = com.fenix.mobilemcp.memory.FenixMemoryDb(ctx)
        val matcher = com.fenix.mobilemcp.delivery.zones.ZoneMatcher()
        val index = com.fenix.mobilemcp.delivery.zones.ZoneIndexProvider.provideIndex()
        val monitor =
            com.fenix.mobilemcp.delivery.monitor.DeliveryMonitor(
                context = ctx,
                zoneMatcher = matcher,
                zoneIndex = index,
            )
        val metrics = com.fenix.mobilemcp.delivery.scheduler.TurnMetricsStore(memDb)
        val scheduler =
            com.fenix.mobilemcp.delivery.scheduler.TurnScheduler(
                context = ctx,
                deliveryMonitor = monitor,
                metricsStore = metrics,
            )
        com.fenix.mobilemcp.mcp.tools.registerFenixTools(
            registrar, memDb, matcher, index, scheduler, toolNamePrefix, perms,
        )
    }

    private fun registerAccessibilityToolBundle(
        registrar: LoggedToolRegistrar,
        toolNamePrefix: String,
        perms: ToolPermissionsConfig,
    ) {
        registerScreenIntrospectionTools(
            registrar,
            treeParser,
            accessibilityServiceProvider,
            screenCaptureProvider,
            compactTreeFormatter,
            screenshotAnnotator,
            screenshotEncoder,
            nodeCache,
            screenStateSnapshotCache,
            webViewNodeMerger,
            privacyToolGate,
            screenshotRedactor,
            toolNamePrefix,
            perms,
        )
        registerSystemActionTools(registrar, actionExecutor, accessibilityServiceProvider, toolNamePrefix, perms)
        registerTouchActionTools(registrar, actionExecutor, toolNamePrefix, perms)
        registerGestureTools(registrar, actionExecutor, toolNamePrefix, perms)
        registerNodeActionTools(
            registrar,
            treeParser,
            elementFinder,
            actionExecutor,
            accessibilityServiceProvider,
            nodeCache,
            privacyToolGate,
            placeholderSubstitutor,
            toolNamePrefix,
            perms,
        )
        registerTextInputTools(
            registrar,
            treeParser,
            actionExecutor,
            accessibilityServiceProvider,
            typeInputController,
            nodeCache,
            privacyToolGate,
            placeholderSubstitutor,
            toolNamePrefix,
            perms,
        )
        registerUtilityTools(
            registrar,
            treeParser,
            elementFinder,
            accessibilityServiceProvider,
            nodeCache,
            privacyToolGate,
            placeholderSubstitutor,
            toolNamePrefix,
            perms,
        )
    }

    private fun registerSharingBundle(
        registrar: LoggedToolRegistrar,
        toolNamePrefix: String,
        perms: ToolPermissionsConfig,
        fileSizeLimitMb: Int,
    ) {
        registerSharingTools(
            registrar,
            sharedContentInbox,
            ephemeralFileLinkService,
            fileOperationProvider,
            fileSizeLimitMb,
            currentBaseUrl,
            privacyToolGate,
            toolNamePrefix,
            perms,
        )
    }

    override fun onDestroy() {
        screenStateSnapshotCache.clear()
        // Release the Privacy Mode model and clear session-scoped pseudonym/NER caches (never persisted).
        privacyModeManager.shutdown()
        pseudonymStore.clear()
        nerCache.clear()
        Log.i(TAG, "McpServerService destroying")
        updateStatus(ServerStatus.Stopping)

        // Second, later attempt to durably commit an explicit stop, in case the first bounded write in
        // onStartCommand timed out. Gated on lastIntentWasStop so an OEM/system kill (last action was a
        // start) never clears the flag — there the flag MUST stay true for restart-if-running.
        if (lastIntentWasStop) {
            persistServerRunning(settingsRepository, false)
        }

        // Cancel tunnel status observer before stopping the tunnel
        tunnelObserverJob?.cancel()
        tunnelObserverJob = null

        // Cancel the OAuth approval observer and clear any pending approval notification.
        approvalObserverJob?.cancel()
        approvalObserverJob = null
        OAuthApprovalNotifier.cancel(this)

        // Stop tunnel first (with ANR-safe timeout).
        // Worst-case blocking time: TUNNEL_STOP_TIMEOUT_MS (3s) + SHUTDOWN_GRACE_PERIOD_MS (1s)
        // + SHUTDOWN_TIMEOUT_MS (5s) = ~9s total. This is well within the Android service
        // onDestroy ANR threshold (~200s), so blocking the main thread here is acceptable.
        @Suppress("TooGenericExceptionCaught")
        try {
            runBlocking {
                withTimeout(TUNNEL_STOP_TIMEOUT_MS) {
                    tunnelManager.stop()
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "Tunnel stop timed out after ${TUNNEL_STOP_TIMEOUT_MS}ms, proceeding with shutdown", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tunnel", e)
        }

        // Stop the Ktor server gracefully
        @Suppress("TooGenericExceptionCaught")
        try {
            mcpServer?.stop(
                gracePeriodMillis = SHUTDOWN_GRACE_PERIOD_MS,
                timeoutMillis = SHUTDOWN_TIMEOUT_MS,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during server shutdown", e)
        }
        mcpServer = null
        serverActive.set(false)

        // Cancel coroutine scope
        coroutineScope.cancel()

        // Clear singleton
        instance = null

        updateStatus(ServerStatus.Stopped)
        Log.i(TAG, "McpServerService destroyed")

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateStatus(status: ServerStatus) {
        _serverStatus.value = status
        serverLogRepository.log(ServerLogEntry.Type.SERVER, serverStatusLogMessage(status, applicationContext))
    }

    private fun createNotification(): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        return NotificationCompat
            .Builder(this, McpApplication.MCP_SERVER_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_mcp_server_title))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "MCP:ServerService"
        const val ACTION_START = "com.fenix.mobilemcp.ACTION_START_MCP_SERVER"
        const val ACTION_STOP = "com.fenix.mobilemcp.ACTION_STOP_MCP_SERVER"
        const val NOTIFICATION_ID = 1001
        const val SHUTDOWN_GRACE_PERIOD_MS = 1000L
        const val SHUTDOWN_TIMEOUT_MS = 5000L
        const val TUNNEL_STOP_TIMEOUT_MS = 3_000L

        /**
         * Shared server status flow. Collected by MainViewModel to update the UI.
         * Uses a companion-level StateFlow so it survives service rebinding and is
         * accessible without requiring a bound service reference.
         */
        private val _serverStatus = MutableStateFlow<ServerStatus>(ServerStatus.Stopped)
        val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

        @Volatile
        var instance: McpServerService? = null
            private set
    }
}

/** Builds the HTTPS [HttpsMaterial] when both the keystore and its password are present, else null. */
private fun buildHttpsMaterial(
    keyStore: KeyStore?,
    keyStorePassword: CharArray?,
): HttpsMaterial? =
    if (keyStore != null && keyStorePassword != null) {
        HttpsMaterial(keyStore, keyStorePassword)
    } else {
        null
    }

/**
 * Recurso de log (y argumentos) para una transición de [ServerStatus]. El texto se resuelve al
 * momento de escribir el registro, con el idioma vigente de la app (ES/EN).
 * Excepción técnica: en Error, `status.message` viaja como argumento sin traducir.
 */
internal fun serverStatusLogMessageRes(status: ServerStatus): Pair<Int, Array<Any>> =
    when (status) {
        ServerStatus.Starting -> R.string.log_server_starting to emptyArray<Any>()
        is ServerStatus.Running -> R.string.log_server_started to arrayOf(status.bindingAddress, status.port)
        ServerStatus.Stopping -> R.string.log_server_stopping to emptyArray<Any>()
        ServerStatus.Stopped -> R.string.log_server_stopped to emptyArray<Any>()
        is ServerStatus.Error -> R.string.log_server_error to arrayOf(status.message)
    }

/** Human-readable server-log message for a [ServerStatus] transition, en el idioma vigente. */
internal fun serverStatusLogMessage(
    status: ServerStatus,
    context: Context,
): String = serverStatusLogMessageRes(status).let { (resId, args) -> context.getString(resId, *args) }

/**
 * Recurso de log (y argumentos) para el autochequeo de Modo Privacidad al iniciar el servidor.
 * Excepción técnica: en Unavailable, `status.reason` (motivo del fallo del modelo) viaja sin traducir.
 */
internal fun privacyStatusLogMessageRes(status: PrivacyModeStatus): Pair<Int, Array<Any>> =
    when (status) {
        is PrivacyModeStatus.Ready -> R.string.log_privacy_ready_model to emptyArray<Any>()
        is PrivacyModeStatus.ReadyDeterministicOnly -> R.string.log_privacy_ready_deterministic to emptyArray<Any>()
        is PrivacyModeStatus.Unavailable -> R.string.log_privacy_unavailable to arrayOf(status.reason)
        is PrivacyModeStatus.Disabled -> R.string.log_privacy_disabled to emptyArray<Any>()
    }

/** Server-log message for a Privacy Mode self-check result at server start, en el idioma vigente. */
internal fun privacyStatusLogMessage(
    status: PrivacyModeStatus,
    context: Context,
): String = privacyStatusLogMessageRes(status).let { (resId, args) -> context.getString(resId, *args) }

/** Server-log message for a tunnel status transition; null when not logged by the observer. */
internal fun tunnelStatusLogMessage(status: TunnelStatus): String? =
    when (status) {
        TunnelStatus.Connecting -> "Tunnel connecting…"
        is TunnelStatus.Connected -> "Tunnel connected: ${status.endpoints.joinToString { it.url }}"
        is TunnelStatus.Error -> "Tunnel error: ${status.message}"
        TunnelStatus.Disconnected -> null // Logged by TunnelManager.stop()
    }
