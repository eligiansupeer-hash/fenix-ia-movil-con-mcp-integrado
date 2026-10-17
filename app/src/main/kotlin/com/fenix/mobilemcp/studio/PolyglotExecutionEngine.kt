@file:Suppress("MagicNumber", "TooGenericExceptionCaught", "LongMethod", "MaxLineLength")

package com.fenix.mobilemcp.studio

import android.content.Context
import com.fenix.mobilemcp.connectors.sandbox.ZeroAuthCloudSandboxClient
import com.fenix.mobilemcp.hardware.CpuCoreAffinityManager
import com.fenix.mobilemcp.mcp.skills.AndroidScriptEvaluator
import kotlinx.serialization.Serializable
import java.io.File
import java.util.Locale

@Serializable
data class PolyglotRunResponse(
    val success: Boolean,
    val language: String,
    val stdout: String,
    val stderr: String,
    val engineUsed: String,
    val assignedCores: List<Int>,
    val availableRamMb: Long,
    val executionTimeMs: Long,
)

/**
 * Motor Políglota de Ejecución y Compilación de Fénix Studio.
 * Coordina la ejecución local (QuickJS Sandbox / Binarios locales) en los núcleos
 * de alto rendimiento y la compilación en servidores virtuales gratuitos sin login
 * (Wandbox Clang/GCC/CPython, Piston API, Godbolt, H2.nexus) cuidando la memoria RAM.
 */
object PolyglotExecutionEngine {

    suspend fun executeCode(
        language: String,
        code: String,
        stdin: String = "",
        preferredProvider: String = "auto",
        context: Context? = null,
    ): PolyglotRunResponse {
        val startMs = System.currentTimeMillis()
        val langNorm = language.trim().lowercase(Locale.ROOT)

        return CpuCoreAffinityManager.runOnPerformanceCores(
            estimatedRamMb = 128L,
            context = context,
        ) { allocation ->
            val coresLabel = "Cores ${allocation.assignedCoreIndices.joinToString(",")} (RAM libre: ${allocation.availableRamBeforeMb} MB)"

            // 1. Si es JavaScript y no se forzó nube, evaluamos en el Sandbox Local si hay contexto
            if ((langNorm == "js" || langNorm == "javascript") && preferredProvider == "local" && context != null) {
                try {
                    val evaluator = AndroidScriptEvaluator(context)
                    val out = evaluator.evaluate(code, "{}")
                    val elapsed = System.currentTimeMillis() - startMs
                    val result = PolyglotRunResponse(
                        success = true,
                        language = langNorm,
                        stdout = out,
                        stderr = "",
                        engineUsed = "Local QuickJS Sandbox",
                        assignedCores = allocation.assignedCoreIndices,
                        availableRamMb = allocation.availableRamBeforeMb,
                        executionTimeMs = elapsed,
                    )
                    LiveWorkspaceBus.publishConsoleOutput(
                        StudioConsoleOutput(
                            title = "Ejecución JS Local",
                            stdout = out,
                            stderr = "",
                            engineUsed = result.engineUsed,
                            coresAssigned = coresLabel,
                            durationMs = elapsed,
                            success = true,
                        ),
                    )
                    return@runOnPerformanceCores result
                } catch (_: Exception) {
                    // Fallback a ejecución cloud si el script usa APIs de Node.js como console.log estándar
                }
            }

            // 2. Si es Python/Bash/C y existe binario local verificable sin presión de memoria
            if (!allocation.fallbackToCloudRecommended && preferredProvider == "local") {
                val localBinResult = tryLocalBinaryIfPresent(langNorm, code)
                if (localBinResult != null) {
                    val elapsed = System.currentTimeMillis() - startMs
                    LiveWorkspaceBus.publishConsoleOutput(
                        StudioConsoleOutput(
                            title = "Ejecución Local ($langNorm)",
                            stdout = localBinResult.first,
                            stderr = localBinResult.second,
                            engineUsed = "Fénix On-Device Native Runtime",
                            coresAssigned = coresLabel,
                            durationMs = elapsed,
                            success = localBinResult.third == 0,
                        ),
                    )
                    return@runOnPerformanceCores PolyglotRunResponse(
                        success = localBinResult.third == 0,
                        language = langNorm,
                        stdout = localBinResult.first,
                        stderr = localBinResult.second,
                        engineUsed = "Fénix On-Device Native Runtime",
                        assignedCores = allocation.assignedCoreIndices,
                        availableRamMb = allocation.availableRamBeforeMb,
                        executionTimeMs = elapsed,
                    )
                }
            }

            // 3. Ejecución/Compilación híbrida en VM Gratuita Zero-Auth (Wandbox / Piston / Godbolt / H2.nexus)
            val cloudRes = ZeroAuthCloudSandboxClient.executeCode(
                language = langNorm,
                code = code,
                stdin = stdin,
                preferredProvider = preferredProvider,
            )

            LiveWorkspaceBus.publishConsoleOutput(
                StudioConsoleOutput(
                    title = "Compilación / Ejecución ($langNorm)",
                    stdout = cloudRes.stdout,
                    stderr = cloudRes.stderr,
                    engineUsed = cloudRes.providerUsed,
                    coresAssigned = coresLabel,
                    durationMs = cloudRes.executionTimeMs,
                    success = cloudRes.success && cloudRes.exitCode == 0,
                ),
            )

            PolyglotRunResponse(
                success = cloudRes.success && cloudRes.exitCode == 0,
                language = langNorm,
                stdout = cloudRes.stdout,
                stderr = cloudRes.stderr,
                engineUsed = cloudRes.providerUsed,
                assignedCores = allocation.assignedCoreIndices,
                availableRamMb = allocation.availableRamBeforeMb,
                executionTimeMs = cloudRes.executionTimeMs,
            )
        }
    }

    private fun tryLocalBinaryIfPresent(lang: String, code: String): Triple<String, String, Int>? {
        return try {
            if (lang != "sh" && lang != "bash") return null
            val shFile = File("/system/bin/sh")
            if (!shFile.exists()) return null
            val proc = ProcessBuilder("/system/bin/sh", "-c", code)
                .redirectErrorStream(false)
                .start()
            val stdout = proc.inputStream.bufferedReader().readText()
            val stderr = proc.errorStream.bufferedReader().readText()
            val exit = proc.waitFor()
            Triple(stdout, stderr, exit)
        } catch (_: Exception) {
            null
        }
    }
}
