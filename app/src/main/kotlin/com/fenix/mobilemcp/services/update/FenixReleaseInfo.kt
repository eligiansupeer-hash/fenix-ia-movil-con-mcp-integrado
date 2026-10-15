package com.fenix.mobilemcp.services.update

/**
 * Central de identidad de release Fénix. El owner es la cuenta real del fork (F9)
 * y jamás contiene datos sensibles de dispositivos (ver checklist sanitización).
 */
object FenixReleaseInfo {
    const val GITHUB_OWNER = "eligiansupeer-hash"
    const val GITHUB_REPO = "fenix-mobile-mcp"
    const val APP_NAME = "Fenix MCP Android"
}
