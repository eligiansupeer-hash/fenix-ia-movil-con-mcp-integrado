package com.fenix.mobilemcp.connectors.google

/**
 * F5.1: constantes OAuth Google en modo Testing (sin auditoría CASA).
 * Callback a localhost:3000; refresh con re-confirmación <2s vía Accessibility.
 * Cuotas 2026: Drive 1M/min/proy (get5/update50/list100/download200),
 * Gmail 6k/min/user (history.list=2, NUNCA polling), Fotos triple vía.
 */
object GoogleOAuth {
    const val CALLBACK_URL = "http://127.0.0.1:3000/auth/callback"
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
    const val GMAIL_SCOPE = "https://www.googleapis.com/auth/gmail.modify"
    const val PICKER_SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"
    const val TOKEN_DB = "tokens.db"

    /** Dominios permitidos (anti-SSRF). */
    val ALLOWLIST =
        listOf(
            "googleusercontent.com",
            "photos.google.com",
            "photoslibrary.googleapis.com",
            "photospicker.googleapis.com",
            "www.googleapis.com",
            "oauth2.googleapis.com",
        )
}
