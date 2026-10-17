package com.fenix.mobilemcp.delivery.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.fenix.mobilemcp.delivery.monitor.DeliveryMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scheduler de turnos para delivery (Rappi, PedidosYa, Uber).
 * Dos modos:
 *  - RADAR (default): alerta cuando hay turnos disponibles + pre-relleno, dedo humano.
 *  - AUTO: toma el turno automáticamente (solo si midió latencia real < umbral durante 2 semanas).
 */
@Singleton
class TurnScheduler @Inject constructor(
    private val context: Context,
    private val deliveryMonitor: DeliveryMonitor,
    private val metricsStore: TurnMetricsStore,
) {

    private val TAG = "Fenix:TurnScheduler"
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    /** Modos del scheduler. */
    enum class Mode {
        RADAR,   // solo alerta + pre-relleno, usuario toca
        AUTO     // toma automática (solo tras validación 2 sem)
    }

    @Volatile
    private var mode: Mode = Mode.RADAR

    @Volatile
    private var desdeHoraWindow: String = "00:00"

    @Volatile
    private var hastaHoraWindow: String = "23:59"

    @Volatile
    private var isWatchingFlag: Boolean = false

    @Volatile
    private var scheduledJob: PendingIntent? = null

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Modo actual. */
    fun getMode(): Mode = mode

    fun getDesdeHora(): String = desdeHoraWindow

    fun getHastaHora(): String = hastaHoraWindow

    fun isWatching(): Boolean = isWatchingFlag

    /** Cambia modo (solo a AUTO si ya hay métrica de éxito > 95% en 2 semanas). */
    suspend fun setMode(newMode: Mode) {
        if (newMode == Mode.AUTO && !canAuto()) {
            Log.w("Fenix:TurnScheduler", "Auto denegado: no hay métrica válida 2 semanas")
            return
        }
        mode = newMode
        reschedule()
        Log.i("Fenix:TurnScheduler", "Modo cambiado a $mode")
    }

    /** Inicia scheduler (por defecto RADAR) registrando la ventana horaria solicitada. */
    fun start(desdeHora: String? = null, hastaHora: String? = null) {
        if (!desdeHora.isNullOrBlank()) desdeHoraWindow = desdeHora.trim()
        if (!hastaHora.isNullOrBlank()) hastaHoraWindow = hastaHora.trim()
        isWatchingFlag = true
        Log.i("Fenix:TurnScheduler", "Scheduler iniciado en modo $mode ($desdeHoraWindow - $hastaHoraWindow)")
        scheduleNextCheck()
    }

    /** Detiene scheduler. */
    fun stop() {
        isWatchingFlag = false
        cancelAlarm()
        Log.i("Fenix:TurnScheduler", "Scheduler detenido")
    }

    /** Registra resultado de intento de toma de turno (para métrica auto). */
    fun recordAttempt(success: Boolean, latencyMs: Long) {
        // TODO: persistir en bitácora para métrica 2 semanas
        Log.d("Fenix:TurnScheduler", "Attempt: success=$success, latency=${latencyMs}ms")
    }

    /** Verifica si puede pasar a AUTO (métrica 2 sem > 95% éxito, latencia p95 < 2s). */
    private suspend fun canAuto(): Boolean = metricsStore.canPromoteToAuto()

    /** Programa próxima comprobación (45-120s gaussiano). */
    private fun scheduleNextCheck() {
        cancelAlarm()
        val nextDelay = (45_000 + (Math.random() * 75_000).toLong()).toLong() // 45-120s gaussiano aprox
        val triggerAt = System.currentTimeMillis() + nextDelay
        val intent = Intent("com.fenix.mobilemcp.CHECK_TURNS")
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduledJob = pendingIntent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun cancelAlarm() {
        scheduledJob?.cancel()
        scheduledJob = null
    }

    /** Reprograma según modo actual. */
    fun reschedule() {
        if (mode == Mode.RADAR) {
            scheduleNextCheck()
        } else {
            cancelAlarm() // AUTO se gestiona por push + polling inteligente
        }
    }

    /** Cambia a modo AUTO solo si métrica válida (llamado desde auditoría/bitácora). */
    suspend fun promoteToAutoIfReady() {
        if (canAuto() && mode == Mode.RADAR) {
            setMode(Mode.AUTO)
        }
    }

    companion object {
        private const val TAG = "Fenix:TurnScheduler"
    }
}