@file:Suppress("MagicNumber", "TooGenericExceptionCaught", "MaxLineLength")

package com.fenix.mobilemcp.hardware

import android.content.Context
import android.os.Process
import com.fenix.mobilemcp.telemetry.FenixBlackBoxRecorder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Serializable
enum class WorkloadProfile {
    /** Servidor MCP, túnel Cloudflare y monitoreo en segundo plano (Núcleos Efficiency LITTLE). */
    BACKGROUND_EFFICIENCY,

    /** Edición de código en vivo, previsualización y parseo AST (Equilibrado). */
    INTERACTIVE_IDE,

    /** Inferencia LLM local, compilación C/C++ y ejecución Python/JS pesada (Núcleos Performance/Prime). */
    HEAVY_COMPUTE_AI,
}

@Serializable
data class ExecutionAllocationReport(
    val workload: WorkloadProfile,
    val assignedCoreIndices: List<Int>,
    val allocatedThreads: Int,
    val availableRamBeforeMb: Long,
    val memoryPressureWarning: Boolean,
    val fallbackToCloudRecommended: Boolean,
)

/**
 * Orquestador de afinidad de núcleos de CPU y Guardián de RAM.
 * Selecciona qué grupo de núcleos (Efficiency vs Performance/Prime) atiende cada tarea
 * y protege la aplicación contra cierres por falta de memoria (Android LMKd).
 */
object CpuCoreAffinityManager {

    private val efficiencyThreadCounter = AtomicInteger(1)
    private val performanceThreadCounter = AtomicInteger(1)

    val efficiencyDispatcher: CoroutineDispatcher by lazy {
        val profile = HardwareCapabilitiesDetector.inspectHardware()
        val threads = profile.recommendedThreadsForBackground.coerceIn(1, 4)
        Executors.newFixedThreadPool(threads) { runnable ->
            Thread({
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                } catch (_: Exception) {
                }
                runnable.run()
            }, "fenix-eff-core-${efficiencyThreadCounter.getAndIncrement()}").apply {
                isDaemon = true
            }
        }.asCoroutineDispatcher()
    }

    val performanceDispatcher: CoroutineDispatcher by lazy {
        val profile = HardwareCapabilitiesDetector.inspectHardware()
        val threads = profile.recommendedThreadsForAi.coerceIn(2, 8)
        Executors.newFixedThreadPool(threads) { runnable ->
            Thread({
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT - 2)
                } catch (_: Exception) {
                }
                runnable.run()
            }, "fenix-perf-core-${performanceThreadCounter.getAndIncrement()}").apply {
                isDaemon = true
            }
        }.asCoroutineDispatcher()
    }

    /**
     * Evalúa el estado de la RAM y los núcleos antes de lanzar una tarea y retorna el reporte de asignación.
     */
    fun planWorkload(
        workload: WorkloadProfile,
        estimatedRamMb: Long = 128L,
        context: Context? = null,
    ): ExecutionAllocationReport {
        val hw = HardwareCapabilitiesDetector.inspectHardware(context)
        val assignedCores = when (workload) {
            WorkloadProfile.BACKGROUND_EFFICIENCY ->
                hw.efficiencyCoreIndices.ifEmpty { listOf(0) }

            WorkloadProfile.INTERACTIVE_IDE ->
                (hw.efficiencyCoreIndices.take(2) + hw.performanceCoreIndices.take(2)).ifEmpty { listOf(0, 1) }

            WorkloadProfile.HEAVY_COMPUTE_AI ->
                (hw.primeCoreIndices + hw.performanceCoreIndices).ifEmpty {
                    (0 until hw.totalCores).toList()
                }
        }

        val pressureWarning = hw.isLowMemoryState || (hw.availableRamMb - estimatedRamMb) < 550L
        val recommendCloud = pressureWarning && workload == WorkloadProfile.HEAVY_COMPUTE_AI

        if (pressureWarning) {
            // Solicitar recolección preventiva de basura en JVM si hay presión de memoria
            Runtime.getRuntime().gc()
            FenixBlackBoxRecorder.recordEvent(
                category = "HARDWARE_GUARD",
                action = "MEMORY_PRESSURE_DETECTED",
                component = "CpuCoreAffinityManager",
                details = "RAM libre: ${hw.availableRamMb} MB (Estimado tarea: $estimatedRamMb MB). Cloud offload recomendado=$recommendCloud",
            )
        }

        return ExecutionAllocationReport(
            workload = workload,
            assignedCoreIndices = assignedCores,
            allocatedThreads = assignedCores.size.coerceAtLeast(1),
            availableRamBeforeMb = hw.availableRamMb,
            memoryPressureWarning = pressureWarning,
            fallbackToCloudRecommended = recommendCloud,
        )
    }

    /**
     * Ejecuta una tarea intensiva en el pool de núcleos de alto rendimiento verificando la RAM previamente.
     */
    suspend fun <T> runOnPerformanceCores(
        estimatedRamMb: Long = 128L,
        context: Context? = null,
        block: suspend (ExecutionAllocationReport) -> T,
    ): T {
        val report = planWorkload(WorkloadProfile.HEAVY_COMPUTE_AI, estimatedRamMb, context)
        return withContext(performanceDispatcher) {
            block(report)
        }
    }

    /**
     * Ejecuta una tarea ligera o de mantenimiento en el pool de núcleos de eficiencia (ahorro de batería).
     */
    suspend fun <T> runOnEfficiencyCores(
        context: Context? = null,
        block: suspend (ExecutionAllocationReport) -> T,
    ): T {
        val report = planWorkload(WorkloadProfile.BACKGROUND_EFFICIENCY, 32L, context)
        return withContext(efficiencyDispatcher) {
            block(report)
        }
    }
}
