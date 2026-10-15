package com.fenix.mobilemcp.connectors.backend

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        private fun client(): HttpClient =
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 15_000L
                    connectTimeoutMillis = 8_000L
                }
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
                client().get("$baseUrl/rest/v1/$table") {
                    header("apikey", anonKey)
                    header(HttpHeaders.Authorization, "Bearer $anonKey")
                    parameter("select", columns)
                    parameter("limit", limit.coerceIn(1, 200))
                }.bodyAsText()
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
