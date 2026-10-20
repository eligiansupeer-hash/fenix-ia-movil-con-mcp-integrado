package com.fenix.mobilemcp.sandbox.pty

import com.fenix.mobilemcp.sandbox.SandboxManager
import com.fenix.mobilemcp.sandbox.terminal.TerminalScreen
import com.fenix.mobilemcp.sandbox.terminal.TerminalSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

/**
 * One PTY-backed guest process plus its [TerminalScreen].
 *
 * Output is parsed under a lock on the reader thread and published at ~30 fps;
 * the parser mutates a large char grid, so publishing per chunk would allocate
 * a snapshot for every burst of OpenCode's diff renderer.
 */
class PtySession(
    private val manager: SandboxManager,
    val title: String,
    columns: Int,
    rows: Int,
) {
    val id: String = UUID.randomUUID().toString().take(8)

    enum class State { IDLE, RUNNING, EXITED }

    private val lock = Any()
    private val screen = TerminalScreen(columns = columns, rows = rows)
    private val winsizeName = "winsize-$id"

    private val _snapshot = MutableStateFlow(screen.snapshot())
    val snapshot: StateFlow<TerminalSnapshot> = _snapshot.asStateFlow()

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var repaint = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var process: Process? = null
    private var reader: Thread? = null
    private var pumpJob: Job? = null

    var onExit: (() -> Unit)? = null

    fun start(
        command: String,
        workDir: String = "/root",
    ) {
        stop()
        repaint = Channel(Channel.CONFLATED)
        manager.refreshDns()
        manager.writeWinsize(winsizeName, screen.rows, screen.columns)

        val argv = manager.ptyCommand(command, screen.rows, screen.columns, winsizeName)
        val builder = ProcessBuilder(argv).apply {
            directory(manager.paths.baseDir)
            environment().clear()
            environment().putAll(manager.environment())
            redirectErrorStream(true)
        }
        val started =
            try {
                builder.start()
            } catch (error: Exception) {
                appendNotice("No se pudo iniciar la sesión: ${error.message}")
                _state.value = State.EXITED
                return
            }
        process = started
        _exitCode.value = null
        _state.value = State.RUNNING

        pumpJob =
            scope.launch {
                while (true) {
                    repaint.receiveCatching().getOrNull() ?: break
                    delay(FRAME_MS)
                    publish()
                }
            }

        reader =
            thread(name = "pty-reader-$id", isDaemon = true) {
                val buffer = ByteArray(8192)
                try {
                    started.inputStream.use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            synchronized(lock) { screen.write(buffer.copyOf(read)) }
                            repaint.trySend(Unit)
                        }
                    }
                } catch (error: IOException) {
                    appendNotice("PTY cerrado: ${error.message}")
                } finally {
                    val code = runCatching { started.waitFor() }.getOrDefault(-1)
                    _exitCode.value = code
                    appendNotice("[sesión finalizada · código $code]")
                    _state.value = State.EXITED
                    publish()
                    if (process === started) process = null
                    onExit?.invoke()
                }
            }
    }

    fun write(text: String) {
        if (text.isEmpty()) return
        val target = process ?: return
        try {
            synchronized(target) {
                target.outputStream.write(text.toByteArray(Charsets.UTF_8))
                target.outputStream.flush()
            }
        } catch (error: IOException) {
            appendNotice("No se pudo escribir en el PTY: ${error.message}")
        }
    }

    fun resize(
        columns: Int,
        rows: Int,
    ) {
        if (columns < 1 || rows < 1) return
        if (columns == screen.columns && rows == screen.rows) return
        synchronized(lock) { screen.resize(columns, rows) }
        manager.writeWinsize(winsizeName, rows, columns)
        publish()
    }

    fun stop() {
        reader?.let { thread ->
            val target = process
            process = null
            runCatching { target?.outputStream?.close() }
            runCatching { target?.destroy() }
            if (target != null) {
                scope.launch {
                    delay(1_500)
                    if (target.isAlive) runCatching { target.destroyForcibly() }
                }
            }
            runCatching { thread.join(400) }
        }
        reader = null
        pumpJob?.cancel()
        pumpJob = null
        repaint.close()
        manager.deleteWinsize(winsizeName)
        _state.value = State.EXITED
    }

    private fun appendNotice(text: String) {
        synchronized(lock) {
            screen.write("\r\n\u001b[38;5;244m$text\u001b[0m\r\n".toByteArray(Charsets.UTF_8))
        }
        repaint.trySend(Unit)
    }

    private fun publish() {
        _snapshot.value = synchronized(lock) { screen.snapshot() }
    }

    private companion object {
        const val FRAME_MS = 33L
    }
}
