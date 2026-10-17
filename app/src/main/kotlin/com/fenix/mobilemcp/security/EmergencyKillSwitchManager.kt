@file:Suppress("MagicNumber", "TooGenericExceptionCaught", "MaxLineLength")

package com.fenix.mobilemcp.security

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import com.fenix.mobilemcp.services.accessibility.McpAccessibilityService
import com.fenix.mobilemcp.services.mcp.McpServerService
import com.fenix.mobilemcp.studio.LiveWorkspaceBus
import com.fenix.mobilemcp.telemetry.FenixBlackBoxRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Interruptor Físico de Emergencia (Hardware Kill-Switch / Botón de Pánico).
 *
 * Permite al usuario abortar instantáneamente TODOS los procesos, bucles infinitos de agentes IA,
 * gestos táctiles en pantalla, compilaciones y conexiones de servidor usando los botones físicos
 * del teléfono:
 * 1. Presionando simultáneamente [Volumen Arriba + Volumen Abajo], o
 * 2. Presionando 3 veces seguidas [Volumen Abajo] en menos de 1.2 segundos.
 */
object EmergencyKillSwitchManager {

    private const val TAG = "MCP:EmergencyKillSwitch"
    private const val TRIPLE_CLICK_WINDOW_MS = 1250L
    private const val COOLDOWN_AFTER_HALT_MS = 3000L

    private val haltedFlag = AtomicBoolean(false)
    private val lastHaltTimestampMs = AtomicLong(0L)

    private val _isEmergencyHaltedFlow = MutableStateFlow(false)
    val isEmergencyHaltedFlow: StateFlow<Boolean> = _isEmergencyHaltedFlow.asStateFlow()

    private val activeToolJobs = ConcurrentHashMap<Long, Job>()
    private val jobCounter = AtomicLong(1L)

    @Volatile
    private var volumeUpPressed = false

    @Volatile
    private var volumeDownPressed = false

    private val volumeDownTapTimestamps = ArrayDeque<Long>(4)

    /**
     * Registra un Job de herramienta en curso para poder cancelarlo en el acto si se activa el Kill-Switch.
     */
    fun registerActiveToolJob(job: Job): Long {
        if (isCurrentlyBlocking()) {
            job.cancel(CancellationException("Abortado por Interruptor de Emergencia Físico"))
        }
        val id = jobCounter.getAndIncrement()
        activeToolJobs[id] = job
        return id
    }

    fun unregisterActiveToolJob(id: Long) {
        activeToolJobs.remove(id)
    }

    /**
     * Comprueba si el bloqueo de emergencia está activo (dura 3 segundos automáticos tras el corte
     * para rechazar ráfagas residuales en vuelo, o hasta que el usuario reanude el servidor).
     */
    fun isCurrentlyBlocking(): Boolean {
        if (!haltedFlag.get()) return false
        val elapsed = System.currentTimeMillis() - lastHaltTimestampMs.get()
        if (elapsed > COOLDOWN_AFTER_HALT_MS) {
            haltedFlag.set(false)
            _isEmergencyHaltedFlow.value = false
            return false
        }
        return true
    }

    fun resetEmergencyState() {
        haltedFlag.set(false)
        _isEmergencyHaltedFlow.value = false
    }

    /**
     * Procesa eventos de los botones físicos de volumen desde `McpAccessibilityService.onKeyEvent`.
     * Devuelve `true` si el evento disparó el corte de emergencia.
     */
    @Synchronized
    fun onPhysicalVolumeKeyEvent(
        context: Context,
        keyCode: Int,
        isActionDown: Boolean,
        eventTimeMs: Long,
    ): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            volumeUpPressed = isActionDown
        } else if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeDownPressed = isActionDown
            if (isActionDown) {
                while (volumeDownTapTimestamps.isNotEmpty() &&
                    eventTimeMs - volumeDownTapTimestamps.first() > TRIPLE_CLICK_WINDOW_MS
                ) {
                    volumeDownTapTimestamps.removeFirst()
                }
                volumeDownTapTimestamps.addLast(eventTimeMs)

                if (volumeDownTapTimestamps.size >= 3) {
                    volumeDownTapTimestamps.clear()
                    triggerEmergencyHalt(context, "Triple pulsación rápida de Volumen Abajo")
                    return true
                }
            }
        }

        // Combinación simultánea: Volumen Arriba + Volumen Abajo presionados a la vez
        if (volumeUpPressed && volumeDownPressed) {
            volumeUpPressed = false
            volumeDownPressed = false
            volumeDownTapTimestamps.clear()
            triggerEmergencyHalt(context, "Combinación física Volumen Arriba + Volumen Abajo")
            return true
        }

        return false
    }

    /**
     * Ejecuta la parada total de emergencia:
     * 1. Bloquea nuevas ejecuciones y cancela todos los Jobs de herramientas MCP en vuelo.
     * 2. Oculta overlays y limpia cachés de accesibilidad.
     * 3. Detiene inmediatamente `McpServerService` y el túnel activo.
     * 4. Mata subprocesos hijos colgados y solicita liberación de memoria.
     * 5. Emite vibración táctil y alerta visual en pantalla.
     */
    fun triggerEmergencyHalt(context: Context, triggerReason: String = "Botón de Emergencia") {
        val now = System.currentTimeMillis()
        lastHaltTimestampMs.set(now)
        haltedFlag.set(true)
        _isEmergencyHaltedFlow.value = true

        val cancelledCount = activeToolJobs.size
        activeToolJobs.values.forEach { job ->
            runCatching {
                job.cancel(CancellationException("CORTE DE EMERGENCIA FÍSICO: $triggerReason"))
            }
        }
        activeToolJobs.clear()

        // Ocultar indicador de control y limpiar caché de accesibilidad
        runCatching {
            McpAccessibilityService.hideToolCallIndicator()
            McpAccessibilityService.instance?.clearFrameworkNodeCache()
        }

        // Detener el servidor MCP y el túnel de inmediato
        runCatching {
            val stopIntent = Intent(context, McpServerService::class.java).apply {
                action = McpServerService.ACTION_STOP
            }
            context.stopService(stopIntent)
            McpServerService.instance?.stopSelf()
        }

        // Limpiar consola en vivo y liberar memoria
        runCatching {
            LiveWorkspaceBus.clearConsole()
            Runtime.getRuntime().gc()
        }

        FenixBlackBoxRecorder.recordEvent(
            category = "SECURITY_EMERGENCY",
            action = "HARDWARE_KILL_SWITCH_TRIGGERED",
            component = "EmergencyKillSwitchManager",
            details = "Motivo: $triggerReason | Tareas canceladas en el acto: $cancelledCount",
        )

        Log.e(TAG, "🛑 CORTE DE EMERGENCIA EJECUTADO ($triggerReason). Tareas canceladas: $cancelledCount")

        // Vibración de confirmación física y aviso visual en el hilo principal
        Handler(Looper.getMainLooper()).post {
            vibrateEmergencyPattern(context)
            Toast.makeText(
                context.applicationContext,
                "🛑 CORTE DE EMERGENCIA ACTIVADO ($triggerReason): Todos los procesos y agentes fueron detenidos.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun vibrateEmergencyPattern(context: Context) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0L, 180L, 80L, 250L), -1),
            )
        } catch (_: Exception) {
        }
    }
}

