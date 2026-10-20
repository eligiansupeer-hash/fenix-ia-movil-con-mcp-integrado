package com.fenix.mobilemcp.sandbox

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SandboxProcessRunner(
    private val workingDir: File,
) {
    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean = false,
    ) {
        val ok: Boolean get() = !timedOut && exitCode == 0
    }

    fun run(
        argv: List<String>,
        environment: Map<String, String>,
        timeoutMs: Long = 60_000L,
        maxOutputChars: Int = 128 * 1024,
    ): Result {
        val builder = ProcessBuilder(argv).apply {
            directory(workingDir)
            environment().clear()
            environment().putAll(environment)
            redirectErrorStream(false)
        }
        val process =
            try {
                builder.start()
            } catch (error: Exception) {
                return Result(-1, "", "No se pudo iniciar proot: ${error.message}")
            }

        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val outThread = thread(name = "sandbox-stdout", isDaemon = true) {
            readBounded(process.inputStream, stdout, maxOutputChars)
        }
        val errThread = thread(name = "sandbox-stderr", isDaemon = true) {
            readBounded(process.errorStream, stderr, maxOutputChars)
        }

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
        }
        outThread.join(1_000)
        errThread.join(1_000)
        return Result(
            exitCode = if (finished) process.exitValue() else -1,
            stdout = stdout.toString(),
            stderr = stderr.toString(),
            timedOut = !finished,
        )
    }

    private fun readBounded(
        input: java.io.InputStream,
        sink: StringBuilder,
        limit: Int,
    ) {
        input.bufferedReader().use { reader ->
            val buffer = CharArray(4096)
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                if (sink.length < limit) sink.append(buffer, 0, read.coerceAtMost(limit - sink.length))
            }
        }
    }
}
