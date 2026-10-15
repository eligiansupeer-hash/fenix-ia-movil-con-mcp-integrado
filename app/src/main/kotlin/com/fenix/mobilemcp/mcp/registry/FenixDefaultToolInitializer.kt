package com.fenix.mobilemcp.mcp.registry

import android.content.Context
import com.fenix.mobilemcp.data.repository.ServerLogRepository
import com.fenix.mobilemcp.delivery.monitor.DeliveryMonitor
import com.fenix.mobilemcp.delivery.scheduler.TurnMetricsStore
import com.fenix.mobilemcp.delivery.scheduler.TurnScheduler
import com.fenix.mobilemcp.delivery.zones.ZoneIndexProvider
import com.fenix.mobilemcp.delivery.zones.ZoneMatcher
import com.fenix.mobilemcp.mcp.tools.LoggedToolRegistrar
import com.fenix.mobilemcp.mcp.tools.registerFenixTools
import com.fenix.mobilemcp.memory.FenixMemoryDb

/**
 * Inicializador automático de herramientas locales Fénix en memoria.
 * Permite que F-Chat pueda interactuar inmediatamente con memoria persistente,
 * zonas de delivery y métricas de turnos sin requerir que el servidor HTTP esté corriendo.
 */
object FenixDefaultToolInitializer {
    fun initialize(context: Context, serverLog: ServerLogRepository) {
        val memDb = FenixMemoryDb(context)
        val matcher = ZoneMatcher()
        val index = ZoneIndexProvider.provideIndex()
        val monitor = DeliveryMonitor(context, matcher, index)
        val metrics = TurnMetricsStore(memDb)
        val scheduler = TurnScheduler(context, monitor, metrics)
        val registrar =
            LoggedToolRegistrar(
                server = null,
                serverLog = serverLog,
            )
        registerFenixTools(
            registrar = registrar,
            db = memDb,
            matcher = matcher,
            index = index,
            scheduler = scheduler,
            toolNamePrefix = "",
            perms = com.fenix.mobilemcp.data.model.ToolPermissionsConfig(),
        )

        val skillEngine = com.fenix.mobilemcp.mcp.skills.QuickJsSkillEngine(context, memDb)
        skillEngine.initialize()
    }
}

