package com.fenix.mobilemcp.mcp.registry

import android.content.Context
import com.fenix.mobilemcp.data.model.ToolPermissionsConfig
import com.fenix.mobilemcp.data.repository.ServerLogRepository
import com.fenix.mobilemcp.delivery.monitor.DeliveryMonitor
import com.fenix.mobilemcp.delivery.scheduler.TurnMetricsStore
import com.fenix.mobilemcp.delivery.scheduler.TurnScheduler
import com.fenix.mobilemcp.delivery.zones.ZoneEntry
import com.fenix.mobilemcp.delivery.zones.ZoneIndexProvider
import com.fenix.mobilemcp.delivery.zones.ZoneMatcher
import com.fenix.mobilemcp.mcp.skills.QuickJsSkillEngine
import com.fenix.mobilemcp.mcp.tools.LoggedToolRegistrar
import com.fenix.mobilemcp.mcp.tools.registerFenixTools
import com.fenix.mobilemcp.memory.FenixMemoryDb

/**
 * Inicializador y contenedor compartido de herramientas locales Fénix.
 * Garantiza que F-Chat in-process y el servidor Ktor MCP compartan exactamente
 * las mismas instancias de FenixMemoryDb, ZoneMatcher, TurnScheduler y QuickJsSkillEngine.
 */
object FenixDefaultToolInitializer {
    @Volatile
    private var sharedMemDb: FenixMemoryDb? = null

    @Volatile
    private var sharedMatcher: ZoneMatcher? = null

    @Volatile
    private var sharedIndex: List<ZoneEntry>? = null

    @Volatile
    private var sharedScheduler: TurnScheduler? = null

    @Volatile
    private var sharedSkillEngine: QuickJsSkillEngine? = null

    @Synchronized
    private fun ensureSharedInstances(context: Context) {
        val appCtx = context.applicationContext
        if (sharedMemDb == null) {
            val db = FenixMemoryDb(appCtx)
            val matcher = ZoneMatcher()
            val index = ZoneIndexProvider.provideIndex()
            val monitor = DeliveryMonitor(appCtx, matcher, index)
            val metrics = TurnMetricsStore(db)
            val scheduler = TurnScheduler(appCtx, monitor, metrics)
            val skillEngine = QuickJsSkillEngine(appCtx, db)

            sharedMemDb = db
            sharedMatcher = matcher
            sharedIndex = index
            sharedScheduler = scheduler
            sharedSkillEngine = skillEngine
        }
    }

    fun initialize(context: Context, serverLog: ServerLogRepository) {
        ensureSharedInstances(context)
        val registrar =
            LoggedToolRegistrar(
                server = null,
                serverLog = serverLog,
            )
        registerSharedBundle(
            context = context.applicationContext,
            registrar = registrar,
            toolNamePrefix = "",
            perms = ToolPermissionsConfig(),
        )
    }

    fun registerSharedBundle(
        context: Context,
        registrar: LoggedToolRegistrar,
        toolNamePrefix: String,
        perms: ToolPermissionsConfig,
    ) {
        ensureSharedInstances(context)
        val db = requireNotNull(sharedMemDb)
        val matcher = requireNotNull(sharedMatcher)
        val index = requireNotNull(sharedIndex)
        val scheduler = requireNotNull(sharedScheduler)
        val skillEngine = requireNotNull(sharedSkillEngine)

        registerFenixTools(
            registrar = registrar,
            db = db,
            matcher = matcher,
            index = index,
            scheduler = scheduler,
            toolNamePrefix = toolNamePrefix,
            perms = perms,
            context = context.applicationContext,
        )

        skillEngine.initialize(
            registrar = registrar,
        )
    }
}

