<!-- SACRED DOCUMENT — DO NOT MODIFY except for checkmarks ([ ] → [x]) and review findings. -->
<!-- Plans in docs/plans/ are PERMANENT artifacts. There are ZERO exceptions. -->

# Plan 66 — Gemini Web OAuth connector support (googleusercontent redirect namespace)

Enable Gemini Web custom MCP connectors to connect to the self-contained OAuth authorization server.
Empirically validated 2026-10-16 against a LIVE Gemini connector attempt (PC Firefox → public tunnel →
device): Gemini's Dynamic Client Registration posts THREE redirect URIs and all are rejected with
`invalid_redirect_uri` because the closed allowlist admits only Claude.ai and ChatGPT. The server log proves
both the rejection and the exact URIs (`OAuth client registration rejected redirect URI(s): …`, 11:01:50):

- `https://oauth-redirect-sandbox.googleusercontent.com/r/user_bound_custom-mcp-<user>-<mcp-host>`
- `https://oauth-redirect-test.googleusercontent.com/r/user_bound_custom-mcp-<user>-<mcp-host>`
- `https://oauth-redirect-sandbox.googleusercontent.com/a/user_bound_custom-mcp-<user>-<mcp-host>`

The tail embeds the connector slug (Google user id + MCP host with `.` replaced by `_`), so it changes with
every tunnel/connector and only the host + path namespace can be matched — the same treatment plan 65 gave
`chatgpt.com/connector/oauth/`. Gemini's flow otherwise mirrors Claude/ChatGPT: blank client id/secret (DCR),
public HTTPS URL required, on-device 2-digit approval, manage/revoke under Settings → Access.

**Scope boundary — files this plan may touch (and NO others):**
- `app/src/main/kotlin/com/fenix/mobilemcp/mcp/oauth/OAuthPolicy.kt`
- `app/src/test/kotlin/com/fenix/mobilemcp/mcp/oauth/OAuthPolicyTest.kt`
- `app/src/test/kotlin/com/fenix/mobilemcp/integration/OAuthFlowIntegrationTest.kt`

**Plan document handling:** `docs/plans/66_google_oauth_connector_support_20261016111500.md` is a tracked,
PERMANENT artifact and MUST be committed as a standalone `docs(plans): add plan 66` commit (repo convention).

---

## User Story 1 — Admit the Google redirect namespace in the OAuth allowlist

**Why:** The redirect-URI allowlist is the security boundary for Dynamic Client Registration. It must admit
Google's two documented-by-observation `*.googleusercontent.com` callback hosts (exact match, `https` only,
path under `/r/` or `/a/`) while keeping the closed-set guarantee so deceptive hosts/paths/schemes stay rejected.
On-device 2-digit approval remains the real authorization gate (unchanged).

**Acceptance criteria:**
- [ ] `OAuthPolicy.isAllowedRedirectUri` returns `true` for the three observed URIs (sandbox `/r`, test `/r`, sandbox `/a`).
- [ ] Deceptive host (`…googleusercontent.com.evil.example`), userinfo-authority (`…@evil.com`), wrong path
  (`/evil/…`), and `http` scheme on the Google hosts are rejected.
- [ ] Existing Claude.ai, ChatGPT, and `http` loopback behavior is unchanged.
- [ ] Registering (DCR) with a Google redirect URI succeeds at the HTTP layer (returns `201`).

### Task 1.1 — Add Google constants and extend the allowlist predicate

**Action — modify** `OAuthPolicy.kt`: insert the Google constants + KDoc after `CHATGPT_REDIRECT_PATH_PREFIX`,
add the `isGoogleConnectorCallback` branch, extend the return and the function KDoc.

```kotlin
    /**
     * Google (Gemini Web custom MCP connectors) uses per-connector OAuth callback URLs under these
     * exact hosts. Observed live 2026-10-16 from a real Gemini connector attempt: ... (see file).
     */
    private const val GOOGLE_REDIRECT_HOST_SANDBOX = "oauth-redirect-sandbox.googleusercontent.com"
    private const val GOOGLE_REDIRECT_HOST_TEST = "oauth-redirect-test.googleusercontent.com"
    private val GOOGLE_REDIRECT_HOSTS = setOf(GOOGLE_REDIRECT_HOST_SANDBOX, GOOGLE_REDIRECT_HOST_TEST)
    private const val GOOGLE_REDIRECT_PATH_PREFIX_R = "/r/"
    private const val GOOGLE_REDIRECT_PATH_PREFIX_A = "/a/"
```

```kotlin
        val isGoogleConnectorCallback =
            parsed != null &&
                parsed.scheme == "https" &&
                host in GOOGLE_REDIRECT_HOSTS &&
                (parsed.path?.startsWith(GOOGLE_REDIRECT_PATH_PREFIX_R) == true ||
                    parsed.path?.startsWith(GOOGLE_REDIRECT_PATH_PREFIX_A) == true)
```

`return isChatGptConnectorCallback || isGoogleConnectorCallback || isLoopback`

**Definition of Done:**
- [ ] Constants, KDoc, predicate branch, return, and function KDoc updated; no other member changed.
- [ ] `make lint` clean (no suppressions; watch the KDoc/EOL-comment rule from plan 65).

### Task 1.2 — Unit tests for the extended allowlist

**File:** `OAuthPolicyTest.kt` — extend `allowsAllowlistedAndLoopback` (3 assertTrue) and
`rejectsDeceptiveHostedCallbacks` (4 assertFalse). Existing assertions untouched.

**Definition of Done:**
- [ ] New assertions added; full JVM suite green (`make test-unit`; `test-e2e` container-gated/out of scope).

### Task 1.3 — Integration test: DCR accepts a Google redirect URI

**File:** `OAuthFlowIntegrationTest.kt` — add `registerAcceptsGoogleRedirect` mirroring
`registerAcceptsChatGptRedirect` (body with the sandbox `/r/` URI; assert `201` + echo).

**Definition of Done:**
- [ ] Test added in file style; OAuth test classes green.

## User Story 2 — Finalize (PR) and ground-up verification

**Why:** Ship the change and re-verify from scratch (plan-65 ritual).

**Acceptance criteria:**
- [ ] Feature branch `feat/google-oauth-connector`, ordered commits (plan doc alone first), PR opened.
- [ ] `git diff main..HEAD --stat` shows ONLY the three in-scope files + this plan doc.
- [ ] Ground-up re-read of every changed hunk against this plan: zero discrepancies.
