package com.fenix.mobilemcp.net

/**
 * F7: perfiles de red SIN Tailscale. Cada nodo guarda un perfil por red
 * conocida (casa/calle/PC) y conmuta: 127.0.0.1 → LAN directa (mDNS+slug) →
 * túnel Cloudflare solo-online. Hotspot: gateway DHCP + IP del par por mDNS.
 */
data class NetworkProfile(
    val ssid: String,
    val kind: NetKind,
    val localPeerSuffix: String = ".local",
    val useTunnel: Boolean = false,
)

enum class NetKind { HOME_WIFI, HOTSPOT_STREET, PC_LAN }

object NetworkProfiles {
    /** Orden de prueba de rutas hacia un par (slug g77/lake). */
    fun routeOrder(slug: String, profile: NetworkProfile): List<String> {
        val direct = mutableListOf("http://127.0.0.1:8080/mcp")
        if (!profile.useTunnel) {
            direct.add("http://fenix-$slug${profile.localPeerSuffix}:8080/mcp")
        } else {
            direct.add("https://PEER_TUNNEL_HOST/mcp")
        }
        return direct
    }
}
