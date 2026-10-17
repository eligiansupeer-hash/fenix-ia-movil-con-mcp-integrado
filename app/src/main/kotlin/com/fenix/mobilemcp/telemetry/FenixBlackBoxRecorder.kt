package com.fenix.mobilemcp.telemetry

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Caja Negra Permanente de Fénix Mobile MCP.
 *
 * Registra eventos de ciclo de vida, llamadas MCP, autenticación OAuth, tráfico HTTP,
 * cambios en el servicio de accesibilidad y excepciones críticas.
 *
 * Arquitectura:
 * - Buffer circular en RAM (ConcurrentLinkedDeque) para acceso instantáneo a costo 0 de I/O.
 * - Registro persistente rotativo en formato JSONL en almacenamiento privado de la app.
 * - Expuesto a IAs conectadas mediante la herramienta MCP `android_blackbox_diagnostics`.
 */
object FenixBlackBoxRecorder {

    private const val TAG = "FenixBlackBox"
    private const val MAX_RAM_EVENTS = 250
    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024 // 2 MB rotativo
    private const val LOG_FILE_NAME = "fenix_blackbox.jsonl"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val eventQueue = ConcurrentLinkedDeque<BlackBoxEvent>()
    private val totalRecordedEvents = AtomicLong(0)
    private val toolCallSuccessCount = AtomicLong(0)
    private val toolCallFailureCount = AtomicLong(0)

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        recordEvent(
            category = "SYSTEM",
            action = "RECORDER_INITIALIZED",
            component = "FenixBlackBoxRecorder",
            details = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) - Modelo: ${Build.MODEL}",
            success = true
        )
        Log.i(TAG, "Caja Negra permanente inicializada.")
    }

    @Serializable
    data class BlackBoxEvent(
        val id: Long,
        val timestamp: String,
        val epochMs: Long,
        val category: String, // TOOL, HTTP, OAUTH, ACCESSIBILITY, SYSTEM, ERROR
        val action: String,
        val component: String,
        val details: String,
        val durationMs: Long? = null,
        val success: Boolean = true,
        val errorCode: String? = null
    )

    fun recordEvent(
        category: String,
        action: String,
        component: String,
        details: String,
        durationMs: Long? = null,
        success: Boolean = true,
        errorCode: String? = null
    ) {
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val isoTimestamp = dateFormat.format(Date(now))
        val eventId = totalRecordedEvents.incrementAndGet()

        if (category == "TOOL") {
            if (success) toolCallSuccessCount.incrementAndGet() else toolCallFailureCount.incrementAndGet()
        }

        val event = BlackBoxEvent(
            id = eventId,
            timestamp = isoTimestamp,
            epochMs = now,
            category = category,
            action = action,
            component = component,
            details = details.take(1000), // Evitar payloads excesivos
            durationMs = durationMs,
            success = success,
            errorCode = errorCode
        )

        // Buffer en memoria
        eventQueue.addLast(event)
        while (eventQueue.size > MAX_RAM_EVENTS) {
            eventQueue.pollFirst()
        }

        // Persistir en disco en segundo plano (archivo rotativo)
        persistEventToFile(event)
    }

    fun recordToolExecution(
        toolName: String,
        durationMs: Long,
        success: Boolean,
        errorMessage: String? = null
    ) {
        recordEvent(
            category = "TOOL",
            action = "CALL_TOOL",
            component = toolName,
            details = if (success) "Herramienta ejecutada con éxito" else (errorMessage ?: "Error desconocido"),
            durationMs = durationMs,
            success = success,
            errorCode = if (!success) "TOOL_ERROR" else null
        )
    }

    fun recordHttp(
        method: String,
        path: String,
        statusCode: Int,
        clientIp: String? = null
    ) {
        val success = statusCode < 400
        recordEvent(
            category = "HTTP",
            action = "$method $path",
            component = "KtorServer",
            details = "Status: $statusCode | Client: ${clientIp ?: "unknown"}",
            success = success,
            errorCode = if (!success) "HTTP_$statusCode" else null
        )
    }

    fun recordOAuth(
        action: String,
        details: String,
        success: Boolean
    ) {
        recordEvent(
            category = "OAUTH",
            action = action,
            component = "OAuthPolicy",
            details = details,
            success = success,
            errorCode = if (!success) "OAUTH_REJECTED" else null
        )
    }

    fun recordAccessibility(
        action: String,
        details: String,
        success: Boolean = true
    ) {
        recordEvent(
            category = "ACCESSIBILITY",
            action = action,
            component = "McpAccessibilityService",
            details = details,
            success = success,
            errorCode = if (!success) "A11Y_ERROR" else null
        )
    }

    fun recordError(
        component: String,
        throwable: Throwable
    ) {
        recordEvent(
            category = "ERROR",
            action = "EXCEPTION",
            component = component,
            details = "${throwable::class.java.simpleName}: ${throwable.message ?: "Sin mensaje"}",
            success = false,
            errorCode = throwable::class.java.simpleName
        )
    }

    fun getSummary(): JsonObject {
        val runtime = Runtime.getRuntime()
        val totalMem = runtime.totalMemory() / (1024 * 1024)
        val freeMem = runtime.freeMemory() / (1024 * 1024)
        val usedMem = totalMem - freeMem

        val totalTools = toolCallSuccessCount.get() + toolCallFailureCount.get()
        val successRate = if (totalTools > 0) {
            String.format(Locale.US, "%.1f%%", (toolCallSuccessCount.get().toDouble() / totalTools) * 100)
        } else {
            "N/A"
        }

        val recentFailures = eventQueue.filter { !it.success }.takeLast(5).map {
            "${it.timestamp} [${it.component}] ${it.details}"
        }

        return buildJsonObject {
            put("total_recorded_events", totalRecordedEvents.get())
            put("buffered_events_in_ram", eventQueue.size)
            put("tool_calls_total", totalTools)
            put("tool_calls_success", toolCallSuccessCount.get())
            put("tool_calls_failure", toolCallFailureCount.get())
            put("tool_success_rate", successRate)
            put("jvm_memory_used_mb", usedMem)
            put("jvm_memory_free_mb", freeMem)
            put("jvm_memory_total_mb", totalMem)
            put("recent_failures_count", recentFailures.size)
        }
    }

    fun getRecentEvents(limit: Int = 20, category: String? = null): List<BlackBoxEvent> {
        val clampedLimit = limit.coerceIn(1, 100)
        val stream = if (category.isNullOrBlank() || category.equals("ALL", ignoreCase = true)) {
            eventQueue.toList()
        } else {
            eventQueue.filter { it.category.equals(category, ignoreCase = true) }
        }
        return stream.takeLast(clampedLimit).reversed()
    }

    fun getToolErrors(limit: Int = 20): List<BlackBoxEvent> {
        val clampedLimit = limit.coerceIn(1, 100)
        return eventQueue.filter { it.category == "TOOL" && !it.success }
            .takeLast(clampedLimit)
            .reversed()
    }

    fun clearBuffer(): Boolean {
        eventQueue.clear()
        recordEvent(
            category = "SYSTEM",
            action = "BUFFER_CLEARED",
            component = "FenixBlackBoxRecorder",
            details = "Buffer en RAM limpiado a solicitud del usuario o IA",
            success = true
        )
        return true
    }

    private fun persistEventToFile(event: BlackBoxEvent) {
        val ctx = appContext ?: return
        try {
            val logDir = File(ctx.filesDir, "blackbox")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val logFile = File(logDir, LOG_FILE_NAME)

            // Rotación si supera el tamaño límite
            if (logFile.exists() && logFile.length() > MAX_FILE_SIZE_BYTES) {
                val oldFile = File(logDir, "$LOG_FILE_NAME.old")
                if (oldFile.exists()) oldFile.delete()
                logFile.renameTo(oldFile)
            }

            val line = json.encodeToString(event) + "\n"
            FileOutputStream(logFile, true).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    writer.write(line)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error persistiendo evento en caja negra: ${e.message}")
        }
    }
}

