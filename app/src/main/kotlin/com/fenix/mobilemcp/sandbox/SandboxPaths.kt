package com.fenix.mobilemcp.sandbox

import android.content.Context
import android.system.Os
import java.io.File

/**
 * Filesystem layout of the Linux sandbox.
 *
 * Everything the guest owns lives under [baseDir] in `noBackupFilesDir`: a
 * 200 MB rootfs must not be uploaded to cloud backup, and it is reproducible
 * from [SandboxManifest]. The user workspace lives in `filesDir` so a future
 * export/share flow can reach it.
 */
class SandboxPaths(
    context: Context,
) {
    private val appContext = context.applicationContext

    val baseDir: File = File(appContext.noBackupFilesDir, "linux-sandbox")
    val rootfsDir: File = File(baseDir, "rootfs")
    val stagingDir: File = File(baseDir, "rootfs-staging")
    val downloadDir: File = File(baseDir, "download")
    val tmpDir: File = File(baseDir, "tmp")
    val shmDir: File = File(baseDir, "shm")
    val fakeProcDir: File = File(baseDir, "fake-proc")
    val binDir: File = File(baseDir, "bin")
    val localDir: File = File(baseDir, "local")

    val homeDir: File = File(appContext.filesDir, "sandbox-home")
    val workspaceDir: File = File(homeDir, "workspace")

    val markerFile: File = File(baseDir, "installed.properties")
    val rootfsArchive: File = File(downloadDir, "fenix-debian12-arm64.tar.gz")
    val localRootfsArchive: File = File(localDir, "rootfs.tar.gz")

    val nativeLibDir: File = File(appContext.applicationInfo.nativeLibraryDir)
    val proot: File = File(nativeLibDir, "libproot.so")
    val prootLoader: File = File(nativeLibDir, "libproot-loader.so")
    val libtalloc: File = File(nativeLibDir, "libtalloc.so")
    val libtermux: File = File(nativeLibDir, "libtermux.so")
    val ptyBridge: File = File(binDir, "pty_bridge.py")

    fun ensureLayout() {
        listOf(baseDir, downloadDir, tmpDir, shmDir, fakeProcDir, binDir, localDir, homeDir, workspaceDir)
            .forEach { it.mkdirs() }
        chmod(tmpDir, 0b111_111_111)
        chmod(shmDir, 0b111_111_111)
        chmod(workspaceDir, 0b111_101_101)
    }

    fun rootfsBash(): File = File(rootfsDir, "bin/bash")

    fun isRootfsInstalled(): Boolean = rootfsBash().isFile && rootfsBash().canExecute()

    fun readInstalledVersion(): String? {
        if (!markerFile.isFile) return null
        return runCatching {
            markerFile.readLines()
                .firstOrNull { it.startsWith("version=") }
                ?.substringAfter('=')
        }.getOrNull()
    }

    fun writeMarker() {
        markerFile.writeText(
            buildString {
                appendLine("version=${SandboxManifest.VERSION}")
                appendLine("sha256=${SandboxManifest.SHA256}")
                appendLine("installed_at=${System.currentTimeMillis()}")
            },
        )
    }

    private fun chmod(file: File, mode: Int) {
        runCatching { Os.chmod(file.absolutePath, mode) }
    }

    companion object {
        const val GUEST_TMP = "/tmp"
        const val GUEST_OPT = "/opt/fenix"
        const val GUEST_WORKSPACE = "/root/workspace"
    }
}
