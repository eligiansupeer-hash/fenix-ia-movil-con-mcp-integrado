package com.fenix.mobilemcp.connectors.github

/**
 * F5.4: GitHub vía oficial total. Cuotas free: 5k/h REST+GraphQL,
 * Actions 2k min/mes privados (ilimitado públicos).
 * Patrón: Kotlin=Ktor control (issues/PRs/diffs/merge/dispatch),
 * Termux single-shot (<1s) para git/gh, builds en Actions o PC.
 */
object GitHubConnector {
    const val API_BASE = "https://api.github.com"
    const val ACCEPT = "application/vnd.github+json"
    const val API_VERSION = "2022-11-28"

    /** Scopes mínimos del fine-grained PAT por uso. */
    val SCOPES_CONTENTS = listOf("contents:read", "contents:write")
    val SCOPES_ISSUES = listOf("issues:read", "issues:write")
    val SCOPES_PRS = listOf("pull-requests:read", "pull-requests:write")
    val SCOPES_ACTIONS = listOf("actions:read", "actions:write")

    /** Termux single-shot: un comando, <1s, cerrar proceso (anti-PPK). */
    const val TERMUX_SINGLE_SHOT_MAX_MS = 60_000L
}
