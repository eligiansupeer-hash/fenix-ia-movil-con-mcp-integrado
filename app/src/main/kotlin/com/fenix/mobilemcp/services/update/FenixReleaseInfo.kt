package com.fenix.mobilemcp.services.update

/**
 * Central de identidad de release Fénix. El owner se define en F9 (release)
 * y jamás contiene datos sensibles de dispositivos (ver checklist sanitización).
 */
object FenixReleaseInfo {
    const val GITHUB_OWNER = "FENIX_GITHUB_OWNER"
    const val GITHUB_REPO = "fenix-mobile-mcp"
    const val APP_NAME = "Fénix Mobile MCP"
}
