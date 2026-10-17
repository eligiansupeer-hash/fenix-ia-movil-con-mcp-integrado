package com.fenix.mobilemcp.connectors.github

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    private val slugRegex = Regex("^[A-Za-z0-9_.-]{1,100}$")

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000L
                connectTimeoutMillis = 8_000L
            }
        }
    }

    suspend fun getAuthenticatedUser(token: String): String = withContext(Dispatchers.IO) {
        require(token.isNotBlank()) { "GitHub token no puede estar vacío" }
        val response = httpClient.get("$API_BASE/user") {
            header(HttpHeaders.Accept, ACCEPT)
            header("X-GitHub-Api-Version", API_VERSION)
            header(HttpHeaders.Authorization, "Bearer ${token.trim()}")
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("GitHub HTTP ${response.status.value}: $body")
        }
        body
    }

    suspend fun getRepository(owner: String, repo: String, token: String? = null): String = withContext(Dispatchers.IO) {
        require(slugRegex.matches(owner.trim())) { "Owner inválido: $owner" }
        require(slugRegex.matches(repo.trim())) { "Repo inválido: $repo" }
        val response = httpClient.get("$API_BASE/repos/${owner.trim()}/${repo.trim()}") {
            header(HttpHeaders.Accept, ACCEPT)
            header("X-GitHub-Api-Version", API_VERSION)
            if (!token.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer ${token.trim()}")
            }
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("GitHub HTTP ${response.status.value}: $body")
        }
        body
    }

    suspend fun listIssues(
        owner: String,
        repo: String,
        state: String = "open",
        perPage: Int = 10,
        token: String? = null,
    ): String = withContext(Dispatchers.IO) {
        require(slugRegex.matches(owner.trim())) { "Owner inválido: $owner" }
        require(slugRegex.matches(repo.trim())) { "Repo inválido: $repo" }
        val response = httpClient.get("$API_BASE/repos/${owner.trim()}/${repo.trim()}/issues") {
            header(HttpHeaders.Accept, ACCEPT)
            header("X-GitHub-Api-Version", API_VERSION)
            if (!token.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer ${token.trim()}")
            }
            parameter("state", state)
            parameter("per_page", perPage.coerceIn(1, 50))
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("GitHub HTTP ${response.status.value}: $body")
        }
        body
    }
}
