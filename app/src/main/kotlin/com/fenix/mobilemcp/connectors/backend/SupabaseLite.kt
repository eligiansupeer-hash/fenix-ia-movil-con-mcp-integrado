package com.fenix.mobilemcp.connectors.backend

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F5.6: Supabase liviano (PostgREST). Límites free: 2 proy, 500MB DB,
 * 1GB storage, 5GB egress, pausa 7d sin queries, read-only >500MB.
 * Móvil: columnas mínimas + paginado. PC: heartbeat 48h + pg_dump + VACUUM.
 */
@Singleton
class SupabaseLite
    @Inject
    constructor() {
        private val httpClient: HttpClient by lazy {
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 15_000L
                    connectTimeoutMillis = 8_000L
                }
            }
        }

        private val identifierRegex = Regex("^[a-zA-Z_][a-zA-Z0-9_]{0,62}$")

        fun validateBaseUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            val uri = try {
                URI(trimmed)
            } catch (e: Exception) {
                throw IllegalArgumentException("URL de Supabase inválida: ${e.message}")
            }
            val scheme = uri.scheme?.lowercase() ?: ""
            val host = uri.host?.lowercase() ?: ""
            require(scheme == "https") { "Supabase requiere HTTPS (recibido: $scheme)" }
            require(host.endsWith(".supabase.co") || host.endsWith(".supabase.in")) {
                "Host no permitido por política anti-SSRF de Supabase: $host"
            }
            return trimmed
        }

        private fun validateTable(table: String): String {
            val clean = table.trim()
            require(identifierRegex.matches(clean)) { "Nombre de tabla inválido: $clean" }
            return clean
        }

        /** SELECT mínimo (ahorra egress): tabla + columnas + límite. */
        suspend fun select(
            baseUrl: String,
            anonKey: String,
            table: String,
            columns: String = "id",
            limit: Int = 50,
        ): String =
            withContext(Dispatchers.IO) {
                val safeBase = validateBaseUrl(baseUrl)
                val safeTable = validateTable(table)
                require(anonKey.isNotBlank()) { "anonKey no puede estar vacía" }
                val response = httpClient.get("$safeBase/rest/v1/$safeTable") {
                    header("apikey", anonKey.trim())
                    header(HttpHeaders.Authorization, "Bearer ${anonKey.trim()}")
                    parameter("select", columns)
                    parameter("limit", limit.coerceIn(1, 200))
                }
                val body = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("Supabase SELECT HTTP ${response.status.value}: $body")
                }
                body
            }

        /** INSERT de fila JSON con retorno representation mínimo. */
        suspend fun insert(
            baseUrl: String,
            anonKey: String,
            table: String,
            rowJson: String,
        ): String =
            withContext(Dispatchers.IO) {
                val safeBase = validateBaseUrl(baseUrl)
                val safeTable = validateTable(table)
                require(anonKey.isNotBlank()) { "anonKey no puede estar vacía" }
                val response = httpClient.post("$safeBase/rest/v1/$safeTable") {
                    header("apikey", anonKey.trim())
                    header(HttpHeaders.Authorization, "Bearer ${anonKey.trim()}")
                    header("Prefer", "return=representation")
                    contentType(ContentType.Application.Json)
                    setBody(rowJson)
                }
                val body = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("Supabase INSERT HTTP ${response.status.value}: $body")
                }
                body
            }

        /** DELETE filtrado por columna exacta (`col=eq.val`) para limpieza de pruebas QA. */
        suspend fun deleteEquals(
            baseUrl: String,
            anonKey: String,
            table: String,
            column: String,
            value: String,
        ): String =
            withContext(Dispatchers.IO) {
                val safeBase = validateBaseUrl(baseUrl)
                val safeTable = validateTable(table)
                require(identifierRegex.matches(column.trim())) { "Columna inválida: $column" }
                require(value.isNotBlank()) { "Valor de filtro no puede estar vacío" }
                val response = httpClient.delete("$safeBase/rest/v1/$safeTable") {
                    header("apikey", anonKey.trim())
                    header(HttpHeaders.Authorization, "Bearer ${anonKey.trim()}")
                    header("Prefer", "return=representation")
                    parameter(column.trim(), "eq.$value")
                }
                val body = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("Supabase DELETE HTTP ${response.status.value}: $body")
                }
                body
            }

        /** Heartbeat anti-pausa (lo corre la PC cada 48h, no el móvil). */
        suspend fun heartbeat(baseUrl: String, anonKey: String): Boolean =
            try {
                select(baseUrl, anonKey, "notification_outbox", "id", 1)
                true
            } catch (e: Exception) {
                false
            }
    }
