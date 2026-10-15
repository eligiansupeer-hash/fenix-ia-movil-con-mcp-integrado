package com.fenix.mobilemcp.connectors.whatsapp

/**
 * F5.2: WhatsApp híbrido nativo. Cloud API DESCARTADA ($0 muerta 1-oct-2026:
 * 1000 serv/mes, Tier0 250/24h, bots IA general prohibidos) + Baileys (ban).
 * Vía: NotificationListener + RemoteInput (leer/responder sin pantalla, ~0%
 * ban) + Accessibility/Intents solo proactivo/Stories. Todo en nodo B.
 */
object WhatsAppHybrid {
    const val PACKAGE = "com.whatsapp"
    const val PACKAGE_BUSINESS = "com.whatsapp.w4b"
    const val SEND_INTENT = "whatsapp://send"

    /** Solo responder hilos con notificación activa; proactivo con pausas humanas. */
    const val PROACTIVE_MIN_PAUSE_MS = 30_000L
    const val MAX_PROACTIVE_PER_HOUR = 10
}
