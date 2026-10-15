package com.fenix.mobilemcp.connectors.social

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
}
