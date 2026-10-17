package com.fenix.mobilemcp.connectors.backend

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * F5.6: Cloudflare. Zero Trust/tunnel ilimitado BW, 1000 túneles/rutas,
 * 50 seats. cloudflared SOLO en PC; el móvil es cliente Streamable+KeepAlive
 * (calle→túnel con headers CF-Access, casa→LAN).
 */
object CloudflareClient {
    const val HEADER_CLIENT_ID = "CF-Access-Client-Id"
    const val HEADER_CLIENT_SECRET = "CF-Access-Client-Secret"
    private const val VERIFY_URL = "https://api.cloudflare.com/client/v4/user/tokens/verify"

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000L
                connectTimeoutMillis = 8_000L
            }
        }
    }

    /** Orden de rutas: loopback → LAN/mDNS → túnel (solo-online). */
    fun routeOrder(localMcp: String, tunnelMcp: String, online: Boolean): List<String> {
        require(localMcp.startsWith("http://") || localMcp.startsWith("https://")) {
            "local_mcp debe iniciar con http:// o https://"
        }
        if (online) {
            require(tunnelMcp.startsWith("https://")) {
                "tunnel_mcp requiere esquema seguro https://"
            }
        }
        return if (online) listOf(localMcp, tunnelMcp) else listOf(localMcp)
    }

    /** Verifica un API Token de Cloudflare contra el endpoint oficial v4. */
    suspend fun verifyToken(apiToken: String): String = withContext(Dispatchers.IO) {
        require(apiToken.isNotBlank()) { "api_token no puede estar vacío" }
        val response = httpClient.get(VERIFY_URL) {
            header(HttpHeaders.Authorization, "Bearer ${apiToken.trim()}")
            header(HttpHeaders.Accept, "application/json")
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("Cloudflare HTTP ${response.status.value}: $body")
        }
        body
    }
}
