package com.fenix.mobilemcp.connectors.whatsapp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.util.concurrent.ConcurrentLinkedQueue

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

    private val phoneDigitsRegex = Regex("^[1-9][0-9]{7,14}$")
    private val proactiveSendTimestamps = ConcurrentLinkedQueue<Long>()

    private fun pruneOldSends(now: Long = System.currentTimeMillis()) {
        val oneHourAgo = now - 3_600_000L
        while (true) {
            val head = proactiveSendTimestamps.peek() ?: break
            if (head < oneHourAgo) {
                proactiveSendTimestamps.poll()
            } else {
                break
            }
        }
    }

    fun resetQuotaForTesting() {
        proactiveSendTimestamps.clear()
    }

    /** Construye URI profunda `whatsapp://send?phone=...&text=...` validando formato E.164 sin '+'. */
    fun buildSendUri(phone: String, message: String): String {
        val cleanPhone = phone.trim().removePrefix("+").replace(Regex("[\\s()-]"), "")
        require(phoneDigitsRegex.matches(cleanPhone)) {
            "Teléfono inválido para WhatsApp E.164 ('$phone'); ingrese de 8 a 15 dígitos con código de país"
        }
        require(message.isNotBlank()) { "El texto del mensaje no puede estar vacío" }
        val encodedText = URLEncoder.encode(message, "UTF-8")
        return "$SEND_INTENT?phone=$cleanPhone&text=$encodedText"
    }

    /** Evalúa y registra cuota de envíos proactivos por hora (máx 10/h). */
    @Synchronized
    fun evaluateProactiveSend(
        phone: String,
        message: String,
        simulateCount: Int = 1,
    ): JsonObject {
        val uri = buildSendUri(phone, message)
        val now = System.currentTimeMillis()
        pruneOldSends(now)

        val count = simulateCount.coerceIn(1, 25)
        if (proactiveSendTimestamps.size + count > MAX_PROACTIVE_PER_HOUR) {
            throw IllegalStateException(
                "WHATSAPP_RATE_LIMIT_EXCEEDED: límite proactivo de $MAX_PROACTIVE_PER_HOUR envíos/hora superado (usados=${proactiveSendTimestamps.size}, solicitados=$count)",
            )
        }
        repeat(count) {
            proactiveSendTimestamps.add(now)
        }
        val used = proactiveSendTimestamps.size
        return buildJsonObject {
            put("package_standard", PACKAGE)
            put("package_business", PACKAGE_BUSINESS)
            put("intent_uri", uri)
            put("proactive_used_hour", used)
            put("proactive_remaining_hour", (MAX_PROACTIVE_PER_HOUR - used).coerceAtLeast(0))
            put("min_pause_ms", PROACTIVE_MIN_PAUSE_MS)
        }
    }
}
