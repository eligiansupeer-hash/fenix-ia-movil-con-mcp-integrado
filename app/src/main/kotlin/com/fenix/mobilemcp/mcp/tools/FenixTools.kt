@file:Suppress("TooManyFunctions")

package com.fenix.mobilemcp.mcp.tools

import com.fenix.mobilemcp.data.model.ToolPermissionsConfig
import com.fenix.mobilemcp.delivery.scheduler.TurnScheduler
import com.fenix.mobilemcp.delivery.zones.ZoneMatcher
import com.fenix.mobilemcp.delivery.zones.ZoneEntry
import com.fenix.mobilemcp.mcp.McpToolException
import com.fenix.mobilemcp.mcp.oauth.OAuthPolicy
import com.fenix.mobilemcp.memory.FenixMemoryDb
import com.fenix.mobilemcp.services.accessibility.McpAccessibilityService
import com.fenix.mobilemcp.services.storage.FenixFileManager
import com.fenix.mobilemcp.telemetry.FenixBlackBoxRecorder
import com.fenix.mobilemcp.utils.PermissionUtils
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
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

/**
 * Busca datos en la memoria persistente Fénix mediante coincidencia por texto.
 */
class FenixMemorySearchHandler
    @Inject
    constructor(
        private val db: FenixMemoryDb,
    ) {
        suspend fun execute(arguments: JsonObject?): CallToolResult =
            try {
                val query = McpToolUtils.requireString(arguments, "query")
                val limit = McpToolUtils.optionalInt(arguments, "limit", 10)
                val results = db.kvBuscar(query, limit)
                val arr = buildJsonArray {
                    results.forEach { (clave, ambito, valor) ->
                        add(
                            buildJsonObject {
                                put("clave", clave)
                                put("ambito", ambito)
                                put("valor", valor)
                            },
                        )
                    }
                }
                val out = buildJsonObject {
                    put("status", "ok")
                    put("query", query)
                    put("total", results.size)
                    put("resultados", arr)
                }
                McpToolUtils.untrustedTextResult(Json.encodeToString(out))
            } catch (e: McpToolException) {
                throw e
            } catch (e: Exception) {
                throw McpToolException.ActionFailed("Error al buscar en memoria: ${e.message}")
            }

        fun register(registrar: LoggedToolRegistrar, toolNamePrefix: String) {
            registrar.addTool(
                toolName = TOOL_NAME,
                name = "$toolNamePrefix$TOOL_NAME",
                description = "Busca información o recuerdos en la memoria persistente SQLite mediante palabras clave.",
                inputSchema = ToolSchema(
                    properties = buildJsonObject {
                        put("query", buildJsonObject {
                            put("type", "string")
                            put("description", "Texto o término a buscar en la memoria persistente")
                        })
                        put("limit", buildJsonObject {
                            put("type", "integer")
                            put("description", "Límite máximo de resultados (por defecto 10)")
                        })
                    },
                    required = listOf("query"),
                ),
            ) { request -> execute(request.arguments) }
        }

        companion object {
            const val TOOL_NAME = "memory_search"
        }
    }

/**
 * Lista las claves de memoria existentes por ámbito.
 */
class FenixMemoryListHandler
    @Inject
    constructor(
        private val db: FenixMemoryDb,
    ) {
        suspend fun execute(arguments: JsonObject?): CallToolResult =
            try {
                val ambito = McpToolUtils.optionalString(arguments, "ambito", "")
                val limit = McpToolUtils.optionalInt(arguments, "limit", 50)
                val results = db.kvListar(ambito.ifEmpty { null }, limit)
                val arr = buildJsonArray {
                    results.forEach { (clave, amb, cat) ->
                        add(
                            buildJsonObject {
                                put("clave", clave)
                                put("ambito", amb)
                                put("categoria", cat)
                            },
                        )
                    }
                }
                val out = buildJsonObject {
                    put("status", "ok")
                    put("total", results.size)
                    put("claves", arr)
                }
                McpToolUtils.untrustedTextResult(Json.encodeToString(out))
            } catch (e: McpToolException) {
                throw e
            } catch (e: Exception) {
                throw McpToolException.ActionFailed("Error al listar memoria: ${e.message}")
            }

        fun register(registrar: LoggedToolRegistrar, toolNamePrefix: String) {
            registrar.addTool(
                toolName = TOOL_NAME,
                name = "$toolNamePrefix$TOOL_NAME",
                description = "Lista las claves almacenadas en la memoria persistente SQLite del teléfono.",
                inputSchema = ToolSchema(
                    properties = buildJsonObject {
                        put("ambito", buildJsonObject {
                            put("type", "string")
                            put("description", "Ámbito o categoría opcional de filtrado")
                        })
                        put("limit", buildJsonObject {
                            put("type", "integer")
                            put("description", "Cantidad máxima de claves a retornar")
                        })
                    },
                    required = listOf(),
                ),
            ) { request -> execute(request.arguments) }
        }

        companion object {
            const val TOOL_NAME = "memory_list"
        }
    }

/**
 * Administra sesiones de trabajo y conversaciones persistentes en SQLite.
 */
class FenixSessionManageHandler
    @Inject
    constructor(
        private val db: FenixMemoryDb,
    ) {
        suspend fun execute(arguments: JsonObject?): CallToolResult =
            try {
                val action = McpToolUtils.optionalString(arguments, "action", "list").lowercase()
                val sessionId = McpToolUtils.optionalString(arguments, "session_id", "")
                val title = McpToolUtils.optionalString(arguments, "title", "Nueva sesión")

                val out = when (action) {
                    "create" -> {
                        val id = if (sessionId.isNotBlank()) sessionId else java.util.UUID.randomUUID().toString()
                        val record = db.crearSesion(id, title)
                        buildJsonObject {
                            put("status", "created")
                            put("session_id", record.id)
                            put("titulo", record.titulo)
                            put("creado", record.creado)
                        }
                    }
                    "list" -> {
                        val sessions = db.listarSesiones()
                        val arr = buildJsonArray {
                            sessions.forEach { s ->
                                add(
                                    buildJsonObject {
                                        put("id", s.id)
                                        put("titulo", s.titulo)
                                        put("actualizado", s.actualizado)
                                        put("estado", s.estado)
                                    },
                                )
                            }
                        }
                        buildJsonObject {
                            put("status", "ok")
                            put("total_sesiones", sessions.size)
                            put("sesiones", arr)
                        }
                    }
                    "context" -> {
                        if (sessionId.isBlank()) throw McpToolException.InvalidParams("Se requiere session_id para consultar contexto")
                        val entries = db.listarEntradas(sessionId)
                        val arr = buildJsonArray {
                            entries.forEach { e ->
                                add(
                                    buildJsonObject {
                                        put("id", e.id)
                                        put("tipo", e.tipo)
                                        put("evento", e.evento)
                                        put("detalle", e.detalle)
                                        put("ts", e.ts)
                                    },
                                )
                            }
                        }
                        buildJsonObject {
                            put("status", "ok")
                            put("session_id", sessionId)
                            put("total_entradas", entries.size)
                            put("entradas", arr)
                        }
                    }
                    "delete" -> {
                        if (sessionId.isBlank()) throw McpToolException.InvalidParams("Se requiere session_id para eliminar sesión")
                        val deleted = db.eliminarSesion(sessionId)
                        buildJsonObject {
                            put("status", if (deleted) "deleted" else "not_found")
                            put("session_id", sessionId)
                        }
                    }
                    else -> throw McpToolException.InvalidParams("Acción inválida: $action (use create, list, context, delete)")
                }

                McpToolUtils.untrustedTextResult(Json.encodeToString(out))
            } catch (e: McpToolException) {
                throw e
            } catch (e: Exception) {
                throw McpToolException.ActionFailed("Error en gestión de sesiones: ${e.message}")
            }

        fun register(registrar: LoggedToolRegistrar, toolNamePrefix: String) {
            registrar.addTool(
                toolName = TOOL_NAME,
                name = "$toolNamePrefix$TOOL_NAME",
                description = "Gestiona sesiones de conversación persistentes en SQLite. Permite crear, listar, consultar contexto o eliminar sesiones.",
                inputSchema = ToolSchema(
                    properties = buildJsonObject {
                        put("action", buildJsonObject {
                            put("type", "string")
                            put("enum", JsonArray(listOf(JsonPrimitive("create"), JsonPrimitive("list"), JsonPrimitive("context"), JsonPrimitive("delete"))))
                        })
                        put("session_id", buildJsonObject { put("type", "string") })
                        put("title", buildJsonObject { put("type", "string") })
                    },
                    required = listOf(),
                ),
            ) { request -> execute(request.arguments) }
        }

        companion object {
            const val TOOL_NAME = "session_manage"
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
                if (matcher.isRawCoordinates(direccion)) {
                    throw McpToolException.InvalidParams(
                        "Coordenadas geográficas crudas ('$direccion') no soportadas en F1.1; ingrese una dirección textual o intersección.",
                    )
                }
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
 * **Input**: `{ "desde_hora": "06:00", "hasta_hora": "08:00" }`
 */
class FenixTurnWatchHandler
    @Inject
    constructor(
        private val scheduler: TurnScheduler,
    ) {
        private val timeRegex = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")

        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        suspend fun execute(arguments: JsonObject?): CallToolResult =
            try {
                val desdeHora = McpToolUtils.optionalString(arguments, "desde_hora", "").trim()
                val hastaHora = McpToolUtils.optionalString(arguments, "hasta_hora", "").trim()
                if (desdeHora.isNotEmpty() && !timeRegex.matches(desdeHora)) {
                    throw McpToolException.InvalidParams("Formato 'desde_hora' inválido ('$desdeHora'); use HH:MM (00:00..23:59)")
                }
                if (hastaHora.isNotEmpty() && !timeRegex.matches(hastaHora)) {
                    throw McpToolException.InvalidParams("Formato 'hasta_hora' inválido ('$hastaHora'); use HH:MM (00:00..23:59)")
                }
                scheduler.start(
                    desdeHora = desdeHora.ifEmpty { null },
                    hastaHora = hastaHora.ifEmpty { null },
                )
                val out =
                    buildJsonObject {
                        put("vigilando", scheduler.isWatching())
                        put("modo", scheduler.getMode().name)
                        put("desde_hora", scheduler.getDesdeHora())
                        put("hasta_hora", scheduler.getHastaHora())
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
                        "alerta y el humano toca) registrando la ventana horaria desde_hora/hasta_hora.",
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

// ─────────────────────────────────────────────────────────────────────────────
// Conectores Oficiales F5 (S2-01..S2-09) + Seguridad/Skills/Cooldown (S1/S3/S8/S9)
// ─────────────────────────────────────────────────────────────────────────────

class FenixCloudflareHandler {
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val localMcp = McpToolUtils.optionalString(arguments, "local_mcp", "https://127.0.0.1:8080/mcp")
            val tunnelMcp = McpToolUtils.optionalString(arguments, "tunnel_mcp", "https://fenix.axiom.local/mcp")
            val online = McpToolUtils.optionalBoolean(arguments, "online", true)
            val apiToken = McpToolUtils.optionalString(arguments, "api_token", "").trim()
            val routes = com.fenix.mobilemcp.connectors.backend.CloudflareClient.routeOrder(localMcp, tunnelMcp, online)
            val verifyPayload = if (apiToken.isNotEmpty()) {
                com.fenix.mobilemcp.connectors.backend.CloudflareClient.verifyToken(apiToken)
            } else {
                null
            }
            val out = buildJsonObject {
                put("online", online)
                put("header_client_id", com.fenix.mobilemcp.connectors.backend.CloudflareClient.HEADER_CLIENT_ID)
                put("header_client_secret", com.fenix.mobilemcp.connectors.backend.CloudflareClient.HEADER_CLIENT_SECRET)
                put(
                    "routes",
                    buildJsonArray {
                        routes.forEach { r -> add(kotlinx.serialization.json.JsonPrimitive(r)) }
                    },
                )
                if (verifyPayload != null) {
                    put("token_verification", verifyPayload)
                }
            }
            McpToolUtils.untrustedTextResult(Json.encodeToString(out))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Cloudflare connector error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Calcula el orden de rutas Cloudflare Zero Trust (loopback -> túnel) y verifica API Token opcional.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("local_mcp", buildJsonObject { put("type", "string") })
                    put("tunnel_mcp", buildJsonObject { put("type", "string") })
                    put("online", buildJsonObject { put("type", "boolean") })
                    put("api_token", buildJsonObject { put("type", "string") })
                },
                required = listOf(),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "cloudflare_route_check"
    }
}

class FenixSupabaseHandler(
    private val supabase: com.fenix.mobilemcp.connectors.backend.SupabaseLite,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val action = McpToolUtils.optionalString(arguments, "action", "select").lowercase()
            val baseUrl = McpToolUtils.requireString(arguments, "base_url")
            val anonKey = McpToolUtils.requireString(arguments, "anon_key")
            val table = McpToolUtils.optionalString(arguments, "table", "notification_outbox")
            val resultText = when (action) {
                "select" -> {
                    val columns = McpToolUtils.optionalString(arguments, "columns", "id")
                    val limit = McpToolUtils.optionalInt(arguments, "limit", 10)
                    supabase.select(baseUrl, anonKey, table, columns, limit)
                }
                "insert" -> {
                    val rowJson = McpToolUtils.requireString(arguments, "row_json")
                    supabase.insert(baseUrl, anonKey, table, rowJson)
                }
                "delete" -> {
                    val col = McpToolUtils.requireString(arguments, "filter_column")
                    val value = McpToolUtils.requireString(arguments, "filter_value")
                    supabase.deleteEquals(baseUrl, anonKey, table, col, value)
                }
                "heartbeat" -> {
                    val ok = supabase.heartbeat(baseUrl, anonKey)
                    if (!ok) throw IllegalStateException("Supabase heartbeat failed")
                    "{\"heartbeat\":true}"
                }
                else -> throw McpToolException.InvalidParams("Acción Supabase inválida: $action (use select, insert, delete, heartbeat)")
            }
            McpToolUtils.untrustedTextResult(resultText)
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Supabase error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Ejecuta operaciones PostgREST livianas contra Supabase (select, insert, delete, heartbeat) con protección anti-SSRF.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("action", buildJsonObject { put("type", "string") })
                    put("base_url", buildJsonObject { put("type", "string") })
                    put("anon_key", buildJsonObject { put("type", "string") })
                    put("table", buildJsonObject { put("type", "string") })
                    put("columns", buildJsonObject { put("type", "string") })
                    put("limit", buildJsonObject { put("type", "integer") })
                    put("row_json", buildJsonObject { put("type", "string") })
                    put("filter_column", buildJsonObject { put("type", "string") })
                    put("filter_value", buildJsonObject { put("type", "string") })
                },
                required = listOf("base_url", "anon_key"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "supabase_query"
    }
}

class FenixEntregasHandler(
    private val matcher: ZoneMatcher,
    private val index: List<ZoneEntry>,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val direccion = McpToolUtils.requireString(arguments, "direccion")
            val monto = McpToolUtils.optionalFloat(arguments, "monto", 0f).toDouble()
            val kind = McpToolUtils.optionalString(
                arguments,
                "kind",
                com.fenix.mobilemcp.connectors.entregas.EntregasFirstParty.KIND_POOL_ORDER,
            )
            val resultObj = com.fenix.mobilemcp.connectors.entregas.EntregasFirstParty.evaluateOrder(
                address = direccion,
                amount = monto,
                kind = kind,
                matcher = matcher,
                zones = index,
            )
            McpToolUtils.untrustedTextResult(Json.encodeToString(resultObj))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("EntregasFirstParty error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Evalúa una orden de Fénix Entregas (pool_order / contract_request) cruzando su dirección contra las zonas de riesgo de San Juan.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("direccion", buildJsonObject { put("type", "string") })
                    put("monto", buildJsonObject { put("type", "number") })
                    put("kind", buildJsonObject { put("type", "string") })
                },
                required = listOf("direccion"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "entregas_order_check"
    }
}

class FenixGitHubHandler {
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val action = McpToolUtils.optionalString(arguments, "action", "repo").lowercase()
            val token = McpToolUtils.optionalString(arguments, "token", "").trim().ifEmpty { null }
            val resultText = when (action) {
                "user" -> {
                    val reqToken = token ?: throw McpToolException.InvalidParams("Se requiere 'token' para action='user'")
                    com.fenix.mobilemcp.connectors.github.GitHubConnector.getAuthenticatedUser(reqToken)
                }
                "repo" -> {
                    val owner = McpToolUtils.requireString(arguments, "owner")
                    val repo = McpToolUtils.requireString(arguments, "repo")
                    com.fenix.mobilemcp.connectors.github.GitHubConnector.getRepository(owner, repo, token)
                }
                "issues" -> {
                    val owner = McpToolUtils.requireString(arguments, "owner")
                    val repo = McpToolUtils.requireString(arguments, "repo")
                    val state = McpToolUtils.optionalString(arguments, "state", "open")
                    val perPage = McpToolUtils.optionalInt(arguments, "per_page", 10)
                    com.fenix.mobilemcp.connectors.github.GitHubConnector.listIssues(owner, repo, state, perPage, token)
                }
                else -> throw McpToolException.InvalidParams("Acción GitHub inválida: $action (use user, repo, issues)")
            }
            McpToolUtils.untrustedTextResult(resultText)
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("GitHub connector error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Consulta la API REST oficial de GitHub v2022-11-28 (user, repo, issues).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("action", buildJsonObject { put("type", "string") })
                    put("token", buildJsonObject { put("type", "string") })
                    put("owner", buildJsonObject { put("type", "string") })
                    put("repo", buildJsonObject { put("type", "string") })
                    put("state", buildJsonObject { put("type", "string") })
                    put("per_page", buildJsonObject { put("type", "integer") })
                },
                required = listOf(),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "github_api"
    }
}

class FenixGmailHandler(
    private val gmailClient: com.fenix.mobilemcp.connectors.google.GmailClient,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val action = McpToolUtils.optionalString(arguments, "action", "search").lowercase()
            val bearer = McpToolUtils.requireString(arguments, "bearer")
            val out = when (action) {
                "search" -> {
                    val q = McpToolUtils.optionalString(arguments, "query", "in:inbox")
                    val max = McpToolUtils.optionalInt(arguments, "max_results", 10)
                    gmailClient.searchAll(bearer, q, max)
                }
                "history" -> {
                    val historyId = McpToolUtils.requireString(arguments, "last_history_id")
                    val newHistoryId = gmailClient.historyList(bearer, historyId)
                    "{\"historyId\":\"$newHistoryId\"}"
                }
                else -> throw McpToolException.InvalidParams("Acción Gmail inválida: $action (use search, history)")
            }
            McpToolUtils.untrustedTextResult(out)
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Gmail connector error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Consulta mensajes o historial incremental en Gmail API v1 con Bearer OAuth.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("action", buildJsonObject { put("type", "string") })
                    put("bearer", buildJsonObject { put("type", "string") })
                    put("query", buildJsonObject { put("type", "string") })
                    put("last_history_id", buildJsonObject { put("type", "string") })
                    put("max_results", buildJsonObject { put("type", "integer") })
                },
                required = listOf("bearer"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "gmail_query"
    }
}

class FenixDriveHandler(
    private val driveClient: com.fenix.mobilemcp.connectors.google.DriveClient,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val action = McpToolUtils.optionalString(arguments, "action", "list").lowercase()
            val bearer = McpToolUtils.requireString(arguments, "bearer")
            val out = when (action) {
                "list" -> {
                    val q = McpToolUtils.optionalString(arguments, "query", "").ifEmpty { null }
                    val pageSize = McpToolUtils.optionalInt(arguments, "page_size", 20)
                    val files = driveClient.list(bearer, q, pageSize)
                    Json.encodeToString(files)
                }
                "get" -> {
                    val fileId = McpToolUtils.requireString(arguments, "file_id")
                    val entry = driveClient.get(bearer, fileId)
                    Json.encodeToString(entry)
                }
                else -> throw McpToolException.InvalidParams("Acción Drive inválida: $action (use list, get)")
            }
            McpToolUtils.untrustedTextResult(out)
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Drive connector error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Lista o recupera metadatos mínimos de archivos en Google Drive API v3.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("action", buildJsonObject { put("type", "string") })
                    put("bearer", buildJsonObject { put("type", "string") })
                    put("query", buildJsonObject { put("type", "string") })
                    put("file_id", buildJsonObject { put("type", "string") })
                    put("page_size", buildJsonObject { put("type", "integer") })
                },
                required = listOf("bearer"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "drive_files"
    }
}

class FenixGoogleOAuthHandler {
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val action = McpToolUtils.optionalString(arguments, "action", "build_auth_url").lowercase()
            val out = when (action) {
                "validate_url" -> {
                    val url = McpToolUtils.requireString(arguments, "url")
                    val allowed = com.fenix.mobilemcp.connectors.google.GoogleOAuth.isAllowedUrl(url)
                    if (!allowed) {
                        throw IllegalArgumentException("SSRF_OAUTH_BLOCKED: URL '$url' fuera del ALLOWLIST de GoogleOAuth")
                    }
                    buildJsonObject {
                        put("url", url)
                        put("allowed", true)
                    }
                }
                "build_auth_url" -> {
                    val clientId = McpToolUtils.optionalString(arguments, "client_id", "fenix-mobile-client")
                    val verifier = McpToolUtils.optionalString(arguments, "code_verifier", "").ifEmpty {
                        com.fenix.mobilemcp.connectors.google.GoogleOAuth.generateCodeVerifier()
                    }
                    val challenge = com.fenix.mobilemcp.connectors.google.GoogleOAuth.computeCodeChallengeS256(verifier)
                    val authUrl = com.fenix.mobilemcp.connectors.google.GoogleOAuth.buildAuthorizationUrl(
                        clientId = clientId,
                        codeVerifier = verifier,
                    )
                    buildJsonObject {
                        put("callback_url", com.fenix.mobilemcp.connectors.google.GoogleOAuth.CALLBACK_URL)
                        put("code_verifier", verifier)
                        put("code_challenge_s256", challenge)
                        put("auth_url", authUrl)
                    }
                }
                else -> throw McpToolException.InvalidParams("Acción GoogleOAuth inválida: $action (use validate_url, build_auth_url)")
            }
            McpToolUtils.untrustedTextResult(Json.encodeToString(out))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("GoogleOAuth error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Genera parámetros OAuth2 PKCE S256 para Google y valida dominios contra el ALLOWLIST anti-SSRF.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("action", buildJsonObject { put("type", "string") })
                    put("url", buildJsonObject { put("type", "string") })
                    put("client_id", buildJsonObject { put("type", "string") })
                    put("code_verifier", buildJsonObject { put("type", "string") })
                },
                required = listOf(),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "google_oauth_pkce"
    }
}

class FenixSocialReadOnlyHandler {
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val reset = McpToolUtils.optionalBoolean(arguments, "reset_quota", false)
            if (reset) {
                com.fenix.mobilemcp.connectors.social.SocialReadOnly.resetQuotaForTesting()
            }
            val pkg = McpToolUtils.optionalString(
                arguments,
                "package_name",
                com.fenix.mobilemcp.connectors.social.SocialReadOnly.IG_PACKAGE,
            )
            val actionType = McpToolUtils.requireString(arguments, "action_type")
            val count = McpToolUtils.optionalInt(arguments, "visit_count", 1)
            val resultObj = com.fenix.mobilemcp.connectors.social.SocialReadOnly.evaluateSocialAction(
                packageName = pkg,
                actionType = actionType,
                simulateVisitCount = count,
            )
            McpToolUtils.untrustedTextResult(Json.encodeToString(resultObj))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("SocialReadOnly guard rejected action: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Valida acciones de solo-lectura y límite de 40 visitas/hora en Instagram/Facebook (Fase 1).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("package_name", buildJsonObject { put("type", "string") })
                    put("action_type", buildJsonObject { put("type", "string") })
                    put("visit_count", buildJsonObject { put("type", "integer") })
                    put("reset_quota", buildJsonObject { put("type", "boolean") })
                },
                required = listOf("action_type"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "social_readonly_guard"
    }
}

class FenixWhatsAppHybridHandler {
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val reset = McpToolUtils.optionalBoolean(arguments, "reset_quota", false)
            if (reset) {
                com.fenix.mobilemcp.connectors.whatsapp.WhatsAppHybrid.resetQuotaForTesting()
            }
            val phone = McpToolUtils.requireString(arguments, "phone")
            val message = McpToolUtils.requireString(arguments, "message")
            val count = McpToolUtils.optionalInt(arguments, "simulate_count", 1)
            val resultObj = com.fenix.mobilemcp.connectors.whatsapp.WhatsAppHybrid.evaluateProactiveSend(
                phone = phone,
                message = message,
                simulateCount = count,
            )
            McpToolUtils.untrustedTextResult(Json.encodeToString(resultObj))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("WhatsAppHybrid guard error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Construye URI whatsapp://send validando formato E.164 y aplica el tope anti-baneo de 10 envíos proactivos por hora.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("phone", buildJsonObject { put("type", "string") })
                    put("message", buildJsonObject { put("type", "string") })
                    put("simulate_count", buildJsonObject { put("type", "integer") })
                    put("reset_quota", buildJsonObject { put("type", "boolean") })
                },
                required = listOf("phone", "message"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "whatsapp_hybrid_guard"
    }
}

class FenixStreetImmunityHandler(
    private val checker: com.fenix.mobilemcp.security.StreetImmunityChecker,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val checkPkg = McpToolUtils.optionalString(arguments, "check_package", "").trim()
            if (checkPkg.isNotEmpty()) {
                val uiAction = com.fenix.mobilemcp.policy.UiAction(pkg = checkPkg, actionType = "checkImmunity")
                val policyGate = com.fenix.mobilemcp.policy.PolicyGate(com.fenix.mobilemcp.McpApplication.instance)
                val decision = policyGate.evaluate(uiAction)
                if (decision is com.fenix.mobilemcp.policy.PolicyDecision.Deny) {
                    throw IllegalStateException("POLICY_BLOCKED: Interacción con '$checkPkg' bloqueada por PolicyGate (Razón: ${decision.reasonCode})")
                }
            }
            val report = checker.auditStreetImmunity()
            val out = buildJsonObject {
                put("isDevelopmentSettingsDisabled", report.isDevelopmentSettingsDisabled)
                put("isAdbDisabled", report.isAdbDisabled)
                put("areWorkshopSocketsClosed", report.areWorkshopSocketsClosed)
                put("isAccessibilityFirewallActive", report.isAccessibilityFirewallActive)
                put("isFullyImmune", report.isFullyImmune)
                put(
                    "details",
                    buildJsonObject {
                        report.details.forEach { (k, v) -> put(k, v) }
                    },
                )
            }
            McpToolUtils.untrustedTextResult(Json.encodeToString(out))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("StreetImmunity error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Ejecuta la auditoría de inmunidad de Modo Calle (ADB, Opciones de Desarrollador, puertos 8022/8080/8081/9222 y cortafuegos anti-ban).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("check_package", buildJsonObject { put("type", "string") })
                },
                required = listOf(),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "street_immunity_check"
    }
}

class FenixPrivacySanitizeHandler(
    private val sanitizer: com.fenix.mobilemcp.privacy.LocalNerSanitizer,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val text = McpToolUtils.requireString(arguments, "texto")
            val sanitized = sanitizer.sanitize(text)
            val out = buildJsonObject {
                put("sanitized", sanitized)
                put("modified", sanitized != text)
            }
            McpToolUtils.untrustedTextResult(Json.encodeToString(out))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Sanitizer error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Sanitiza texto local con LocalNerSanitizer ocultando API keys, JWTs, CBU, CUIT, DNI, tarjetas Luhn, IMEI, MAC, emails y seriales.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("texto", buildJsonObject { put("type", "string") })
                },
                required = listOf("texto"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "privacy_sanitize_text"
    }
}

class FenixSkillCatalogHandler {
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val skillName = McpToolUtils.optionalString(arguments, "name", "").trim()
            val executeRequested = McpToolUtils.optionalBoolean(arguments, "execute", false)
            if (skillName.isNotEmpty()) {
                val skill = com.fenix.mobilemcp.skills.SkillCatalog.find(skillName)
                    ?: throw McpToolException.InvalidParams("Skill '$skillName' no existe en SkillCatalog")
                if (executeRequested && skill.kind == com.fenix.mobilemcp.skills.SkillKind.TEXT) {
                    throw McpToolException.ActionFailed(
                        "SKILL_TEXT_ONLY_NOT_EXECUTABLE: La skill '${skill.name}' es de tipo ${skill.kind.name} (documental/prompt) y no posee código ejecutable",
                    )
                }
                val out = buildJsonObject {
                    put("name", skill.name)
                    put("description", skill.description)
                    put("kind", skill.kind.name)
                    put("executable", skill.kind != com.fenix.mobilemcp.skills.SkillKind.TEXT)
                }
                McpToolUtils.untrustedTextResult(Json.encodeToString(out))
            } else {
                val arr = buildJsonArray {
                    com.fenix.mobilemcp.skills.SkillCatalog.BUILT_IN.forEach { s ->
                        add(
                            buildJsonObject {
                                put("name", s.name)
                                put("description", s.description)
                                put("kind", s.kind.name)
                                put("executable", s.kind != com.fenix.mobilemcp.skills.SkillKind.TEXT)
                            },
                        )
                    }
                }
                McpToolUtils.untrustedTextResult(Json.encodeToString(arr))
            }
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("SkillCatalog error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Inspecciona el catálogo de skills integradas (turno-radar, zona-sj) y verifica su tipo (TEXT, JS_WEBVIEW, NATIVE_INTENT).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("name", buildJsonObject { put("type", "string") })
                    put("execute", buildJsonObject { put("type", "boolean") })
                },
                required = listOf(),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "skill_catalog_inspect"
    }
}

/**
 * Centro de autodescubrimiento y activación de habilidades (Skills) a demanda.
 * Permite a cualquier IA (Gemini, Claude, GPT o SLMs locales) explorar capacidades
 * dinámicamente sin inundar su ventana de contexto con esquemas innecesarios.
 */
class FenixSkillsDiscoveryHandler {
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val action = McpToolUtils.optionalString(arguments, "action", "list").lowercase()
            val skillId = McpToolUtils.optionalString(arguments, "skill", "").lowercase()
            val query = McpToolUtils.optionalString(arguments, "query", "")

            val out = when (action) {
                "list" -> {
                    val catalog = com.fenix.mobilemcp.mcp.registry.FenixToolRegistry.getSkillCatalog()
                    buildJsonObject {
                        put("status", "ok")
                        put("total_skills", catalog.size)
                        put("skills", JsonArray(catalog))
                        put("instruccion", "Para activar una habilidad y recibir sus herramientas específicas, invoca fenix_skills_discovery con action='activate' y el id de la habilidad deseada.")
                    }
                }
                "search" -> {
                    val detected = com.fenix.mobilemcp.mcp.registry.FenixToolRegistry.detectRelevantCategories(query)
                    val matchedSkills = detected.map { cat ->
                        val catTools = com.fenix.mobilemcp.mcp.registry.FenixToolRegistry.getByCategory(cat)
                        buildJsonObject {
                            put("id", cat.id)
                            put("nombre", cat.displayName)
                            put("descripcion", cat.description)
                            put("herramientas", JsonArray(catTools.map { JsonPrimitive(it.name) }))
                        }
                    }
                    buildJsonObject {
                        put("status", "ok")
                        put("query", query)
                        put("habilidades_coincidentes", JsonArray(matchedSkills))
                    }
                }
                "activate" -> {
                    val category = com.fenix.mobilemcp.mcp.registry.FenixSkillCategory.fromId(skillId)
                    val tools = com.fenix.mobilemcp.mcp.registry.FenixToolRegistry.getByCategory(category)
                    buildJsonObject {
                        put("status", "activated")
                        put("skill_id", category.id)
                        put("skill_nombre", category.displayName)
                        put("herramientas_activadas", JsonArray(tools.map { JsonPrimitive(it.name) }))
                        put("mensaje", "La habilidad '${category.displayName}' ha sido activada en esta sesión. Sus herramientas ahora están listas para ser ejecutadas.")
                    }
                }
                else -> throw McpToolException.InvalidParams("Acción desconocida: $action. Use 'list', 'search' o 'activate'")
            }

            McpToolUtils.untrustedTextResult(Json.encodeToString(out))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Error en autodescubrimiento de skills: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Centro de autodescubrimiento y activación de habilidades a demanda de Fénix. Permite consultar el catálogo ('action': 'list'), buscar por intención ('action': 'search') o activar herramientas específicas ('action': 'activate', 'skill': 'archivos'). Protege el contexto y reduce cuotas drásticamente.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", JsonArray(listOf(JsonPrimitive("list"), JsonPrimitive("search"), JsonPrimitive("activate"))))
                        put("description", "Acción a realizar: 'list' (ver catálogo), 'search' (buscar por palabras clave), 'activate' (activar habilidad en la sesión)")
                    })
                    put("skill", buildJsonObject {
                        put("type", "string")
                        put("description", "Identificador de la habilidad a activar (ej: 'archivos', 'memoria', 'dispositivo', 'seguridad', 'red', 'accesibilidad', 'conectores', 'diagnostico')")
                    })
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "Término de búsqueda cuando action es 'search'")
                    })
                },
                required = listOf(),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "fenix_skills_discovery"
    }
}

class FenixProviderCooldownHandler(
    private val db: FenixMemoryDb,
) {
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val action = McpToolUtils.optionalString(arguments, "action", "check").lowercase()
            val providerId = McpToolUtils.requireString(arguments, "provider_id")
            when (action) {
                "set" -> {
                    val seconds = McpToolUtils.optionalInt(arguments, "seconds", 60).toLong()
                    val reason = McpToolUtils.optionalString(arguments, "reason", "HTTP 429 Too Many Requests")
                    db.setProviderCooldown(providerId, seconds, reason)
                }
                "clear" -> {
                    db.reportProviderSuccess(providerId)
                }
                "check" -> Unit
                else -> throw McpToolException.InvalidParams("Acción inválida: $action (use set, clear, check)")
            }
            val inCooldown = db.isProviderInCooldown(providerId)
            val out = buildJsonObject {
                put("provider_id", providerId)
                put("action", action)
                put("in_cooldown", inCooldown)
            }
            McpToolUtils.untrustedTextResult(Json.encodeToString(out))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Provider cooldown error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Consulta o simula enfriamiento (cooldown HTTP 429) de un proveedor de IA en SQLite.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("action", buildJsonObject { put("type", "string") })
                    put("provider_id", buildJsonObject { put("type", "string") })
                    put("seconds", buildJsonObject { put("type", "integer") })
                    put("reason", buildJsonObject { put("type", "string") })
                },
                required = listOf("provider_id"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "provider_cooldown_control"
    }
}

/**
 * Herramienta de gestion dinamica de conectores y proveedores de IA (OAuth allowlist).
 * Permite a agentes conectados como Gemini o Claude auto-descubrir y autorizar nuevas IAs.
 */
class FenixConnectorManageHandler {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val action = McpToolUtils.optionalString(arguments, "action", "info").lowercase()
            when (action) {
                "authorize_domain" -> {
                    val domain = McpToolUtils.requireString(arguments, "domain")
                    val ok = OAuthPolicy.registerAllowedHost(domain)
                    val out = buildJsonObject {
                        put("action", "authorize_domain")
                        put("domain", domain)
                        put("success", ok)
                    }
                    McpToolUtils.untrustedTextResult(Json.encodeToString(out))
                }
                "authorize_uri" -> {
                    val uri = McpToolUtils.requireString(arguments, "uri")
                    val ok = OAuthPolicy.registerAllowedRedirectUri(uri)
                    val out = buildJsonObject {
                        put("action", "authorize_uri")
                        put("uri", uri)
                        put("success", ok)
                    }
                    McpToolUtils.untrustedTextResult(Json.encodeToString(out))
                }
                "remove_domain" -> {
                    val domain = McpToolUtils.requireString(arguments, "domain")
                    val ok = OAuthPolicy.removeAllowedHost(domain)
                    val out = buildJsonObject {
                        put("action", "remove_domain")
                        put("domain", domain)
                        put("success", ok)
                    }
                    McpToolUtils.untrustedTextResult(Json.encodeToString(out))
                }
                "list", "info" -> {
                    val summary = OAuthPolicy.getEffectiveAllowedSummary()
                    val out = buildJsonObject {
                        put("action", action)
                        put("oauth_enabled", true)
                        put("allowed_summary", buildJsonObject {
                            summary.forEach { (k, v) ->
                                put(k, buildJsonArray { v.forEach { add(JsonPrimitive(it)) } })
                            }
                        })
                    }
                    McpToolUtils.untrustedTextResult(Json.encodeToString(out))
                }
                else -> throw McpToolException.InvalidParams(
                    "Accion invalida: $action (use info, list, authorize_domain, authorize_uri, remove_domain)",
                )
            }
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Connector manage error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Gestiona e inspecciona dinamicamente los proveedores y conectores de IA autorizados para OAuth.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("action", buildJsonObject { put("type", "string") })
                    put("domain", buildJsonObject { put("type", "string") })
                    put("uri", buildJsonObject { put("type", "string") })
                },
                required = emptyList(),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "connector_manage"
    }
}

/**
 * Herramienta para auditar, activar, desactivar y reiniciar el servicio de accesibilidad a demanda.
 */
class FenixAccessibilityControlHandler(
    private val context: android.content.Context,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val action = McpToolUtils.optionalString(arguments, "action", "status").lowercase()
            val serviceClass = McpAccessibilityService::class.java
            val isEnabledInSettings = PermissionUtils.isAccessibilityServiceEnabled(context, serviceClass)
            val isConnected = McpAccessibilityService.instance?.isReady() == true
            val hasSecurePerm = PermissionUtils.hasWriteSecureSettings(context)

            val out = when (action) {
                "enable" -> {
                    val changed = PermissionUtils.setAccessibilityServiceEnabled(context, serviceClass, true)
                    FenixBlackBoxRecorder.recordAccessibility("ENABLE_REQUEST", "changed=$changed, hasSecurePerm=$hasSecurePerm", changed)
                    buildJsonObject {
                        put("action", "enable")
                        put("success", changed)
                        put("has_write_secure_settings", hasSecurePerm)
                        put("instructions", if (!changed) "Requiere conceder WRITE_SECURE_SETTINGS via ADB: adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS" else "Servicio activado en ajustes")
                    }
                }
                "disable" -> {
                    val changed = PermissionUtils.setAccessibilityServiceEnabled(context, serviceClass, false)
                    FenixBlackBoxRecorder.recordAccessibility("DISABLE_REQUEST", "changed=$changed, hasSecurePerm=$hasSecurePerm", changed)
                    buildJsonObject {
                        put("action", "disable")
                        put("success", changed)
                        put("has_write_secure_settings", hasSecurePerm)
                    }
                }
                "restart" -> {
                    val restarted = PermissionUtils.restartAccessibilityService(context, serviceClass)
                    FenixBlackBoxRecorder.recordAccessibility("RESTART_REQUEST", "restarted=$restarted", restarted)
                    buildJsonObject {
                        put("action", "restart")
                        put("success", restarted)
                        put("has_write_secure_settings", hasSecurePerm)
                    }
                }
                "open_settings" -> {
                    PermissionUtils.openAccessibilitySettings(context)
                    FenixBlackBoxRecorder.recordAccessibility("OPEN_SETTINGS", "Pantalla de ajustes abierta", true)
                    buildJsonObject {
                        put("action", "open_settings")
                        put("success", true)
                        put("message", "Pantalla de Ajustes > Accesibilidad abierta en el dispositivo")
                    }
                }
                "status" -> {
                    buildJsonObject {
                        put("action", "status")
                        put("enabled_in_settings", isEnabledInSettings)
                        put("is_binder_connected", isConnected)
                        put("has_write_secure_settings", hasSecurePerm)
                        put("package_name", context.packageName)
                        put("service_class", serviceClass.name)
                    }
                }
                else -> throw McpToolException.InvalidParams(
                    "Accion invalida: $action (use status, enable, disable, restart, open_settings)"
                )
            }
            McpToolUtils.untrustedTextResult(Json.encodeToString(out))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Accessibility control error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Audita, conecta, desconecta o reinicia el servicio de accesibilidad del dispositivo a demanda.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("action", buildJsonObject { put("type", "string") })
                },
                required = emptyList(),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "accessibility_control"
    }
}

/**
 * Herramienta MCP permanente para diagnostico y telemetria interna (Caja Negra).
 * Permite a agentes conectados (Gemini, Claude, ChatGPT) auto-diagnosticar fallos y proponer mejoras.
 */
class FenixBlackBoxDiagnosticsHandler {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val action = McpToolUtils.optionalString(arguments, "action", "summary").lowercase()
            val limit = McpToolUtils.optionalInt(arguments, "limit", 20)
            val category = McpToolUtils.optionalString(arguments, "category", "")

            val resultString = when (action) {
                "summary" -> {
                    val summaryObj = FenixBlackBoxRecorder.getSummary()
                    Json.encodeToString(summaryObj)
                }
                "recent_events" -> {
                    val events = FenixBlackBoxRecorder.getRecentEvents(limit, category.ifBlank { null })
                    Json.encodeToString(events)
                }
                "tool_errors" -> {
                    val errors = FenixBlackBoxRecorder.getToolErrors(limit)
                    Json.encodeToString(errors)
                }
                "clear" -> {
                    val ok = FenixBlackBoxRecorder.clearBuffer()
                    "{\"action\":\"clear\",\"success\":$ok}"
                }
                else -> throw McpToolException.InvalidParams(
                    "Accion invalida: $action (use summary, recent_events, tool_errors, clear)"
                )
            }
            McpToolUtils.untrustedTextResult(resultString)
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("BlackBox error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Inspecciona la Caja Negra permanente de telemetria, eventos internos, errores y metricas del servidor MCP.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("action", buildJsonObject { put("type", "string") })
                    put("limit", buildJsonObject { put("type", "integer") })
                    put("category", buildJsonObject { put("type", "string") })
                },
                required = emptyList(),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "blackbox_diagnostics"
    }
}

/** Búsqueda recursiva de archivos con filtros de nombre, extensión y tamaño sin root. */
class FenixFileSearchHandler {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val startPath = McpToolUtils.optionalString(arguments, "start_path", "")
            val query = McpToolUtils.optionalString(arguments, "query", "")
            val extension = McpToolUtils.optionalString(arguments, "extension", "").ifBlank { null }
            val minSizeBytes = McpToolUtils.optionalLong(arguments, "min_size_bytes", 0L)
            val maxSizeBytes = McpToolUtils.optionalLong(arguments, "max_size_bytes", Long.MAX_VALUE)
            val maxResults = McpToolUtils.optionalInt(arguments, "max_results", 50)

            val matches = FenixFileManager.searchFiles(
                startPath = startPath,
                query = query,
                extension = extension,
                minSizeBytes = minSizeBytes,
                maxSizeBytes = maxSizeBytes,
                maxResults = maxResults,
            )

            val out = buildJsonObject {
                put("query", query)
                put("matches_count", matches.size)
                put("files", buildJsonArray {
                    matches.forEach { m ->
                        add(buildJsonObject {
                            put("name", m.name)
                            put("path", m.path)
                            put("size_bytes", m.sizeBytes)
                            put("is_directory", m.isDirectory)
                            put("last_modified_ms", m.lastModifiedMs)
                        })
                    }
                })
            }
            McpToolUtils.untrustedTextResult(Json.encodeToString(out))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("File search error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Busca archivos de forma recursiva por nombre, extensión o tamaño en el almacenamiento.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("start_path", buildJsonObject { put("type", "string") })
                    put("query", buildJsonObject { put("type", "string") })
                    put("extension", buildJsonObject { put("type", "string") })
                    put("min_size_bytes", buildJsonObject { put("type", "integer") })
                    put("max_size_bytes", buildJsonObject { put("type", "integer") })
                    put("max_results", buildJsonObject { put("type", "integer") })
                },
                required = emptyList(),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "file_search"
    }
}

/** Diagnóstico de almacenamiento: clasifica por tipo (docs, código, fotos, audio, video) y detecta archivos pesados (>50MB). */
class FenixFileClassifyHandler {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val targetPath = McpToolUtils.optionalString(arguments, "target_path", "")
            val classification = FenixFileManager.countAndClassify(targetPath)
            McpToolUtils.untrustedTextResult(Json.encodeToString(classification))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("File classify error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Clasifica archivos por tipo y peso para diagnosticar y liberar espacio de memoria.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("target_path", buildJsonObject { put("type", "string") })
                },
                required = emptyList(),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "file_classify"
    }
}

/** Búsqueda interna de texto y frases (grep) dentro del contenido de archivos de texto o código. */
class FenixFileGrepHandler {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val phrase = McpToolUtils.requireString(arguments, "phrase")
            val startPath = McpToolUtils.optionalString(arguments, "start_path", "")
            val extension = McpToolUtils.optionalString(arguments, "extension", "").ifBlank { null }
            val grepResult = FenixFileManager.grepInFiles(startPath, phrase, extension)
            McpToolUtils.untrustedTextResult(Json.encodeToString(grepResult))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("File grep error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Busca palabras o frases dentro del contenido de archivos sin conocer la ruta exacta.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("phrase", buildJsonObject { put("type", "string") })
                    put("start_path", buildJsonObject { put("type", "string") })
                    put("extension", buildJsonObject { put("type", "string") })
                },
                required = listOf("phrase"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "file_grep"
    }
}

/** Inserción quirúrgica de contenido en un archivo (por línea o después de un patrón). */
class FenixFileInsertHandler {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val path = McpToolUtils.requireString(arguments, "path")
            val content = McpToolUtils.requireString(arguments, "content")
            val atLine = McpToolUtils.optionalInt(arguments, "at_line_number", 0).takeIf { it > 0 }
            val afterPattern = McpToolUtils.optionalString(arguments, "after_pattern", "").ifBlank { null }

            val insertResult = FenixFileManager.insertInFile(path, content, atLine, afterPattern)
            McpToolUtils.untrustedTextResult(Json.encodeToString(insertResult))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("File insert error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Inserta texto en una posición específica de un archivo (por número de línea o después de un patrón).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string") })
                    put("content", buildJsonObject { put("type", "string") })
                    put("at_line_number", buildJsonObject { put("type", "integer") })
                    put("after_pattern", buildJsonObject { put("type", "string") })
                },
                required = listOf("path", "content"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "file_insert"
    }
}

/** Lectura universal paginada de archivos de texto o código en el almacenamiento. */
class FenixFileReadUniversalHandler {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val path = McpToolUtils.requireString(arguments, "path")
            val startLine = McpToolUtils.optionalInt(arguments, "start_line", 1)
            val maxLines = McpToolUtils.optionalInt(arguments, "max_lines", 300)

            val readResult = FenixFileManager.readUniversal(path, startLine, maxLines)
            McpToolUtils.untrustedTextResult(Json.encodeToString(readResult))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("File read universal error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Lee archivos de texto, código o documentación con paginación de líneas.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string") })
                    put("start_line", buildJsonObject { put("type", "integer") })
                    put("max_lines", buildJsonObject { put("type", "integer") })
                },
                required = listOf("path"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "file_read_universal"
    }
}

/** Escritura o creación universal de archivos en cualquier carpeta accesible. */
class FenixFileWriteUniversalHandler {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val path = McpToolUtils.requireString(arguments, "path")
            val content = McpToolUtils.requireString(arguments, "content")
            val append = McpToolUtils.optionalBoolean(arguments, "append", false)

            val writeResult = FenixFileManager.writeUniversal(path, content, append)
            McpToolUtils.untrustedTextResult(Json.encodeToString(writeResult))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("File write universal error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Crea o sobrescribe un archivo en el almacenamiento del dispositivo.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string") })
                    put("content", buildJsonObject { put("type", "string") })
                    put("append", buildJsonObject { put("type", "boolean") })
                },
                required = listOf("path", "content"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "file_write_universal"
    }
}

/** Creación de carpetas y directorios en el almacenamiento. */
class FenixDirectoryCreateHandler {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val path = McpToolUtils.requireString(arguments, "path")
            val dirResult = FenixFileManager.createDirectory(path)
            McpToolUtils.untrustedTextResult(Json.encodeToString(dirResult))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Directory create error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Crea una nueva carpeta o estructura de directorios en el almacenamiento.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string") })
                },
                required = listOf("path"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "directory_create"
    }
}

/** Eliminación segura de archivos o carpetas con filtro perimetral. */
class FenixFileDeleteSafeHandler {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val path = McpToolUtils.requireString(arguments, "path")
            val delResult = FenixFileManager.deleteSafe(path)
            McpToolUtils.untrustedTextResult(Json.encodeToString(delResult))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("File delete safe error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Elimina un archivo o directorio de forma segura con verificación perimetral.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string") })
                },
                required = listOf("path"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "file_delete_safe"
    }
}

/** Inspección de capacidades de hardware (RAM libre, núcleos big.LITTLE y modelos cuantizados compatibles). */
class FenixHardwareProfileHandler(
    private val context: android.content.Context? = null,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(): CallToolResult =
        try {
            val profile = com.fenix.mobilemcp.hardware.HardwareCapabilitiesDetector.inspectHardware(context)
            McpToolUtils.untrustedTextResult(Json.encodeToString(profile))
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Hardware profile error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Inspecciona en tiempo real la RAM libre/total, la topología de núcleos del procesador (Efficiency vs Performance) y los modelos cuantizados recomendados para este móvil.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {},
                required = emptyList(),
            ),
        ) { _ -> execute() }
    }

    companion object {
        const val TOOL_NAME = "hardware_profile"
    }
}

/** Ejecución y compilación políglota (Python, C, C++, JS, Rust, Go, Bash, SQL) con gestión de núcleos CPU y VMs gratuitas. */
class FenixCodeExecuteHandler(
    private val context: android.content.Context? = null,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val language = McpToolUtils.requireString(arguments, "language")
            val code = McpToolUtils.requireString(arguments, "code")
            val stdin = McpToolUtils.optionalString(arguments, "stdin", "")
            val provider = McpToolUtils.optionalString(arguments, "provider", "auto")

            val result = com.fenix.mobilemcp.studio.PolyglotExecutionEngine.executeCode(
                language = language,
                code = code,
                stdin = stdin,
                preferredProvider = provider,
                context = context,
            )
            McpToolUtils.untrustedTextResult(Json.encodeToString(result))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Code execute error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Compila y ejecuta código en Python, C, C++, JavaScript, TypeScript, Rust, Go, Bash o SQLite usando los núcleos de rendimiento o servidores virtuales gratuitos sin login (Wandbox, Piston, Godbolt, H2.nexus).",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("language", buildJsonObject { put("type", "string") })
                    put("code", buildJsonObject { put("type", "string") })
                    put("stdin", buildJsonObject { put("type", "string") })
                    put("provider", buildJsonObject { put("type", "string") })
                },
                required = listOf("language", "code"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "code_execute"
    }
}

/** Subida de archivos a almacenamiento efímero sin login ni tarjeta (file.io / storage.to / tmpfiles.org). */
class FenixCloudUploadEphemeralHandler {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val path = McpToolUtils.requireString(arguments, "path")
            val provider = McpToolUtils.optionalString(arguments, "provider", "file.io")
            val res = com.fenix.mobilemcp.connectors.storage.EphemeralCloudStorageClient.uploadFile(
                filePath = path,
                preferredProvider = provider,
            )
            McpToolUtils.untrustedTextResult(Json.encodeToString(res))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Cloud ephemeral upload error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Sube un archivo del móvil a un servidor efímero gratuito sin login (file.io, storage.to, tmpfiles.org) y devuelve el enlace de descarga.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("path", buildJsonObject { put("type", "string") })
                    put("provider", buildJsonObject { put("type", "string") })
                },
                required = listOf("path"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "cloud_upload_ephemeral"
    }
}

/** Creación de extensiones personalizadas en el Marketplace mediante Vibe Coding. */
class FenixVibeExtensionCreateHandler {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val id = McpToolUtils.requireString(arguments, "id")
            val name = McpToolUtils.requireString(arguments, "name")
            val shortDescription = McpToolUtils.requireString(arguments, "short_description")
            val readmeMarkdown = McpToolUtils.optionalString(arguments, "readme_markdown", "# $name\n\n$shortDescription")
            val sourceCodeJs = McpToolUtils.optionalString(arguments, "source_code_js", "")
            val endpointUrl = McpToolUtils.optionalString(arguments, "endpoint_url", "")

            val ext = com.fenix.mobilemcp.marketplace.FenixMarketplaceExtension(
                id = id.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "-"),
                name = name,
                publisher = "Vibe Coding / Usuario",
                version = "1.0.0",
                category = com.fenix.mobilemcp.marketplace.ExtensionCategory.USER_VIBE_CODING,
                shortDescription = shortDescription,
                readmeMarkdown = readmeMarkdown,
                learnMoreUrl = "https://modelcontextprotocol.io/docs/concepts/tools",
                endpointUrl = endpointUrl.takeIf { it.isNotBlank() },
                sourceCodeJs = sourceCodeJs.takeIf { it.isNotBlank() },
                capabilitiesProvided = listOf("vibe_coded_extension"),
                isBuiltIn = false,
                isInstalled = true,
                isEnabled = true,
            )
            com.fenix.mobilemcp.marketplace.FenixExtensionRegistry.registerUserOrVibeExtension(ext)
            McpToolUtils.untrustedTextResult(Json.encodeToString(ext))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Vibe extension create error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Crea e instala una nueva extensión en el Marketplace local de Fénix (Vibe Coding), con su ficha descriptiva estilo VS Code y código opcional.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("id", buildJsonObject { put("type", "string") })
                    put("name", buildJsonObject { put("type", "string") })
                    put("short_description", buildJsonObject { put("type", "string") })
                    put("readme_markdown", buildJsonObject { put("type", "string") })
                    put("source_code_js", buildJsonObject { put("type", "string") })
                    put("endpoint_url", buildJsonObject { put("type", "string") })
                },
                required = listOf("id", "name", "short_description"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "vibe_extension_create"
    }
}

/** Control bajo demanda de Pantalla Dividida (Split-Screen / Multi-Window) sobre otras aplicaciones. */
class FenixSplitScreenControlHandler(
    private val context: android.content.Context,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun execute(arguments: JsonObject?): CallToolResult =
        try {
            val action = McpToolUtils.optionalString(arguments, "action", "toggle")
            val packageName = McpToolUtils.optionalString(arguments, "package_name", "")
            val forceResizable = McpToolUtils.optionalString(arguments, "force_resizable", "true") != "false"

            val res = com.fenix.mobilemcp.services.accessibility.SplitScreenController.executeCommand(
                context = context,
                action = action,
                targetPackage = packageName.takeIf { it.isNotBlank() },
                forceResizable = forceResizable,
            )
            McpToolUtils.untrustedTextResult(Json.encodeToString(res))
        } catch (e: McpToolException) {
            throw e
        } catch (e: Exception) {
            throw McpToolException.ActionFailed("Split screen control error: ${e.message}")
        }

    fun register(registrar: LoggedToolRegistrar, prefix: String) {
        registrar.addTool(
            toolName = TOOL_NAME,
            name = "$prefix$TOOL_NAME",
            description = "Fuerza o controla el modo de Pantalla Dividida (Split-Screen) en Android para que el agente trabaje simultáneamente sobre otra aplicación. Acciones: 'toggle' (activar/desactivar), 'launch_adjacent' (abrir package_name en la mitad adyacente), 'status'.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("action", buildJsonObject { put("type", "string") })
                    put("package_name", buildJsonObject { put("type", "string") })
                    put("force_resizable", buildJsonObject { put("type", "string") })
                },
                required = listOf("action"),
            ),
        ) { req -> execute(req.arguments) }
    }

    companion object {
        const val TOOL_NAME = "split_screen_control"
    }
}

/** Registro del bundle Fénix completo (memoria + zonas + turnos + 9 conectores + seguridad + skills + suite de archivos + IDE/Marketplace + SplitScreen). */
fun registerFenixTools(
    registrar: LoggedToolRegistrar,
    db: FenixMemoryDb,
    matcher: ZoneMatcher,
    index: List<ZoneEntry>,
    scheduler: TurnScheduler,
    toolNamePrefix: String,
    perms: ToolPermissionsConfig,
    context: android.content.Context? = null,
) {
    com.fenix.mobilemcp.marketplace.FenixExtensionRegistry.initialize(db)

    if (perms.isToolEnabled(FenixMemorySaveHandler.TOOL_NAME)) {
        FenixMemorySaveHandler(db).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(FenixMemoryReadHandler.TOOL_NAME)) {
        FenixMemoryReadHandler(db).register(registrar, toolNamePrefix)
    }
    FenixMemorySearchHandler(db).register(registrar, toolNamePrefix)
    FenixMemoryListHandler(db).register(registrar, toolNamePrefix)
    FenixSessionManageHandler(db).register(registrar, toolNamePrefix)
    if (perms.isToolEnabled(FenixZoneCheckHandler.TOOL_NAME)) {
        FenixZoneCheckHandler(matcher, index).register(registrar, toolNamePrefix)
    }
    if (perms.isToolEnabled(FenixTurnWatchHandler.TOOL_NAME)) {
        FenixTurnWatchHandler(scheduler).register(registrar, toolNamePrefix)
    }

    // Conectores F5 (S2-01..S2-09)
    FenixCloudflareHandler().register(registrar, toolNamePrefix)
    FenixSupabaseHandler(com.fenix.mobilemcp.connectors.backend.SupabaseLite()).register(registrar, toolNamePrefix)
    FenixEntregasHandler(matcher, index).register(registrar, toolNamePrefix)
    FenixGitHubHandler().register(registrar, toolNamePrefix)
    FenixGmailHandler(com.fenix.mobilemcp.connectors.google.GmailClient()).register(registrar, toolNamePrefix)
    FenixDriveHandler(com.fenix.mobilemcp.connectors.google.DriveClient()).register(registrar, toolNamePrefix)
    FenixGoogleOAuthHandler().register(registrar, toolNamePrefix)
    FenixSocialReadOnlyHandler().register(registrar, toolNamePrefix)
    FenixWhatsAppHybridHandler().register(registrar, toolNamePrefix)

    // Herramientas de Privacidad, Skills, Cooldown, Conectores Dinamicos y Modo Calle
    FenixPrivacySanitizeHandler(com.fenix.mobilemcp.privacy.LocalNerSanitizer()).register(registrar, toolNamePrefix)
    FenixSkillCatalogHandler().register(registrar, toolNamePrefix)
    FenixSkillsDiscoveryHandler().register(registrar, toolNamePrefix)
    FenixProviderCooldownHandler(db).register(registrar, toolNamePrefix)
    FenixConnectorManageHandler().register(registrar, toolNamePrefix)

    // Telemetria Caja Negra
    FenixBlackBoxDiagnosticsHandler().register(registrar, toolNamePrefix)

    // Suite Universal de Archivos Fénix
    FenixFileSearchHandler().register(registrar, toolNamePrefix)
    FenixFileClassifyHandler().register(registrar, toolNamePrefix)
    FenixFileGrepHandler().register(registrar, toolNamePrefix)
    FenixFileInsertHandler().register(registrar, toolNamePrefix)
    FenixFileReadUniversalHandler().register(registrar, toolNamePrefix)
    FenixFileWriteUniversalHandler().register(registrar, toolNamePrefix)
    FenixDirectoryCreateHandler().register(registrar, toolNamePrefix)
    FenixFileDeleteSafeHandler().register(registrar, toolNamePrefix)

    // F-Studio IDE, Hardware Guard, Cloud Sandboxes & Marketplace Vibe Coding
    FenixHardwareProfileHandler(context).register(registrar, toolNamePrefix)
    FenixCodeExecuteHandler(context).register(registrar, toolNamePrefix)
    FenixCloudUploadEphemeralHandler().register(registrar, toolNamePrefix)
    FenixVibeExtensionCreateHandler().register(registrar, toolNamePrefix)

    if (context != null) {
        FenixStreetImmunityHandler(com.fenix.mobilemcp.security.StreetImmunityChecker(context)).register(registrar, toolNamePrefix)
        FenixAccessibilityControlHandler(context).register(registrar, toolNamePrefix)
        FenixSplitScreenControlHandler(context).register(registrar, toolNamePrefix)
    }
}
