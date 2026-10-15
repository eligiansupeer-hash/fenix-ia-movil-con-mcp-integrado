package com.fenix.mobilemcp.delivery.scheduler

import com.fenix.mobilemcp.memory.FenixMemoryDb
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F2.2 (rev auditoría): métricas en FenixMemoryDb/kv con TTL 14 días
 * (NO SharedPreferences). Clave por intento; expira_en purga solo.
 */
@Singleton
class TurnMetricsStore @Inject constructor(
    private val db: FenixMemoryDb,
) {
    data class Attempt(
        val timestamp: Long,
        val success: Boolean,
        val latencyMs: Long,
    )

    fun recordAttempt(success: Boolean, latencyMs: Long) {
        val now = Instant.now()
        val exp = now.plusSeconds(14L * 24 * 60 * 60).toString()
        db.kvGuardar(
            clave = "turn_attempt_${now.toEpochMilli()}",
            ambito = "turn_metrics",
            valor = "${if (success) 1 else 0}|$latencyMs",
            categoria = "turnos",
            expiraEn = exp,
        )
    }

    /** >95% éxito y p95 <2s sobre intentos RADAR no expirados (mínimo 20). */
    fun canPromoteToAuto(): Boolean {
        val rows = db.kvBuscar("turn_attempt_", 500)
        val mine = rows.filter { it.second == "turn_metrics" }
        if (mine.size < 20) return false
        val parsed = mine.mapNotNull { (_, _, v) ->
            val p = v.split("|")
            if (p.size != 2) null else Attempt(0L, p[0] == "1", p[1].toLongOrNull() ?: return@mapNotNull null)
        }
        if (parsed.isEmpty()) return false
        val rate = parsed.count { it.success }.toDouble() / parsed.size
        if (rate < 0.95) return false
        val lat = parsed.filter { it.success }.map { it.latencyMs }.sorted()
        if (lat.isEmpty()) return false
        return lat[((lat.size * 0.95).toInt().coerceAtMost(lat.size - 1))] < 2000
    }
}
