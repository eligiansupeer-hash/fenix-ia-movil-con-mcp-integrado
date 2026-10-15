package com.fenix.mobilemcp.providers

/**
 * F8: proveedores LLM externos. Reglas duras:
 * - Codex: UN auth chain = UNA copia viva, en UN solo móvil con login propio
 *   (device-auth). Refresh race mata la cadena en ambos nodos. Quota 5h+semanal.
 * - OpenCode free: SOLO dentro del cliente oficial (403 fuera). El MCP orquesta
 *   (abre sesión, pasa tarea, lee resultado), no suplanta ni spoofea fingerprint.
 * - Rotación 3x por proveedor + discovery sin hardcodear + auto-alta validada.
 * - Tier free puede entrenar con tus datos: NER local antes de enviar.
 */
object ProviderPolicy {
    const val CODEX_AUTH_PATH = "\$CODEX_HOME/auth.json"
    const val MAX_KEYS_PER_PROVIDER = 3
    const val AUTH_FAILURES_DISABLE_AT = 3
}
