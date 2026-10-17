@file:Suppress("MagicNumber", "TooGenericExceptionCaught", "LongMethod", "MaxLineLength")

package com.fenix.mobilemcp.connectors.sandbox

import com.fenix.mobilemcp.telemetry.FenixBlackBoxRecorder
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class CloudExecutionResult(
    val success: Boolean,
    val providerUsed: String,
    val language: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val executionTimeMs: Long,
)

/**
 * Cliente unificado de máquinas virtuales y sandboxes de compilación/ejecución en la nube
 * 100% gratuitos, sin login y sin tarjeta (Wandbox Clang/GCC/Python/Rust/Go, Piston API,
 * Godbolt Compiler Explorer y servidores virtuales H2.nexus / Custom).
 */
object ZeroAuthCloudSandboxClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(12, TimeUnit.SECONDS)
                    readTimeout(25, TimeUnit.SECONDS)
                }
            }
        }
    }

    /**
     * Ejecuta o compila código fuente utilizando el proveedor indicado o seleccionando el mejor
     * servidor gratuito sin login con fallback automático (Wandbox -> Godbolt -> Piston/Custom).
     */
    suspend fun executeCode(
        language: String,
        code: String,
        stdin: String = "",
        preferredProvider: String = "auto",
        customEndpointUrl: String? = null,
    ): CloudExecutionResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val langNorm = language.trim().lowercase(Locale.ROOT)

        try {
            if (!customEndpointUrl.isNullOrBlank()) {
                val customRes = executeOnCustomOrPiston(customEndpointUrl, langNorm, code, stdin, startMs)
                if (customRes.success || preferredProvider == "custom") return@withContext customRes
            }

            when (preferredProvider.lowercase(Locale.ROOT)) {
                "piston" -> {
                    val pistonUrl = customEndpointUrl?.takeIf { it.isNotBlank() } ?: "https://emkc.org/api/v2/piston/execute"
                    val res = executeOnCustomOrPiston(pistonUrl, langNorm, code, stdin, startMs)
                    if (res.success) return@withContext res
                    // Fallback transparente a Wandbox Zero-Auth si el nodo público de Piston rechaza sin key
                    executeOnWandbox(langNorm, code, stdin, startMs)
                }
                "godbolt" -> {
                    val res = executeOnGodbolt(langNorm, code, stdin, startMs)
                    if (res.success) return@withContext res
                    executeOnWandbox(langNorm, code, stdin, startMs)
                }
                "h2.nexus", "h2nexus" -> {
                    val url = customEndpointUrl?.takeIf { it.isNotBlank() } ?: "https://wandbox.org/api/compile.json"
                    if (url.contains("wandbox")) {
                        executeOnWandbox(langNorm, code, stdin, startMs, providerLabel = "H2.nexus / Wandbox Zero-Auth VM")
                    } else {
                        executeOnCustomOrPiston(url, langNorm, code, stdin, startMs, providerLabel = "H2.nexus Virtual Server")
                    }
                }
                else -> {
                    val wandboxRes = executeOnWandbox(langNorm, code, stdin, startMs)
                    if (wandboxRes.success) return@withContext wandboxRes
                    executeOnGodbolt(langNorm, code, stdin, startMs)
                }
            }
        } catch (e: Exception) {
            CloudExecutionResult(
                success = false,
                providerUsed = preferredProvider,
                language = langNorm,
                stdout = "",
                stderr = "Error de conexión con sandbox remoto: ${e.message}",
                exitCode = -1,
                executionTimeMs = System.currentTimeMillis() - startMs,
            )
        }
    }

    private suspend fun executeOnWandbox(
        lang: String,
        code: String,
        stdin: String,
        startMs: Long,
        providerLabel: String = "Wandbox Zero-Auth VM (GCC/Clang/CPython)",
    ): CloudExecutionResult = try {
        val compiler = when (lang) {
            "python", "py", "python3" -> "cpython-3.12.7"
            "c", "gcc", "clang-c" -> "gcc-head-c"
            "cpp", "c++", "clang++", "g++" -> "gcc-head"
            "js", "javascript", "node", "nodejs" -> "nodejs-20.17.0"
            "ts", "typescript" -> "typescript-5.6.2"
            "rust", "rs" -> "rust-1.82.0"
            "go", "golang" -> "go-1.23.2"
            "bash", "sh", "shell" -> "bash"
            "sql", "sqlite" -> "sqlite-3.46.1"
            "java" -> "openjdk-jdk-22+36"
            "ruby", "rb" -> "ruby-3.4.9"
            "lua" -> "lua-5.4.7"
            else -> "cpython-3.12.7"
        }

        val payload = buildJsonObject {
            put("compiler", compiler)
            put("code", code)
            if (stdin.isNotEmpty()) {
                put("stdin", stdin)
            }
            put("save", false)
        }

        val response = httpClient.post("https://wandbox.org/api/compile.json") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        val rawBody = response.bodyAsText()
        val parsed = json.parseToJsonElement(rawBody).jsonObject

        val statusStr = parsed["status"]?.jsonPrimitive?.content ?: "0"
        val exitCode = statusStr.toIntOrNull() ?: 0
        val programOut = parsed["program_output"]?.jsonPrimitive?.content
            ?: parsed["program_message"]?.jsonPrimitive?.content
            ?: ""
        val compilerErr = parsed["compiler_error"]?.jsonPrimitive?.content ?: ""
        val programErr = parsed["program_error"]?.jsonPrimitive?.content ?: ""
        val combinedErr = listOf(compilerErr, programErr).filter { it.isNotBlank() }.joinToString("\n")

        FenixBlackBoxRecorder.recordEvent(
            category = "CLOUD_SANDBOX",
            action = "WANDBOX_EXEC",
            component = "ZeroAuthCloudSandboxClient",
            details = "Lenguaje=$lang ($compiler), exit=$exitCode",
        )

        CloudExecutionResult(
            success = response.status.value in 200..299 && exitCode == 0,
            providerUsed = "$providerLabel [$compiler]",
            language = lang,
            stdout = programOut,
            stderr = combinedErr,
            exitCode = exitCode,
            executionTimeMs = System.currentTimeMillis() - startMs,
        )
    } catch (e: Exception) {
        CloudExecutionResult(
            success = false,
            providerUsed = providerLabel,
            language = lang,
            stdout = "",
            stderr = "Wandbox error: ${e.message}",
            exitCode = -1,
            executionTimeMs = System.currentTimeMillis() - startMs,
        )
    }

    private suspend fun executeOnGodbolt(
        lang: String,
        code: String,
        stdin: String,
        startMs: Long,
    ): CloudExecutionResult {
        val compilerId = when (lang) {
            "c" -> "cg141"
            "cpp", "c++" -> "clang1810"
            "python", "py" -> "python312"
            "rust", "rs" -> "r1810"
            "go", "golang" -> "gltip"
            else -> "clang1810"
        }

        val payload = buildJsonObject {
            put("source", code)
            put(
                "options",
                buildJsonObject {
                    put("userArguments", "")
                    put(
                        "executeParameters",
                        buildJsonObject {
                            put("args", buildJsonArray {})
                            put("stdin", stdin)
                        },
                    )
                    put(
                        "compilerOptions",
                        buildJsonObject {
                            put("executorRequest", true)
                        },
                    )
                    put(
                        "filters",
                        buildJsonObject {
                            put("execute", true)
                        },
                    )
                },
            )
        }

        val response = httpClient.post("https://godbolt.org/api/compiler/$compilerId/compile") {
            contentType(ContentType.Application.Json)
            header("Accept", "application/json")
            setBody(payload.toString())
        }

        val rawBody = response.bodyAsText()
        val parsed = json.parseToJsonElement(rawBody).jsonObject
        val codeExit = parsed["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

        return CloudExecutionResult(
            success = response.status.value in 200..299,
            providerUsed = "Compiler Explorer (Godbolt Zero-Auth) [$compilerId]",
            language = lang,
            stdout = rawBody.take(4000),
            stderr = "",
            exitCode = codeExit,
            executionTimeMs = System.currentTimeMillis() - startMs,
        )
    }

    private suspend fun executeOnCustomOrPiston(
        endpointUrl: String,
        lang: String,
        code: String,
        stdin: String,
        startMs: Long,
        providerLabel: String = "Piston / Custom Virtual Server",
    ): CloudExecutionResult {
        val payload = buildJsonObject {
            put("language", lang)
            put("version", "*")
            put(
                "files",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("content", code)
                        },
                    )
                },
            )
            if (stdin.isNotEmpty()) {
                put("stdin", stdin)
            }
        }

        val response = httpClient.post(endpointUrl) {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }

        if (response.status.value !in 200..299) {
            return CloudExecutionResult(
                success = false,
                providerUsed = providerLabel,
                language = lang,
                stdout = "",
                stderr = "HTTP ${response.status.value}: ${response.bodyAsText().take(500)}",
                exitCode = response.status.value,
                executionTimeMs = System.currentTimeMillis() - startMs,
            )
        }

        val rawBody = response.bodyAsText()
        val parsed = json.parseToJsonElement(rawBody).jsonObject
        val runObj = parsed["run"]?.jsonObject ?: parsed
        val stdout = runObj["stdout"]?.jsonPrimitive?.content ?: runObj["output"]?.jsonPrimitive?.content ?: ""
        val stderr = runObj["stderr"]?.jsonPrimitive?.content ?: ""
        val exitCode = runObj["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

        return CloudExecutionResult(
            success = true,
            providerUsed = providerLabel,
            language = lang,
            stdout = stdout,
            stderr = stderr,
            exitCode = exitCode,
            executionTimeMs = System.currentTimeMillis() - startMs,
        )
    }
}
