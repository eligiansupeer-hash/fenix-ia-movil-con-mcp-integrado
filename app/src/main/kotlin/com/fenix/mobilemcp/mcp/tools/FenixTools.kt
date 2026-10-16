@file:Suppress("TooManyFunctions")

package com.fenix.mobilemcp.mcp.tools

import com.fenix.mobilemcp.data.model.ToolPermissionsConfig
import com.fenix.mobilemcp.delivery.scheduler.TurnScheduler
import com.fenix.mobilemcp.delivery.zones.ZoneMatcher
import com.fenix.mobilemcp.delivery.zones.ZoneEntry
import com.fenix.mobilemcp.mcp.McpToolException
import com.fenix.mobilemcp.mcp.oauth.OAuthPolicy
import com.fenix.mobilemcp.memory.FenixMemoryDb
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
            if (checkPkg.isNotEmpty() && com.fenix.mobilemcp.security.StreetImmunityChecker.isDeliveryPackageBlocked(checkPkg)) {
                throw IllegalStateException("ANTI_BAN_BLOCKED: Interacción directa con '$checkPkg' bloqueada por cortafuegos de Modo Calle")
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

/** Registro del bundle Fénix completo (memoria + zonas + turnos + 9 conectores + seguridad + skills). */
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
    FenixProviderCooldownHandler(db).register(registrar, toolNamePrefix)
    FenixConnectorManageHandler().register(registrar, toolNamePrefix)
    if (context != null) {
        FenixStreetImmunityHandler(com.fenix.mobilemcp.security.StreetImmunityChecker(context)).register(registrar, toolNamePrefix)
    }
}
