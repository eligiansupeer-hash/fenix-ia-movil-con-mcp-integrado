@file:Suppress("TooManyFunctions")

package com.fenix.mobilemcp.mcp.tools

import com.fenix.mobilemcp.data.model.ToolPermissionsConfig
import com.fenix.mobilemcp.delivery.scheduler.TurnScheduler
import com.fenix.mobilemcp.delivery.zones.ZoneMatcher
import com.fenix.mobilemcp.delivery.zones.ZoneEntry
import com.fenix.mobilemcp.mcp.McpToolException
import com.fenix.mobilemcp.memory.FenixMemoryDb
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// fenix_memory_save
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Guarda un dato en la memoria persistente Fénix (SQLite local, con TTL opcional).
 *
 * **Input**: `{ "clave": "...", "ambito": "...", "valor": "...", "categoria": "general", "tags": "", "ttl_horas": 0 }`
 */
class FenixMemorySaveHandler
    @Inject
    constructor(
        private val db: FenixMemoryDb,
    ) {
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        suspend fun execute(arguments: JsonObject?): CallToolResult =
            try {
                val clave = McpToolUtils.requireString(arguments, "clave")
                val ambito = McpToolUtils.optionalString(arguments, "ambito", "")
                val valor = McpToolUtils.requireString(arguments, "valor")
                val categoria = McpToolUtils.optionalString(arguments, "categoria", "general")
                val tags = McpToolUtils.optionalString(arguments, "tags", "")
                val ttlHoras = McpToolUtils.optionalInt(arguments, "ttl_horas", 0)
                val expira =
                    if (ttlHoras > 0) {
                        java.time.Instant.now().plusSeconds(ttlHoras * 3600L).toString()
                    } else {
                        null
                    }
                db.kvGuardar(clave, ambito, valor, categoria, tags, expira)
                McpToolUtils.untrustedTextResult("{\"guardado\":true,\"clave\":\"$clave\"}")
            } catch (e: McpToolException) {
                throw e
            } catch (e: Exception) {
                throw McpToolException.ActionFailed("Failed to save memory: ${e.message ?: "Unknown error"}")
            }

        fun register(
            registrar: LoggedToolRegistrar,
            toolNamePrefix: String,
        ) {
            registrar.addTool(
                toolName = TOOL_NAME,
                name = "$toolNamePrefix$TOOL_NAME",
                description =
                    "Guarda un dato persistente en la memoria Fénix del teléfono (SQLite local). " +
                        "Usar ttl_horas para auto-expirar.",
                inputSchema =
                    ToolSchema(
                        properties =
                            buildJsonObject {
                                put("clave", buildJsonObject { put("type", "string") })
                                put("ambito", buildJsonObject { put("type", "string") })
                                put("valor", buildJsonObject { put("type", "string") })
                                put("categoria", buildJsonObject { put("type", "string") })
                                put("tags", buildJsonObject { put("type", "string") })
                                put("ttl_horas", buildJsonObject { put("type", "integer") })
                            },
                        required = listOf("clave", "valor"),
                    ),
            ) { request -> execute(request.arguments) }
        }

        companion object {
            const val TOOL_NAME = "memory_save"
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// fenix_memory_read
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Lee un dato de la memoria Fénix (respeta TTL).
 *
 * **Input**: `{ "clave": "...", "ambito": "" }`
 */
class FenixMemoryReadHandler
    @Inject
    constructor(
        private val db: FenixMemoryDb,
    ) {
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        suspend fun execute(arguments: JsonObject?): CallToolResult =
            try {
                val clave = McpToolUtils.requireString(arguments, "clave")
                val ambito = McpToolUtils.optionalString(arguments, "ambito", "")
                val valor = db.kvLeer(clave, ambito)
                val out =
                    buildJsonObject {
                        put("clave", clave)
                        if (valor != null) put("valor", valor) else put("valor", "")
                        put("hallado", valor != null)
                    }
                McpToolUtils.untrustedTextResult(Json.encodeToString(out))
            } catch (e: McpToolException) {
                throw e
            } catch (e: Exception) {
                throw McpToolException.ActionFailed("Failed to read memory: ${e.message ?: "Unknown error"}")
            }

        fun register(
            registrar: LoggedToolRegistrar,
            toolNamePrefix: String,
        ) {
            registrar.addTool(
                toolName = TOOL_NAME,
                name = "$toolNamePrefix$TOOL_NAME",
                description = "Lee un dato de la memoria Fénix del teléfono. Respeta expiración.",
                inputSchema =
                    ToolSchema(
                        properties =
                            buildJsonObject {
                                put("clave", buildJsonObject { put("type", "string") })
                                put("ambito", buildJsonObject { put("type", "string") })
                            },
                        required = listOf("clave"),
                    ),
            ) { request -> execute(request.arguments) }
        }

        companion object {
            const val TOOL_NAME = "memory_read"
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// fenix_zone_check
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Cruza una dirección contra las 41 zonas peligrosas de San Juan (<15 ms).
 *
 * **Input**: `{ "direccion": "Vidart y Ortega" }`
 */
class FenixZoneCheckHandler
    @Inject
    constructor(
        private val matcher: ZoneMatcher,
        private val index: List<ZoneEntry>,
    ) {
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        suspend fun execute(arguments: JsonObject?): CallToolResult =
            try {
                val direccion = McpToolUtils.requireString(arguments, "direccion")
                val hits = matcher.match(direccion, index)
                val out =
                    buildJsonObject {
                        put("peligrosa", hits.isNotEmpty())
                        put(
                            "hits",
                            buildJsonArray {
                                for (h in hits) {
                                    add(
                                        buildJsonObject {
                                            put("zone_id", h.zoneId)
                                            put("departamento", h.department)
                                            put("nombre", h.name)
                                            put("extrema_noche", h.nightExtreme)
                                        },
                                    )
                                }
                            },
                        )
                    }
                McpToolUtils.untrustedTextResult(Json.encodeToString(out))
            } catch (e: McpToolException) {
                throw e
            } catch (e: Exception) {
                throw McpToolException.ActionFailed("Failed zone check: ${e.message ?: "Unknown error"}")
            }

        fun register(
            registrar: LoggedToolRegistrar,
            toolNamePrefix: String,
        ) {
            registrar.addTool(
                toolName = TOOL_NAME,
                name = "$toolNamePrefix$TOOL_NAME",
                description =
                    "Cruza una dirección contra las zonas peligrosas de San Juan. " +
                        "Devuelve hits con departamento y si es extrema de noche.",
                inputSchema =
                    ToolSchema(
                        properties =
                            buildJsonObject {
                                put("direccion", buildJsonObject { put("type", "string") })
                            },
                        required = listOf("direccion"),
                    ),
            ) { request -> execute(request.arguments) }
        }

        companion object {
            const val TOOL_NAME = "zone_check"
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// fenix_turn_watch
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Programa vigilancia de turnos (modo radar: alerta, dedo humano).
 *
 * **Input**: `{ "desde_hora": "06:00", "hasta_hora": "08:00" }` (informativo; la cadencia la fija el scheduler)
 */
class FenixTurnWatchHandler
    @Inject
    constructor(
        private val scheduler: TurnScheduler,
    ) {
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        suspend fun execute(arguments: JsonObject?): CallToolResult =
            try {
                scheduler.start()
                val out =
                    buildJsonObject {
                        put("vigilando", true)
                        put("modo", scheduler.getMode().name)
                    }
                McpToolUtils.untrustedTextResult(Json.encodeToString(out))
            } catch (e: McpToolException) {
                throw e
            } catch (e: Exception) {
                throw McpToolException.ActionFailed("Failed to watch turns: ${e.message ?: "Unknown error"}")
            }

        fun register(
            registrar: LoggedToolRegistrar,
            toolNamePrefix: String,
        ) {
            registrar.addTool(
                toolName = TOOL_NAME,
                name = "$toolNamePrefix$TOOL_NAME",
                description =
                    "Activa la vigilancia de turnos de delivery (modo radar por defecto: " +
                        "alerta y el humano toca).",
                inputSchema =
                    ToolSchema(
                        properties =
                            buildJsonObject {
                                put("desde_hora", buildJsonObject { put("type", "string") })
                                put("hasta_hora", buildJsonObject { put("type", "string") })
                            },
                        required = listOf(),
                    ),
            ) { request -> execute(request.arguments) }
        }

        companion object {
            const val TOOL_NAME = "turn_watch"
        }
    }

/** Registro del bundle Fénix (memoria + zonas + turnos), con permission-gate por tool. */
fun registerFenixTools(
    registrar: LoggedToolRegistrar,
    db: FenixMemoryDb,
    matcher: ZoneMatcher,
    index: List<ZoneEntry>,
    scheduler: TurnScheduler,
    toolNamePrefix: String,
    perms: ToolPermissionsConfig,
) {
    if (perms.isToolEnabled(FenixMemorySaveHandler.TOOL_NAME)) {
        FenixMemorySaveHandler(db).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(FenixMemoryReadHandler.TOOL_NAME)) {
        FenixMemoryReadHandler(db).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(FenixZoneCheckHandler.TOOL_NAME)) {
        FenixZoneCheckHandler(matcher, index).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(FenixTurnWatchHandler.TOOL_NAME)) {
        FenixTurnWatchHandler(scheduler).register(registrar, toolNamePrefix)
    }
}
