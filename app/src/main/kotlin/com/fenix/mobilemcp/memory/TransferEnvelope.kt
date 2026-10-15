package com.fenix.mobilemcp.memory

import kotlinx.serialization.Serializable

/**
 * F3.2: sobre de transferencia SELECTIVA entre nodos (vía A2A Task).
 * El emisor arma el sobre (alcance explícito), el receptor lo importa
 * solo tras aprobación local. El filtro NER corre antes de transmitir.
 */
@Serializable
data class TransferEnvelope(
    val fromSlug: String,
    val toSlug: String,
    val scope: String,
    val items: List<TransferItem>,
    val createdAt: String,
)

@Serializable
data class TransferItem(
    val clave: String,
    val ambito: String,
    val valor: String,
    val categoria: String = "general",
    val tags: String = "",
)

/** Arma un sobre desde una búsqueda local (el llamante ya aprobó el alcance). */
fun buildEnvelope(
    fromSlug: String,
    toSlug: String,
    scope: String,
    found: List<Triple<String, String, String>>,
    nowIso: String,
): TransferEnvelope =
    TransferEnvelope(
        fromSlug = fromSlug,
        toSlug = toSlug,
        scope = scope,
        items = found.map { (clave, ambito, valor) -> TransferItem(clave, ambito, valor) },
        createdAt = nowIso,
    )
