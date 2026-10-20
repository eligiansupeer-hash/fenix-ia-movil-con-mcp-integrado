package com.fenix.mobilemcp.sandbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single entry point for the Linux sandbox: layout, install, DNS, guest
 * commands and PTY command construction.
 *
 * One instance per screen is enough; it holds no mutable session state, so the
 * settings and console screen can each build their own.
 */
class SandboxManager(
    context: Context,
) {
    private val appContext = context.applicationContext

    val paths: SandboxPaths = SandboxPaths(appContext)
    val installer: SandboxInstaller = SandboxInstaller(paths)
    val proot: ProotCommandBuilder = ProotCommandBuilder(paths)
    private val runner: SandboxProcessRunner = SandboxProcessRunner(paths.baseDir)

    data class Status(
        val installed: Boolean,
        val installedVersion: String?,
        val openCodeInstalled: Boolean,
        val rootfsBytes: Long,
        val freeBytes: Long,
        val nativeProotPresent: Boolean,
    )

    fun status(): Status {
        val opencode = File(paths.rootfsDir, "root/.opencode/bin/opencode")
        return Status(
            installed = paths.isRootfsInstalled(),
            installedVersion = paths.readInstalledVersion(),
            openCodeInstalled = opencode.isFile,
            rootfsBytes = directorySize(paths.rootfsDir),
            freeBytes = paths.baseDir.usableSpace,
            nativeProotPresent = paths.proot.isFile && paths.prootLoader.isFile,
        )
    }

    fun dnsServers(): List<String> {
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
        val properties: LinkProperties? = manager?.activeNetwork?.let { manager.getLinkProperties(it) }
        val fromSystem = properties?.dnsServers?.mapNotNull { it.hostAddress }?.filter { it.isNotBlank() }
        return if (!fromSystem.isNullOrEmpty()) fromSystem else listOf("8.8.8.8", "1.1.1.1")
    }

    suspend fun install(onProgress: (SandboxInstaller.Progress) -> Unit) {
        paths.ensureLayout()
        installer.install(dnsServers(), onProgress)
    }

    fun uninstall() {
        installer.uninstall()
    }

    /** Rewrites resolv.conf from the current network before a session starts. */
    fun refreshDns() {
        if (!paths.isRootfsInstalled()) return
        runCatching {
            File(paths.rootfsDir, "etc/resolv.conf").writeText(
                dnsServers().joinToString("") { "nameserver $it\n" },
            )
        }
    }

    suspend fun smokeTest(): SandboxProcessRunner.Result = withContext(Dispatchers.IO) {
        checkInstalled()
        runner.run(
            argv = proot.command(
                "uname -a; id -u; python3 --version; curl --version | head -1; " +
                    "echo SANDBOX_OK",
            ),
            environment = proot.environment(),
            timeoutMs = 60_000L,
        )
    }

    suspend fun installOpenCode(): SandboxProcessRunner.Result = withContext(Dispatchers.IO) {
        checkInstalled()
        refreshDns()
        runner.run(
            argv = proot.command(
                "curl -fsSL https://opencode.ai/install | bash -s -- --no-modify-path && " +
                    "opencode --version",
            ),
            environment = proot.environment(),
            timeoutMs = 15 * 60_000L,
        )
    }

    fun shellCommand(): String = "/bin/bash -l"

    fun openCodeCommand(): String = "opencode"

    fun ptyCommand(
        guestCommand: String,
        rows: Int,
        cols: Int,
        winsizeName: String,
    ): List<String> = proot.ptyCommand(guestCommand, rows, cols, winsizeName)

    fun environment(): Map<String, String> = proot.environment()

    fun writeWinsize(
        name: String,
        rows: Int,
        cols: Int,
    ) {
        proot.winsizeFile(name).writeText("$rows $cols\n")
    }

    fun deleteWinsize(name: String) {
        proot.winsizeFile(name).delete()
    }

    private fun checkInstalled() {
        check(paths.isRootfsInstalled()) { "El sandbox Linux no está instalado" }
        check(paths.proot.isFile && paths.prootLoader.isFile) {
            "Faltan los binarios proot arm64 en el APK (jniLibs)"
        }
    }

    private fun directorySize(dir: File): Long {
        if (!dir.isDirectory) return 0L
        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}
