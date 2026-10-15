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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F5.1: Drive vía oficial total. Cuotas 2026: 1M/min/proy, 325k/user,
 * 400M/día; costos get5/update50/list100/download200; 750GB/día, 1TB
 * egress, export Docs 10MB. SIEMPRE fields explícitos (ahorro hotspot).
 * Token: Bearer OAuth Testing (rotación <7d vía re-confirmación).
 */
@Singleton
class DriveClient
    @Inject
    constructor() {
        private val json = Json { ignoreUnknownKeys = true }

        private fun client(): HttpClient =
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 20_000L
                    connectTimeoutMillis = 10_000L
                }
            }

        @Serializable
        data class FileEntry(
            val id: String = "",
            val name: String = "",
            val mimeType: String = "",
            val modifiedTime: String = "",
            val size: String = "0",
        )

        @Serializable
        private data class ListDto(
            val files: List<FileEntry> = emptyList(),
            val nextPageToken: String? = null,
        )

        /** Lista archivos (100 u.). pageSize máx 1000, fields mínimos. */
        suspend fun list(
            bearer: String,
            query: String? = null,
            pageSize: Int = 100,
            pageToken: String? = null,
        ): List<FileEntry> =
            withContext(Dispatchers.IO) {
                val body =
                    client().get("https://www.googleapis.com/drive/v3/files") {
                        header(HttpHeaders.Authorization, "Bearer $bearer")
                        parameter("pageSize", pageSize.coerceIn(1, 1000))
                        parameter("fields", "files(id,name,mimeType,modifiedTime,size),nextPageToken")
                        query?.let { parameter("q", it) }
                        pageToken?.let { parameter("pageToken", it) }
                    }.bodyAsText()
                json.decodeFromString(ListDto.serializer(), body).files
            }

        /** Metadatos de un archivo (5 u.). */
        suspend fun get(bearer: String, fileId: String): FileEntry =
            withContext(Dispatchers.IO) {
                val body =
                    client().get("https://www.googleapis.com/drive/v3/files/$fileId") {
                        header(HttpHeaders.Authorization, "Bearer $bearer")
                        parameter("fields", "id,name,mimeType,modifiedTime,size")
                    }.bodyAsText()
                json.decodeFromString(FileEntry.serializer(), body)
            }

        /** ¿Respuesta es límite de cuota? (403 rateLimitExceeded / 429). */
        fun isQuotaHit(status: Int): Boolean = status == 429 || status == 403
    }
