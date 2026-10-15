package com.fenix.mobilemcp.connectors.backend

/**
 * F5.6: Cloudflare. Zero Trust/tunnel ilimitado BW, 1000 túneles/rutas,
 * 50 seats. cloudflared SOLO en PC; el móvil es cliente Streamable+KeepAlive
 * (calle→túnel con headers CF-Access, casa→LAN).
 */
object CloudflareClient {
    const val HEADER_CLIENT_ID = "CF-Access-Client-Id"
    const val HEADER_CLIENT_SECRET = "CF-Access-Client-Secret"

    /** Orden de rutas: loopback → LAN/mDNS → túnel (solo-online). */
    fun routeOrder(localMcp: String, tunnelMcp: String, online: Boolean): List<String> =
        if (online) listOf(localMcp, tunnelMcp) else listOf(localMcp)
}
