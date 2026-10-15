package com.fenix.mobilemcp.connectors.entregas

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
}
