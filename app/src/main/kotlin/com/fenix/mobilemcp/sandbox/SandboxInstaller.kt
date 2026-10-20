package com.fenix.mobilemcp.sandbox

import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Downloads, verifies and unpacks the Debian rootfs.
 *
 * The tar is written by `tar --hard-dereference --numeric-owner`; entries are
 * applied with [Os.chmod]/[Os.symlink] because Java cannot create symlinks or
 * POSIX modes. Extraction is tar-slip checked entry by entry, including
 * canonical-path checks so a symlink in the archive cannot redirect a later
 * entry outside the staging directory.
 */
class SandboxInstaller(
    private val paths: SandboxPaths,
) {
    data class Progress(
        val phase: Phase,
        val fraction: Float,
        val detail: String = "",
    ) {
        enum class Phase { DOWNLOADING, VERIFYING, EXTRACTING, CONFIGURING, DONE }
    }

    suspend fun install(
        dnsServers: List<String>,
        onProgress: (Progress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        paths.ensureLayout()
        checkFreeSpace()

        val archive =
            if (paths.localRootfsArchive.isFile) {
                onProgress(Progress(Progress.Phase.VERIFYING, 0f, "archivo local"))
                paths.localRootfsArchive
            } else {
                download(onProgress)
            }

        onProgress(Progress(Progress.Phase.VERIFYING, 0f, "SHA-256"))
        verify(archive)
        coroutineContext.ensureActive()

        paths.stagingDir.deleteRecursively()
        paths.stagingDir.mkdirs()
        extract(archive) { fraction ->
            onProgress(Progress(Progress.Phase.EXTRACTING, fraction, "${(fraction * 100).toInt()}%"))
        }
        coroutineContext.ensureActive()

        onProgress(Progress(Progress.Phase.CONFIGURING, 0f))
        configure(paths.stagingDir, dnsServers)
        writePtyBridge()
        coroutineContext.ensureActive()

        swapIntoPlace()
        paths.writeMarker()
        onProgress(Progress(Progress.Phase.DONE, 1f))
    }

    fun uninstall() {
        paths.rootfsDir.deleteRecursively()
        paths.stagingDir.deleteRecursively()
        paths.markerFile.delete()
    }

    private fun checkFreeSpace() {
        val free = paths.baseDir.usableSpace
        check(free >= SandboxManifest.REQUIRED_FREE_BYTES) {
            "Espacio insuficiente: ${free / (1024 * 1024)} MB libres, " +
                "se necesitan ${SandboxManifest.REQUIRED_FREE_BYTES / (1024 * 1024)} MB"
        }
    }

    private fun download(onProgress: (Progress) -> Unit): File {
        val target = paths.rootfsArchive
        val partial = File(target.parentFile, "${target.name}.part")
        partial.delete()
        target.parentFile?.mkdirs()

        val connection = (URL(SandboxManifest.ROOTFS_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "Fenix-Mobile-Sandbox/1.0")
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            check(code in 200..299) { "Descarga fallida: HTTP $code" }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: SandboxManifest.SIZE_BYTES
            var copied = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(
                            Progress(
                                Progress.Phase.DOWNLOADING,
                                (copied.toFloat() / total).coerceIn(0f, 1f),
                                "${copied / (1024 * 1024)} / ${total / (1024 * 1024)} MB",
                            ),
                        )
                    }
                }
            }
            check(copied == SandboxManifest.SIZE_BYTES) {
                "Tamaño inesperado: $copied bytes (esperado ${SandboxManifest.SIZE_BYTES})"
            }
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            return target
        } finally {
            connection.disconnect()
        }
    }

    private fun verify(archive: File) {
        val digest = MessageDigest.getInstance("SHA-256")
        archive.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(SandboxManifest.SHA256, ignoreCase = true)) {
            if (archive == paths.rootfsArchive) archive.delete()
            error("SHA-256 no coincide: $actual")
        }
    }

    private fun extract(
        archive: File,
        onProgress: (Float) -> Unit,
    ) {
        val root = paths.stagingDir.canonicalFile
        val total = archive.length().coerceAtLeast(1L)
        val counting = CountingInputStream(BufferedInputStream(FileInputStream(archive)))
        GzipCompressorInputStream(counting).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                var entry: TarArchiveEntry? = tar.nextEntry
                while (entry != null) {
                    applyEntry(root, entry, tar)
                    onProgress((counting.count.toFloat() / total).coerceIn(0f, 1f))
                    entry = tar.nextEntry
                }
            }
        }
    }

    private fun applyEntry(
        root: File,
        entry: TarArchiveEntry,
        tar: TarArchiveInputStream,
    ) {
        val name = entry.name.replace('\\', '/').removePrefix("./").trimStart('/')
        if (name.isEmpty() || name == ".") return

        val out = File(root, name)
        val canonical = out.canonicalFile
        check(canonical == root || canonical.path.startsWith(root.path + File.separator)) {
            "Entrada fuera del rootfs: ${entry.name}"
        }

        when {
            entry.isDirectory -> {
                canonical.mkdirs()
                chmod(canonical, (entry.mode and 0x1FF) or 0x1C0)
            }

            entry.isSymbolicLink -> {
                canonical.parentFile?.mkdirs()
                canonical.delete()
                Os.symlink(entry.linkName, canonical.absolutePath)
            }

            entry.isLink -> {
                val targetName = entry.linkName.removePrefix("./").trimStart('/')
                val target = File(root, targetName).canonicalFile
                check(target.path.startsWith(root.path + File.separator)) {
                    "Hardlink fuera del rootfs: ${entry.linkName}"
                }
                canonical.parentFile?.mkdirs()
                canonical.delete()
                if (!runCatching { Os.link(target.absolutePath, canonical.absolutePath) }.isSuccess) {
                    check(target.isFile) { "Hardlink sin destino: ${entry.linkName}" }
                    target.copyTo(canonical, overwrite = true)
                }
                chmod(canonical, fileMode(entry.mode))
            }

            else -> {
                canonical.parentFile?.mkdirs()
                canonical.delete()
                FileOutputStream(canonical).use { output -> tar.copyTo(output) }
                chmod(canonical, fileMode(entry.mode))
            }
        }
    }

    private fun fileMode(mode: Int): Int {
        val base = if (mode and 0x49 != 0) 0x1C0 else 0x180
        return (mode and 0x1FF) or base
    }

    private fun chmod(file: File, mode: Int) {
        runCatching { Os.chmod(file.absolutePath, mode) }
    }

    private fun configure(
        root: File,
        dnsServers: List<String>,
    ) {
        val dns = dnsServers.ifEmpty { listOf("8.8.8.8", "1.1.1.1") }
        File(root, "etc").mkdirs()
        File(root, "etc/resolv.conf").writeText(dns.joinToString("") { "nameserver $it\n" })
        File(root, "etc/hostname").writeText("fenix\n")
        File(root, "etc/hosts").writeText(
            "127.0.0.1 localhost fenix\n::1 localhost ip6-localhost ip6-loopback\n",
        )

        val profileDir = File(root, "etc/profile.d").apply { mkdirs() }
        File(profileDir, "fenix-path.sh").writeText(
            """
            |# Managed by Fenix Mobile — coding agents live outside /usr.
            |export PATH="/root/.local/bin:/root/.opencode/bin:${'$'}PATH"
            |
            """.trimMargin(),
        )

        File(root, "root").mkdirs()
        File(root, "root/.profile").writeText(
            """
            |# Managed by Fenix Mobile
            |[ -f /etc/profile.d/fenix-path.sh ] && . /etc/profile.d/fenix-path.sh
            |
            """.trimMargin(),
        )
        File(root, "root/.bashrc").writeText(
            """
            |# ~/.bashrc — Fenix Mobile Linux sandbox
            |export PATH="/root/.local/bin:/root/.opencode/bin:${'$'}PATH"
            |export LANG=C.UTF-8
            |export TERM=xterm-256color
            |export PS1='\[\e[1;32m\]fenix\[\e[0m\]:\[\e[1;34m\]\w\[\e[0m\]\$ '
            |alias ll='ls -alF'
            |
            """.trimMargin(),
        )

        listOf("tmp", "dev/shm", "dev/pts", "proc", "sys", "root/.local/bin", "root/workspace", "root/projects")
            .forEach { File(root, it).mkdirs() }
        // CRITICAL: /proc/self/cwd symlink for getcwd() in static binaries (Bun/OpenCode)
        File(root, "proc/self").mkdirs()
        Os.symlink("/root", File(root, "proc/self/cwd").absolutePath)
        chmod(File(root, "tmp"), 0x1FF)
        chmod(File(root, "dev/shm"), 0x1FF)
    }

    private fun writePtyBridge() {
        paths.binDir.mkdirs()
        paths.ptyBridge.writeText(PTY_BRIDGE)
        chmod(paths.ptyBridge, 0x1ED)
    }

    private fun swapIntoPlace() {
        val old = File(paths.baseDir, "rootfs-old")
        if (old.exists()) old.deleteRecursively()
        if (paths.rootfsDir.exists() && !paths.rootfsDir.renameTo(old)) {
            paths.rootfsDir.deleteRecursively()
        }
        if (!paths.stagingDir.renameTo(paths.rootfsDir)) {
            old.renameTo(paths.rootfsDir)
            error("No se pudo activar el rootfs extraído")
        }
        old.deleteRecursively()
    }

    private class CountingInputStream(
        input: InputStream,
    ) : FilterInputStream(input) {
        var count: Long = 0L
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) count++
            return value
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            val value = super.read(b, off, len)
            if (value > 0) count += value
            return value
        }
    }

    companion object {
        val PTY_BRIDGE: String =
            """
            |#!/usr/bin/env python3
            |# Fenix Mobile Linux sandbox — PTY bridge.
            |# Adapted from Kai (https://github.com/SimonSchubert/Kai), Apache License 2.0.
            |# Runs inside proot: allocates a real PTY for the guest command and bridges raw
            |# bytes to the app over stdin/stdout. Also answers the handful of terminal
            |# capability queries that modern TUIs (OpenTUI/OpenCode) wait for before paint.
            |import base64, errno, fcntl, os, pty, re, select, signal, struct, sys, termios
            |
            |ROWS = int(os.environ.get("FENIX_ROWS", "24"))
            |COLS = int(os.environ.get("FENIX_COLS", "80"))
            |WS_PATH = os.environ.get("FENIX_WINSIZE", "")
            |CMD = base64.b64decode(os.environ.get("FENIX_CMD_B64", "")).decode("utf-8", "replace")
            |
            |def set_winsize(fd, rows, cols):
            |    fcntl.ioctl(fd, termios.TIOCSWINSZ, struct.pack("HHHH", rows, cols, 0, 0))
            |
            |def get_winsize(fd):
            |    packed = fcntl.ioctl(fd, termios.TIOCGWINSZ, struct.pack("HHHH", 0, 0, 0, 0))
            |    rows, cols, _, _ = struct.unpack("HHHH", packed)
            |    return rows, cols
            |
            |pid, master = pty.fork()
            |if pid == 0:
            |    os.environ["TERM"] = "xterm-256color"
            |    os.environ["COLORTERM"] = "truecolor"
            |    os.environ["COLUMNS"] = str(COLS)
            |    os.environ["LINES"] = str(ROWS)
            |    os.environ["FENIX_SANDBOX"] = "1"
            |    os.environ.pop("LD_LIBRARY_PATH", None)
            |    # PATH con /root/.opencode/bin y /root/.local/bin para que opencode y herramientas estén disponibles
            |    os.environ["PATH"] = "/root/.local/bin:/root/.opencode/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            |    os.execvp("/bin/bash", ["bash", "-lc", CMD])
            |    os._exit(127)
            |
            |try:
            |    set_winsize(master, ROWS, COLS)
            |except OSError:
            |    pass
            |try:
            |    os.kill(pid, signal.SIGWINCH)
            |except OSError:
            |    pass
            |
            |MODES = {}
            |
            |def respond(data):
            |    # Answer capability queries; otherwise OpenTUI blocks waiting for the terminal.
            |    for m in re.finditer(rb"\x1b\[\?([0-9;]+)([hl])", data):
            |        for n in m.group(1).split(b";"):
            |            try:
            |                MODES[int(n)] = (m.group(2) == b"h")
            |            except ValueError:
            |                pass
            |    out = bytearray()
            |    if b"\x1b[6n" in data:
            |        out += b"\x1b[1;1R"
            |    if b"\x1b[?u" in data:
            |        out += b"\x1b[?0u"
            |    if b"\x1b[14t" in data:
            |        out += b"\x1b[4;480;640t"
            |    if b"\x1b[c" in data or b"\x1b[>c" in data:
            |        out += b"\x1b[?1;2c"
            |    if b"\x1b]10;?" in data:
            |        out += b"\x1b]10;rgb:ffff/ffff/ffff\x1b\\"
            |    if b"\x1b]11;?" in data:
            |        out += b"\x1b]11;rgb:0000/0000/0000\x1b\\"
            |    for m in re.finditer(rb"\x1b\[\?(\d+)\x24p", data):
            |        mode = int(m.group(1))
            |        state = 1 if MODES.get(mode, False) else 2
            |        out += b"\x1b[?%d;%d\x24y" % (mode, state)
            |    for m in re.finditer(rb"\x1bP\+q([0-9a-fA-F]+)\x1b\\", data):
            |        out += b"\x1bP0+r" + m.group(1) + b"\x1b\\"
            |    if out:
            |        try:
            |            os.write(master, bytes(out))
            |        except OSError:
            |            pass
            |
            |def poll_winsize():
            |    if not WS_PATH:
            |        return
            |    try:
            |        with open(WS_PATH, "r") as f:
            |            parts = f.read().split()
            |        if len(parts) < 2:
            |            return
            |        rows, cols = int(parts[0]), int(parts[1])
            |        if rows < 1 or cols < 1 or get_winsize(master) == (rows, cols):
            |            return
            |        set_winsize(master, rows, cols)
            |        try:
            |            os.kill(pid, signal.SIGWINCH)
            |        except OSError:
            |            pass
            |    except (OSError, ValueError, struct.error):
            |        pass
            |
            |stdin_open = True
            |try:
            |    while True:
            |        rfds = [master] + ([0] if stdin_open else [])
            |        try:
            |            ready, _, _ = select.select(rfds, [], [], 0.05)
            |        except InterruptedError:
            |            continue
            |        poll_winsize()
            |        if master in ready:
            |            try:
            |                data = os.read(master, 8192)
            |            except OSError as e:
            |                if e.errno == errno.EIO:
            |                    break
            |                raise
            |            if not data:
            |                break
            |            respond(data)
            |            try:
            |                os.write(1, data)
            |            except OSError:
            |                break
            |        if stdin_open and 0 in ready:
            |            data = os.read(0, 8192)
            |            if not data:
            |                stdin_open = False
            |            else:
            |                try:
            |                    os.write(master, data)
            |                except OSError:
            |                    break
            |except OSError:
            |    pass
            |finally:
            |    try:
            |        os.close(master)
            |    except OSError:
            |        pass
            |
            |_, status = os.waitpid(pid, 0)
            |if hasattr(os, "waitstatus_to_exitcode"):
            |    raise SystemExit(os.waitstatus_to_exitcode(status))
            |raise SystemExit(os.WEXITSTATUS(status) if os.WIFEXITED(status) else 1)
            """.trimMargin()

    }
}
