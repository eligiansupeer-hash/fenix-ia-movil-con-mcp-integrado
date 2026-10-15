package com.fenix.mobilemcp.delivery.mirror

/**
 * Secreto compartido del espejo entre nodos emparejados (g77/lake).
 * Se genera al emparejar y vive en kv (nunca en código, logs ni URL).
 * Valor por defecto SOLO para primer arranque; debe rotarse en F9.
 */
object MirrorAuth {
    const val HEADER = "X-Fenix-Mirror"
    const val DEFAULT_SECRET = "FENIX-ROTATE-ME"
}
