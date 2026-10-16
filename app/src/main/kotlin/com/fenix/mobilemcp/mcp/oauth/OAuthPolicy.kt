package com.fenix.mobilemcp.mcp.oauth

import java.net.URI

/**
 * Security policy constants and predicates for the self-contained OAuth authorization server:
 * the redirect-URI allowlist (a CLOSED set), resource matching (RFC 8707), and the locked TTL/cap
 * values agreed for tokens, codes, approvals, and the client registry.
 */
object OAuthPolicy {
    /** The fixed Claude.ai connector callback. */
    const val CLAUDE_REDIRECT_URI = "https://claude.ai/api/mcp/auth_callback"

    /**
     * Legacy/fixed ChatGPT connector callback used by already-published ChatGPT MCP integrations.
     * Ported from the fix/chatgpt-oauth-redirect fork by GitHub user ciel051130.
     */
    const val CHATGPT_REDIRECT_URI = "https://chatgpt.com/connector_platform_oauth_redirect"

    /** ChatGPT also uses per-connector OAuth callback URLs under this exact host/path prefix. */
    private const val CHATGPT_REDIRECT_HOST = "chatgpt.com"
    private const val CHATGPT_REDIRECT_PATH_PREFIX = "/connector/oauth/"

    /**
     * Google (Gemini Web custom MCP connectors) uses per-connector OAuth callback URLs under these
     * exact hosts. Observed live 2026-10-16 from a real Gemini connector attempt:
     * `https://oauth-redirect-sandbox.googleusercontent.com/r/user_bound_custom-mcp-<user>-<mcp-host>`
     * (plus `/a/` variant and `oauth-redirect-test`/prod hosts).
     */
    private const val GOOGLE_REDIRECT_HOST_SANDBOX = "oauth-redirect-sandbox.googleusercontent.com"
    private const val GOOGLE_REDIRECT_HOST_TEST = "oauth-redirect-test.googleusercontent.com"
    private const val GOOGLE_REDIRECT_HOST_PROD = "oauth-redirect.googleusercontent.com"
    private val GOOGLE_REDIRECT_HOSTS = setOf(
        GOOGLE_REDIRECT_HOST_SANDBOX,
        GOOGLE_REDIRECT_HOST_TEST,
        GOOGLE_REDIRECT_HOST_PROD,
    )
    private const val GOOGLE_REDIRECT_PATH_PREFIX_R = "/r/"
    private const val GOOGLE_REDIRECT_PATH_PREFIX_A = "/a/"

    /** Exact non-loopback redirect URIs accepted by the allowlist (add other hosted connectors here). */
    val ALLOWED_REDIRECT_URIS = setOf(CLAUDE_REDIRECT_URI, CHATGPT_REDIRECT_URI)

    /** Conjunto dinamico de hosts y URIs autorizados en tiempo de ejecucion (thread-safe). */
    private val dynamicAllowedHosts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val dynamicAllowedUris = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Maximum persisted OAuth clients before eviction. */
    const val MAX_OAUTH_CLIENTS = 20

    /** Access-token lifetime (24h). */
    const val ACCESS_TOKEN_TTL_SECONDS = 86_400L

    /** Refresh-token lifetime (90d). */
    const val REFRESH_TOKEN_TTL_SECONDS = 7_776_000L

    /** Authorization-code lifetime (60s). */
    const val AUTH_CODE_TTL_MS = 60_000L

    /** On-device approval window (5 minutes). */
    const val APPROVAL_WINDOW_MS = 300_000L

    /** Match-code modulo: a 2-digit, zero-padded confirmation code. */
    const val MATCH_CODE_MODULO = 100

    /** Maximum concurrently-pending approvals (caps notification flooding). */
    const val MAX_PENDING_APPROVALS = 10

    /** Idle gap after which a client's next authenticated request is logged as a new session (30 min). */
    const val IDLE_SESSION_LOG_THRESHOLD_MS = 1_800_000L

    private const val SCHEME_SEPARATOR = "://"
    private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1")

    /**
     * Valida si un URI de redireccion esta permitido. Admite:
     * 1. URIs fijas en [ALLOWED_REDIRECT_URIS] o [dynamicAllowedUris].
     * 2. Callbacks de ChatGPT (`chatgpt.com/connector/oauth/`).
     * 3. Callbacks de Google Gemini (`oauth-redirect-{sandbox,test,prod}.googleusercontent.com` bajo `/r/` o `/a/`).
     * 4. Hosts dinamicos registrados en tiempo de ejecucion en [dynamicAllowedHosts] con esquema https.
     * 5. Loopback HTTP (`localhost` / `127.0.0.1` / `[::1]`) para clientes locales de desarrollo.
     */
    fun isAllowedRedirectUri(uri: String): Boolean {
        if (uri in ALLOWED_REDIRECT_URIS || uri in dynamicAllowedUris) return true

        val parsed = runCatching { URI(uri) }.getOrNull() ?: return false
        val host = parsed.host?.removePrefix("[")?.removeSuffix("]")?.lowercase() ?: return false
        val path = parsed.path ?: ""
        val isHttps = parsed.scheme == "https"

        if (isHttps && host in dynamicAllowedHosts) return true

        val isChatGptConnectorCallback =
            isHttps && host == CHATGPT_REDIRECT_HOST && path.startsWith(CHATGPT_REDIRECT_PATH_PREFIX)
        val isGoogleConnectorCallback =
            isHttps &&
                host in GOOGLE_REDIRECT_HOSTS &&
                (path.startsWith(GOOGLE_REDIRECT_PATH_PREFIX_R) || path.startsWith(GOOGLE_REDIRECT_PATH_PREFIX_A))
        val isLoopback = parsed.scheme == "http" && host in LOOPBACK_HOSTS

        return isChatGptConnectorCallback || isGoogleConnectorCallback || isLoopback
    }

    /**
     * Registra dinamicamente un host HTTPS permitido para callbacks OAuth (ej. "chat.mistral.ai").
     */
    fun registerAllowedHost(rawHost: String): Boolean {
        val cleanHost = rawHost.trim().lowercase().removePrefix("https://").substringBefore("/").substringBefore(":")
        if (cleanHost.isNotEmpty() && !cleanHost.contains(" ") && !cleanHost.contains("@")) {
            return dynamicAllowedHosts.add(cleanHost)
        }
        return false
    }

    /**
     * Registra dinamicamente un URI de redireccion HTTPS exacto.
     */
    fun registerAllowedRedirectUri(rawUri: String): Boolean {
        val cleanUri = rawUri.trim()
        if (cleanUri.startsWith("https://") && !cleanUri.contains(" ")) {
            return dynamicAllowedUris.add(cleanUri)
        }
        return false
    }

    /**
     * Remueve un host registrado dinamicamente.
     */
    fun removeAllowedHost(rawHost: String): Boolean {
        val cleanHost = rawHost.trim().lowercase().removePrefix("https://").substringBefore("/").substringBefore(":")
        return dynamicAllowedHosts.remove(cleanHost)
    }

    /**
     * Lista los hosts y URIs autorizados actualmente (fijos + dinamicos).
     */
    fun getEffectiveAllowedSummary(): Map<String, List<String>> =
        mapOf(
            "fixed_uris" to ALLOWED_REDIRECT_URIS.toList(),
            "google_hosts" to GOOGLE_REDIRECT_HOSTS.toList(),
            "chatgpt_hosts" to listOf(CHATGPT_REDIRECT_HOST),
            "dynamic_hosts" to dynamicAllowedHosts.toList(),
            "dynamic_uris" to dynamicAllowedUris.toList(),
        )

    /**
     * RFC 8707 resource matching: case-insensitive on scheme+host, trailing-slash-insensitive, exact
     * otherwise. Returns false when [requested] is blank.
     */
    fun resourceMatches(
        requested: String,
        canonical: String,
    ): Boolean {
        if (requested.isBlank()) return false
        return normalize(requested) == normalize(canonical)
    }

    private fun normalize(resource: String): String {
        val trimmed = resource.trim().removeSuffix("/")
        val schemeSep = trimmed.indexOf(SCHEME_SEPARATOR)
        if (schemeSep < 0) return trimmed.lowercase()
        val scheme = trimmed.substring(0, schemeSep).lowercase()
        val rest = trimmed.substring(schemeSep + SCHEME_SEPARATOR.length)
        val slash = rest.indexOf('/')
        return if (slash < 0) {
            "$scheme://${rest.lowercase()}"
        } else {
            "$scheme://${rest.substring(0, slash).lowercase()}${rest.substring(slash)}"
        }
    }
}
