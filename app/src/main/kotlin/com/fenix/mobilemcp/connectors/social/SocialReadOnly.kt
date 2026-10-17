package com.fenix.mobilemcp.connectors.social

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * F5.3: IG/FB SOLO lectura (Fase 1). Graph API no sirve para feed propio ni
 * algoritmo → UI en nodo B con scheduler anti-delivery. Moldeo activo
 * (dwell/likes) solo en F10 tras medición. Límites anti-baneo.
 */
object SocialReadOnly {
    const val IG_PACKAGE = "com.instagram.android"
    const val FB_PACKAGE = "com.facebook.katana"
    const val FB_LITE_PACKAGE = "com.facebook.lite"

    /** Tope visitas de perfil por hora, sesiones diurnas discontinuas. */
    const val MAX_PROFILE_VISITS_PER_HOUR = 40

    /** Dwell en Reels relevantes vs swipe rápido en irrelevantes. */
    const val DWELL_RELEVANT_MS_MIN = 15_000L
    const val DWELL_RELEVANT_MS_MAX = 35_000L
    const val FAST_SWIPE_MS = 800L

    /** Gestos siempre dispatchGesture con jitter; jamás click en nodos invisibles. */
    const val TOUCH_MIN_MS = 45L
    const val TOUCH_MAX_MS = 110L

    val SUPPORTED_PACKAGES = setOf(IG_PACKAGE, FB_PACKAGE, FB_LITE_PACKAGE)
    val ALLOWED_READ_ACTIONS = setOf("read_feed", "view_profile", "watch_reel", "scroll_stories", "inspect_ui")
    val FORBIDDEN_WRITE_ACTIONS = setOf("post", "comment", "like", "follow", "unfollow", "dm", "share", "publish")

    private val profileVisitTimestamps = ConcurrentLinkedQueue<Long>()

    private fun pruneOldVisits(now: Long = System.currentTimeMillis()) {
        val oneHourAgo = now - 3_600_000L
        while (true) {
            val head = profileVisitTimestamps.peek() ?: break
            if (head < oneHourAgo) {
                profileVisitTimestamps.poll()
            } else {
                break
            }
        }
    }

    fun resetQuotaForTesting() {
        profileVisitTimestamps.clear()
    }

    /** Evalúa y aplica la política de solo lectura y tope horario en redes sociales. */
    @Synchronized
    fun evaluateSocialAction(
        packageName: String,
        actionType: String,
        simulateVisitCount: Int = 1,
    ): JsonObject {
        val pkg = packageName.trim()
        val act = actionType.trim().lowercase()
        require(pkg in SUPPORTED_PACKAGES) {
            "Paquete social '$pkg' no reconocido; permitidos: $SUPPORTED_PACKAGES"
        }
        if (act in FORBIDDEN_WRITE_ACTIONS || act !in ALLOWED_READ_ACTIONS) {
            throw IllegalStateException(
                "POLITICA_SOLO_LECTura_VIOLADA: la acción '$act' en $pkg está prohibida en Fase 1 (solo lectura permitida: $ALLOWED_READ_ACTIONS)",
            )
        }

        val now = System.currentTimeMillis()
        pruneOldVisits(now)

        if (act == "view_profile") {
            val countToRecord = simulateVisitCount.coerceIn(1, 50)
            if (profileVisitTimestamps.size + countToRecord > MAX_PROFILE_VISITS_PER_HOUR) {
                throw IllegalStateException(
                    "LIMITE_ANTI_BANEO_EXCEDIDO: visitas de perfil por hora superan el máximo de $MAX_PROFILE_VISITS_PER_HOUR (actuales=${profileVisitTimestamps.size}, solicitadas=$countToRecord)",
                )
            }
            repeat(countToRecord) {
                profileVisitTimestamps.add(now)
            }
        }

        val used = profileVisitTimestamps.size
        return buildJsonObject {
            put("package_name", pkg)
            put("action_type", act)
            put("read_only_enforced", true)
            put("allowed", true)
            put("profile_visits_used_hour", used)
            put("profile_visits_remaining_hour", (MAX_PROFILE_VISITS_PER_HOUR - used).coerceAtLeast(0))
            put("dwell_min_ms", DWELL_RELEVANT_MS_MIN)
            put("dwell_max_ms", DWELL_RELEVANT_MS_MAX)
            put("touch_jitter_range_ms", "$TOUCH_MIN_MS..$TOUCH_MAX_MS")
        }
    }
}
