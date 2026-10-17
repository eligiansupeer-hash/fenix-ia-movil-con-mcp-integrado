package com.fenix.mobilemcp.connectors.google

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * F5.1: constantes OAuth Google en modo Testing (sin auditoría CASA).
 * Callback a localhost:3000; refresh con re-confirmación <2s vía Accessibility.
 * Cuotas 2026: Drive 1M/min/proy (get5/update50/list100/download200),
 * Gmail 6k/min/user (history.list=2, NUNCA polling), Fotos triple vía.
 */
object GoogleOAuth {
    const val CALLBACK_URL = "http://127.0.0.1:3000/auth/callback"
    const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
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
            "gmail.googleapis.com",
            "oauth2.googleapis.com",
            "accounts.google.com",
        )

    /** Verifica que una URL destino pertenezca estrictamente al ALLOWLIST oficial o al callback local PKCE. */
    fun isAllowedUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed == CALLBACK_URL || trimmed.startsWith("$CALLBACK_URL?")) return true
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host?.lowercase() ?: return false
            if (scheme != "https") return false
            ALLOWLIST.any { allowed -> host == allowed || host.endsWith(".$allowed") }
        } catch (_: Exception) {
            false
        }
    }

    /** Genera un code_verifier criptográfico RFC 7636 (43-128 chars URL-safe). */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Calcula `code_challenge = BASE64URL(SHA-256(code_verifier))` (PKCE S256). */
    fun computeCodeChallengeS256(codeVerifier: String): String {
        require(codeVerifier.length in 43..128) {
            "code_verifier debe tener entre 43 y 128 caracteres según RFC 7636"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** Construye la URL de autorización OAuth2 con PKCE S256 y scopes explícitos. */
    fun buildAuthorizationUrl(
        clientId: String,
        scopes: List<String> = listOf(DRIVE_SCOPE, GMAIL_SCOPE, PICKER_SCOPE),
        codeVerifier: String,
        state: String = "fenix_pkce_state",
    ): String {
        require(clientId.isNotBlank()) { "clientId no puede estar vacío" }
        val challenge = computeCodeChallengeS256(codeVerifier)
        val encodedScope = URLEncoder.encode(scopes.joinToString(" "), "UTF-8")
        val encodedRedirect = URLEncoder.encode(CALLBACK_URL, "UTF-8")
        val encodedClient = URLEncoder.encode(clientId.trim(), "UTF-8")
        val encodedState = URLEncoder.encode(state.trim(), "UTF-8")
        return "$AUTH_ENDPOINT?client_id=$encodedClient&redirect_uri=$encodedRedirect" +
            "&response_type=code&scope=$encodedScope" +
            "&code_challenge=$challenge&code_challenge_method=S256" +
            "&access_type=offline&prompt=consent&state=$encodedState"
    }
}
