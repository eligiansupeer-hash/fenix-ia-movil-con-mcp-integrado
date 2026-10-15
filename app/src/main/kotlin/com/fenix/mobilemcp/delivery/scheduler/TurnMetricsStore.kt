package com.fenix.mobilemcp.delivery.scheduler

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Persistencia local de métricas del TurnScheduler (14 días).
 * Guarda en SharedPreferences con Gson (ligero, sin Room para mantenerlo simple).
 */
@Singleton
class TurnMetricsStore @Inject constructor(
    private val context: Context,
) {

    private val PREFS_NAME = "turn_metrics"
    private val KEY_ATTEMPTS = "attempts"
    private val gson = Gson()
    private val typeToken = object : TypeToken<List<Attempt>>() {}.type

    data class Attempt(
        val timestamp: Long,
        val success: Boolean,
        val latencyMs: Long,
        val mode: String, // "RADAR" o "AUTO"
        val zoneId: String? = null,
    )

    /** Guarda un intento de toma de turno. */
    suspend fun recordAttempt(success: Boolean, latencyMs: Long, mode: String, zoneId: String? = null) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("turn_metrics", Context.MODE_PRIVATE)
            val current = getAttemptsInternal(prefs)
            val attempt = Attempt(
                timestamp = System.currentTimeMillis(),
                success = success,
                latencyMs = latencyMs,
                mode = mode,
                zoneId = zoneId,
            )
            val updated = (current + Attempt(timestamp = System.currentTimeMillis(), success, latencyMs, mode, zoneId))
            // Mantener solo últimos 14 días
            val cutoff = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000
            val filtered = updated.filter { it.timestamp > cutoff }
            saveAttempts(prefs, filtered)
        }
    }

    private suspend fun getAttemptsInternal(prefs: android.content.SharedPreferences): List<Attempt> {
        val json = prefs.getString(KEY_ATTEMPTS, "[]") ?: "[]"
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun saveAttempts(prefs: android.content.SharedPreferences, attempts: List<Attempt>) {
        val json = gson.toJson(attempts)
        prefs.edit().putString(KEY_ATTEMPTS, json).apply()
    }

    /** Evalúa si se puede promocionar a AUTO (14 días, >95% éxito, latencia p95 < 2s). */
    suspend fun canPromoteToAuto(): Boolean {
        val attempts = getAllAttempts().filter { it.mode == "RADAR" }
        if (attempts.size < 20) return false // mínimo de muestras
        val successes = attempts.count { it.success }
        val rate = successes.toDouble() / attempts.size
        if (rate < 0.95) return false
        val latencies = attempts.filter { it.success }.map { it.latencyMs }.sorted()
        val p95 = latencies[((latencies.size * 0.95).toInt().coerceAtMost(latencies.size - 1))]
        return p95 < 2000 // 2 segundos p95
    }

    private suspend fun getAllAttempts(): List<Attempt> {
        val prefs = context.getSharedPreferences("turn_metrics", Context.MODE_PRIVATE)
        return getAttemptsInternal(prefs)
    }
}