package com.fenix.mobilemcp.connectors.entregas

import com.fenix.mobilemcp.delivery.zones.ZoneEntry
import com.fenix.mobilemcp.delivery.zones.ZoneMatcher
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * F5.5: Fenix Entregas first-party (PWA React + Supabase, repo local).
 * Sin scraping: Realtime sobre notification_outbox (kinds pool_order,
 * contract_request) + lectura de orders (address/amount) → cruce de zonas.
 * Push vía push_subscriptions (RLS por usuario).
 */
object EntregasFirstParty {
    const val TABLE_OUTBOX = "notification_outbox"
    const val TABLE_ORDERS = "orders"
    const val TABLE_PUSH_SUBS = "push_subscriptions"
    const val KIND_POOL_ORDER = "pool_order"
    const val KIND_CONTRACT_REQUEST = "contract_request"

    /** Canales Realtime a suscribir (uno por tabla). */
    val REALTIME_CHANNELS = listOf(TABLE_OUTBOX, TABLE_ORDERS)

    val VALID_KINDS = setOf(KIND_POOL_ORDER, KIND_CONTRACT_REQUEST)

    /** Evalúa una orden de entrega first-party cruzando su dirección contra el índice de zonas. */
    fun evaluateOrder(
        address: String,
        amount: Double,
        kind: String,
        matcher: ZoneMatcher,
        zones: List<ZoneEntry>,
    ): JsonObject {
        require(address.isNotBlank()) { "La dirección de la orden no puede estar vacía" }
        require(!matcher.isRawCoordinates(address)) {
            "Coordenadas crudas no admitidas en orden de entrega; se requiere dirección textual"
        }
        require(kind in VALID_KINDS) {
            "Kind '$kind' inválido; valores permitidos: $VALID_KINDS"
        }
        val hits = matcher.match(address, zones)
        return buildJsonObject {
            put("table_orders", TABLE_ORDERS)
            put("table_outbox", TABLE_OUTBOX)
            put("kind", kind)
            put("address", address)
            put("amount", amount)
            put("peligrosa", hits.isNotEmpty())
            put(
                "realtime_channels",
                buildJsonArray {
                    REALTIME_CHANNELS.forEach { ch -> add(kotlinx.serialization.json.JsonPrimitive(ch)) }
                },
            )
            put(
                "hits",
                buildJsonArray {
                    for (h in hits) {
                        add(
                            buildJsonObject {
                                put("zone_id", h.zoneId)
                                put("departamento", h.department)
                                put("nombre", h.name)
                                put("extrema_noche", h.nightExtreme)
                            },
                        )
                    }
                },
            )
        }
    }
}
