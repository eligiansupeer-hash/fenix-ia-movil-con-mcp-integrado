@file:Suppress("MagicNumber", "TooGenericExceptionCaught", "MaxLineLength")

package com.fenix.mobilemcp.hardware

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
enum class CoreClusterType {
    EFFICIENCY,
    PERFORMANCE,
    PRIME,
}

@Serializable
data class CpuCoreInfo(
    val coreIndex: Int,
    val maxFreqKhz: Long,
    val minFreqKhz: Long,
    val clusterType: CoreClusterType,
)

@Serializable
enum class ModelCompatibilityTier {
    OPTIMAL,
    TIGHT_FIT,
    EXCEEDS_RAM,
}

@Serializable
data class QuantizedModelRecommendation(
    val id: String,
    val name: String,
    val parameters: String,
    val quantization: String,
    val engineFormat: String,
    val requiredRamMb: Long,
    val downloadSizeMb: Long,
    val compatibility: ModelCompatibilityTier,
    val summary: String,
    val downloadUrl: String,
)

@Serializable
data class DeviceHardwareProfile(
    val deviceModel: String,
    val socHardware: String,
    val supportedAbis: List<String>,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val lowMemoryThresholdMb: Long,
    val isLowMemoryState: Boolean,
    val safeAiBudgetMb: Long,
    val totalCores: Int,
    val efficiencyCoreIndices: List<Int>,
    val performanceCoreIndices: List<Int>,
    val primeCoreIndices: List<Int>,
    val cores: List<CpuCoreInfo>,
    val cpuFeatures: List<String>,
    val recommendedThreadsForAi: Int,
    val recommendedThreadsForBackground: Int,
    val modelRecommendations: List<QuantizedModelRecommendation>,
)

/**
 * Detector inteligente de topología de CPU (ARM big.LITTLE / DynamIQ) y presupuesto de RAM.
 * Inspecciona `/sys/devices/system/cpu/` y `ActivityManager.MemoryInfo` sin requerir Root
 * para asignar núcleos óptimos y recomendar modelos cuantizados según el móvil portador.
 */
object HardwareCapabilitiesDetector {

    private val cachedCores: List<CpuCoreInfo> by lazy { detectCpuTopology() }
    private val cachedFeatures: List<String> by lazy { readCpuFeatures() }

    fun inspectHardware(context: Context? = null): DeviceHardwareProfile {
        val (totalRamMb, availRamMb, thresholdMb, isLowMem) = readMemoryStats(context)
        val cores = cachedCores
        val features = cachedFeatures

        val efficiencyIndices = cores.filter { it.clusterType == CoreClusterType.EFFICIENCY }.map { it.coreIndex }
        val performanceIndices = cores.filter { it.clusterType == CoreClusterType.PERFORMANCE }.map { it.coreIndex }
        val primeIndices = cores.filter { it.clusterType == CoreClusterType.PRIME }.map { it.coreIndex }

        val fastCoresCount = (performanceIndices.size + primeIndices.size).coerceAtLeast(1)
        val slowCoresCount = efficiencyIndices.size.coerceAtLeast(1)

        // Dejar un margen seguro de 900 MB libres para Android y el servidor MCP antes de LMKd
        val safeAiBudgetMb = (availRamMb - 900L).coerceAtLeast(256L)

        val recommendations = buildModelRecommendations(totalRamMb, availRamMb)

        return DeviceHardwareProfile(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            socHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            lowMemoryThresholdMb = thresholdMb,
            isLowMemoryState = isLowMem,
            safeAiBudgetMb = safeAiBudgetMb,
            totalCores = cores.size,
            efficiencyCoreIndices = efficiencyIndices,
            performanceCoreIndices = performanceIndices,
            primeCoreIndices = primeIndices,
            cores = cores,
            cpuFeatures = features,
            recommendedThreadsForAi = fastCoresCount,
            recommendedThreadsForBackground = slowCoresCount.coerceAtMost(2),
            modelRecommendations = recommendations,
        )
    }

    private fun readMemoryStats(context: Context?): List<Long> {
        if (context != null) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                if (am != null) {
                    val info = ActivityManager.MemoryInfo()
                    am.getMemoryInfo(info)
                    val totalMb = info.totalMem / (1024 * 1024)
                    val availMb = info.availMem / (1024 * 1024)
                    val threshMb = info.threshold / (1024 * 1024)
                    val lowFlag = if (info.lowMemory) 1L else 0L
                    return listOf(totalMb, availMb, threshMb, lowFlag)
                }
            } catch (_: Exception) {
                // Fallback a /proc/meminfo
            }
        }

        return try {
            val memInfoFile = File("/proc/meminfo")
            if (memInfoFile.exists()) {
                val lines = memInfoFile.readLines()
                var totalKb = 4_194_304L
                var availKb = 2_097_152L
                for (line in lines) {
                    if (line.startsWith("MemTotal:")) {
                        totalKb = line.replace(Regex("[^0-9]"), "").toLongOrNull() ?: totalKb
                    } else if (line.startsWith("MemAvailable:")) {
                        availKb = line.replace(Regex("[^0-9]"), "").toLongOrNull() ?: availKb
                    }
                }
                val totalMb = totalKb / 1024L
                val availMb = availKb / 1024L
                listOf(totalMb, availMb, 512L, if (availMb < 600L) 1L else 0L)
            } else {
                listOf(4096L, 2048L, 512L, 0L)
            }
        } catch (_: Exception) {
            listOf(4096L, 2048L, 512L, 0L)
        }
    }

    private operator fun List<Long>.component1(): Long = this[0]
    private operator fun List<Long>.component2(): Long = this[1]
    private operator fun List<Long>.component3(): Long = this[2]
    private operator fun List<Long>.component4(): Boolean = this[3] == 1L

    private fun detectCpuTopology(): List<CpuCoreInfo> {
        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val rawFreqs = mutableListOf<Triple<Int, Long, Long>>()

        for (i in 0 until coreCount) {
            val maxFreqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            val minFreqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq")
            val maxKhz = try {
                if (maxFreqFile.exists()) maxFreqFile.readText().trim().toLongOrNull() ?: 2_000_000L else 2_000_000L
            } catch (_: Exception) {
                2_000_000L
            }
            val minKhz = try {
                if (minFreqFile.exists()) minFreqFile.readText().trim().toLongOrNull() ?: 300_000L else 300_000L
            } catch (_: Exception) {
                300_000L
            }
            rawFreqs.add(Triple(i, maxKhz, minKhz))
        }

        val distinctMaxFreqs = rawFreqs.map { it.second }.distinct().sorted()
        val lowestMax = distinctMaxFreqs.firstOrNull() ?: 2_000_000L
        val highestMax = distinctMaxFreqs.lastOrNull() ?: 2_000_000L

        return rawFreqs.map { (idx, maxKhz, minKhz) ->
            val cluster = when {
                distinctMaxFreqs.size == 1 -> if (idx < coreCount / 2) CoreClusterType.EFFICIENCY else CoreClusterType.PERFORMANCE
                distinctMaxFreqs.size >= 3 && maxKhz == highestMax -> CoreClusterType.PRIME
                maxKhz == lowestMax -> CoreClusterType.EFFICIENCY
                else -> CoreClusterType.PERFORMANCE
            }
            CpuCoreInfo(
                coreIndex = idx,
                maxFreqKhz = maxKhz,
                minFreqKhz = minKhz,
                clusterType = cluster,
            )
        }
    }

    private fun readCpuFeatures(): List<String> {
        return try {
            val cpuInfo = File("/proc/cpuinfo")
            if (!cpuInfo.exists()) return listOf("arm64-neon")
            val featureLine = cpuInfo.useLines { lines ->
                lines.firstOrNull { it.startsWith("Features", ignoreCase = true) }
            }
            featureLine
                ?.substringAfter(":")
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?: listOf("asimd", "asimddp")
        } catch (_: Exception) {
            listOf("asimd")
        }
    }

    private fun buildModelRecommendations(totalRamMb: Long, availRamMb: Long): List<QuantizedModelRecommendation> {
        fun classify(requiredMb: Long): ModelCompatibilityTier {
            return when {
                requiredMb <= availRamMb - 700L && requiredMb <= totalRamMb * 0.45 -> ModelCompatibilityTier.OPTIMAL
                requiredMb <= availRamMb - 300L && requiredMb <= totalRamMb * 0.65 -> ModelCompatibilityTier.TIGHT_FIT
                else -> ModelCompatibilityTier.EXCEEDS_RAM
            }
        }

        return listOf(
            QuantizedModelRecommendation(
                id = "qwen2.5-coder-1.5b-q4_k_m",
                name = "Qwen 2.5 Coder 1.5B Instruct (Q4_K_M)",
                parameters = "1.5B",
                quantization = "Q4_K_M (GGUF)",
                engineFormat = "llama.cpp / GGUF",
                requiredRamMb = 1050L,
                downloadSizeMb = 986L,
                compatibility = classify(1050L),
                summary = "Ultraligero y rapidísimo. Especialista en programación, generación de scripts y herramientas MCP para cualquier móvil desde 4 GB de RAM.",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF",
            ),
            QuantizedModelRecommendation(
                id = "gemma-2-2b-it-mediapipe",
                name = "Gemma 2 2B Instruct (GPU/NPU Int4)",
                parameters = "2B",
                quantization = "Int4 (MediaPipe LiteRT)",
                engineFormat = "MediaPipe LLM SDK",
                requiredRamMb = 1550L,
                downloadSizeMb = 1350L,
                compatibility = classify(1550L),
                summary = "Optimizado para MediaPipe LLM Inference SDK con aceleración por GPU/NPU en Android.",
                downloadUrl = "https://huggingface.co/litert-community/Gemma2-2B-IT",
            ),
            QuantizedModelRecommendation(
                id = "deepseek-r1-distill-qwen-1.5b-q4",
                name = "DeepSeek-R1 Distill Qwen 1.5B (Q4_K_M)",
                parameters = "1.5B",
                quantization = "Q4_K_M (GGUF)",
                engineFormat = "llama.cpp / GGUF",
                requiredRamMb = 1150L,
                downloadSizeMb = 1020L,
                compatibility = classify(1150L),
                summary = "Modelo de razonamiento profundo (Chain-of-Thought) capaz de pensar paso a paso en matemáticas, lógica y depuración sin gastar RAM.",
                downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
            ),
            QuantizedModelRecommendation(
                id = "hermes-3-llama-3.2-3b-q4_k_m",
                name = "Hermes 3 Llama 3.2 3B (Q4_K_M)",
                parameters = "3B",
                quantization = "Q4_K_M (GGUF)",
                engineFormat = "llama.cpp / GGUF",
                requiredRamMb = 2250L,
                downloadSizeMb = 2020L,
                compatibility = classify(2250L),
                summary = "Especialista nativo en Function Calling, uso de herramientas MCP y respuestas estructuradas. Ideal para móviles de 6 GB a 8 GB+ de RAM.",
                downloadUrl = "https://huggingface.co/NousResearch/Hermes-3-Llama-3.2-3B-GGUF",
            ),
            QuantizedModelRecommendation(
                id = "qwen2.5-coder-7b-q4_k_m",
                name = "Qwen 2.5 Coder 7B Instruct (Q4_K_M)",
                parameters = "7B",
                quantization = "Q4_K_M (GGUF)",
                engineFormat = "llama.cpp / GGUF",
                requiredRamMb = 4800L,
                downloadSizeMb = 4450L,
                compatibility = classify(4800L),
                summary = "Nivel de estación de trabajo para teléfonos de gama alta (12 GB - 16 GB RAM). Calidad de código equiparable a modelos en la nube.",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-7B-Instruct-GGUF",
            ),
        )
    }
}
