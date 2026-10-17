package com.fenix.mobilemcp.services.storage

import com.fenix.mobilemcp.data.model.ServerConfig
import com.fenix.mobilemcp.mcp.McpToolException
import java.net.InetAddress
import java.net.MalformedURLException
import java.net.URL

private val BLOCKED_HOST_PATTERNS = listOf(
    Regex("""(?i)^(localhost|127\.\d+\.\d+\.\d+|0\.0\.0\.0|::1|\[::1\])$"""),
    Regex("""^10\.\d+\.\d+\.\d+$"""),
    Regex("""^192\.168\.\d+\.\d+$"""),
    Regex("""^172\.(1[6-9]|2\d|3[0-1])\.\d+\.\d+$"""),
    Regex("""^169\.254\.\d+\.\d+$"""),
)

internal fun isSsrfHostBlocked(host: String): Boolean {
    val clean = host.trim().lowercase()
    if (clean.isEmpty()) return true
    if (BLOCKED_HOST_PATTERNS.any { it.matches(clean) }) return true
    return try {
        val addr = InetAddress.getByName(clean)
        addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress
    } catch (_: Exception) {
        false
    }
}

/**
 * Parses [url] and validates it for downloading: only HTTP/HTTPS are allowed, plain HTTP requires
 * [ServerConfig.allowHttpDownloads], and loopback/private/link-local SSRF addresses are rejected.
 * Throws [McpToolException.ActionFailed] on a malformed or disallowed URL.
 */
internal fun parseAndValidateDownloadUrl(
    url: String,
    config: ServerConfig,
): URL {
    val parsedUrl =
        try {
            URL(url)
        } catch (e: MalformedURLException) {
            throw McpToolException.ActionFailed("Invalid URL: $url", e)
        }
    val errorMessage =
        when {
            parsedUrl.protocol != "http" && parsedUrl.protocol != "https" -> {
                "Unsupported URL protocol: ${parsedUrl.protocol}. Only HTTP and HTTPS are supported."
            }

            isSsrfHostBlocked(parsedUrl.host ?: "") -> {
                "SSRF_BLOCKED: Downloads from loopback, private, or link-local addresses (${parsedUrl.host}) are forbidden."
            }

            parsedUrl.protocol == "http" && !config.allowHttpDownloads -> {
                "HTTP downloads are not allowed. Enable 'Allow HTTP Downloads' in settings, or use HTTPS."
            }

            else -> {
                null
            }
        }
    if (errorMessage != null) {
        throw McpToolException.ActionFailed(errorMessage)
    }
    return parsedUrl
}
