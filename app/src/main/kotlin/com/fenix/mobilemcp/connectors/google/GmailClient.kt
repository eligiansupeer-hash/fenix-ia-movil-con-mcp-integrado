package com.fenix.mobilemcp.connectors.google

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F5.1: Gmail total. Cuotas: 1.2M/min/proy, 6k/user, 80M/día.
 * Costos: history.list=2, messages.list=5, messages.get=20, threads.get=40,
 * send=100. Regla: incremental con historyId persistido + BÚSQUEDA TOTAL
 * bajo demanda (q de Gmail, todo el historial, no solo lo nuevo).
 */
@Singleton
class GmailClient
    @Inject
    constructor() {
        private val json = Json { ignoreUnknownKeys = true }

        private val httpClient: HttpClient by lazy {
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 20_000L
                    connectTimeoutMillis = 10_000L
                }
            }
        }

        @Serializable
        data class HistoryDto(
            val historyId: String = "",
        )

        /** Novedades desde historyId (2 u.). Persistir el nuevo historyId. */
        suspend fun historyList(bearer: String, lastHistoryId: String): String =
            withContext(Dispatchers.IO) {
                require(bearer.isNotBlank()) { "Bearer token de Gmail no puede estar vacío" }
                require(lastHistoryId.isNotBlank()) { "lastHistoryId no puede estar vacío" }
                val response =
                    httpClient.get("https://gmail.googleapis.com/gmail/v1/users/me/history") {
                        header(HttpHeaders.Authorization, "Bearer ${bearer.trim()}")
                        parameter("startHistoryId", lastHistoryId.trim())
                        parameter("historyTypes", "messageAdded")
                    }
                val body = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("Gmail HTTP ${response.status.value}: $body")
                }
                json.decodeFromString(HistoryDto.serializer(), body).historyId
            }

        /** Búsqueda TOTAL en todo el buzón (5 u. + gets). */
        suspend fun searchAll(bearer: String, query: String, max: Int = 20): String =
            withContext(Dispatchers.IO) {
                require(bearer.isNotBlank()) { "Bearer token de Gmail no puede estar vacío" }
                val response =
                    httpClient.get("https://gmail.googleapis.com/gmail/v1/users/me/messages") {
                        header(HttpHeaders.Authorization, "Bearer ${bearer.trim()}")
                        parameter("q", query)
                        parameter("maxResults", max.coerceIn(1, 100))
                    }
                val body = response.bodyAsText()
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("Gmail HTTP ${response.status.value}: $body")
                }
                body
            }
    }
