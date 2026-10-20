package com.fenix.mobilemcp.sandbox

import android.util.Base64
import java.io.File

/**
 * Assembles the proot invocation used to enter the guest.
 *
 * Bind order matters: `/dev`, `/proc` and `/sys` come from the host, and the
 * fake `/proc` files are bound last so they shadow the host values that Android
 * restricts. `/tmp`, `/opt/fenix`, `/root/workspace` and `/dev/shm` are the only
 * writable host directories the guest sees besides its own rootfs.
 * 
 * Flags for static binary support (Bun/OpenCode):
 * - --link2symlink: resolve symlinks in guest (required for static bins)
 * - -0: trace all syscalls (required for getcwd in static bins)
 * - --kill-on-exit: cleanup on exit
 * - PROOT_NO_SECCOMP=1: disable seccomp (static bins fail with seccomp)
 * - /proc/self/cwd bind: getcwd() needs this symlink for static bins
 */
class ProotCommandBuilder(
    private val paths: SandboxPaths,
) {
    fun environment(): Map<String, String> =
        mapOf(
            "PROOT_NO_SECCOMP" to "1",
            "PROOT_TMP_DIR" to paths.tmpDir.absolutePath,
            "PROOT_LOADER" to paths.prootLoader.absolutePath,
            "LD_LIBRARY_PATH" to paths.nativeLibDir.absolutePath,
            "TMPDIR" to paths.tmpDir.absolutePath,
        )

    fun command(
        guestCommand: String,
        workDir: String = "/root",
        extraEnv: Map<String, String> = emptyMap(),
    ): List<String> {
        paths.ensureLayout()
        ensureFakeProc()

        val argv = mutableListOf<String>()
        argv += paths.proot.absolutePath
        // Flags for static binary support (Bun/OpenCode getcwd fix)
        argv += listOf(
            "--kill-on-exit",
            "--link2symlink",
            "-L",
            "-0",                    // trace all syscalls (critical for static bins)
            "-k", SandboxManifest.GUEST_KERNEL
        )
        argv += listOf("-r", paths.rootfsDir.absolutePath, "-w", workDir)
        argv += listOf("-b", "/dev", "-b", "/proc", "-b", "/sys")
        argv += listOf("-b", "${paths.tmpDir.absolutePath}:/tmp")
        argv += listOf("-b", "${paths.binDir.absolutePath}:/opt/fenix")
        argv += listOf("-b", "${paths.workspaceDir.absolutePath}:/root/workspace")
        argv += listOf("-b", "${paths.shmDir.absolutePath}:/dev/shm")
        // Fake /proc files (shadow host)
        argv += listOf("-b", "${fake("stat").absolutePath}:/proc/stat")
        argv += listOf("-b", "${fake("version").absolutePath}:/proc/version")
        argv += listOf("-b", "${fake("uptime").absolutePath}:/proc/uptime")
        argv += listOf("-b", "${fake("loadavg").absolutePath}:/proc/loadavg")
        argv += listOf("-b", "${fake("vmstat").absolutePath}:/proc/vmstat")
        // /proc/self/cwd symlink creado en rootfs durante instalación (fix getcwd static bins)

        argv += "/usr/bin/env"
        argv += "-i"
        argv += "HOME=/root"
        argv += "USER=root"
        argv += "LOGNAME=root"
        argv += "SHELL=/bin/bash"
        argv += "TERM=xterm-256color"
        argv += "COLORTERM=truecolor"
        argv += "LANG=C.UTF-8"
        argv += "PATH=${SandboxManifest.GUEST_PATH}"
        argv += "TMPDIR=/tmp"
        argv += "FENIX_SANDBOX=1"
        extraEnv.forEach { (key, value) -> argv += "$key=$value" }
        argv += "/bin/bash"
        argv += "-lc"
        argv += guestCommand
        return argv
    }

    /**
     * Command that runs [guestCommand] under a real PTY inside the guest.
     * The bridge reads its command and window size from environment variables so
     * the command line never has to be quoted twice.
     */
    fun ptyCommand(
        guestCommand: String,
        rows: Int,
        cols: Int,
        winsizeName: String,
        workDir: String = "/root",
    ): List<String> {
        val encoded = Base64.encodeToString(guestCommand.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return command(
            guestCommand = "python3 -u /opt/fenix/pty_bridge.py",
            workDir = workDir,
            extraEnv = mapOf(
                "FENIX_CMD_B64" to encoded,
                "FENIX_ROWS" to rows.toString(),
                "FENIX_COLS" to cols.toString(),
                "FENIX_WINSIZE" to "/tmp/$winsizeName",
            ),
        )
    }

    fun winsizeFile(name: String): File = File(paths.tmpDir, name)

    private fun ensureFakeProc() {
        paths.fakeProcDir.mkdirs()
        writeIfChanged(
            fake("stat"),
            """
            |cpu  0 0 0 0 0 0 0 0 0 0
            |intr 0
            |ctxt 0
            |btime 0
            |processes 0
            |procs_running 1
            |procs_blocked 0
            |
            """.trimMargin(),
        )
        writeIfChanged(
            fake("version"),
            "Linux version 6.6.0-fenix-arm64 (fenix@mobile) (gcc 12.2.0) #1 SMP PREEMPT\n",
        )
        writeIfChanged(fake("uptime"), "12345.67 12345.67\n")
        writeIfChanged(fake("loadavg"), "0.00 0.00 0.00 1/1 1\n")
        writeIfChanged(
            fake("vmstat"),
            "nr_free_pages 262144\nnr_zone_inactive_anon 0\nnr_zone_active_anon 0\n" +
                "pgfault 0\npgmajfault 0\n",
        )
    }

    private fun fake(name: String): File = File(paths.fakeProcDir, name)

    private fun writeIfChanged(
        file: File,
        content: String,
    ) {
        if (!file.isFile || file.readText() != content) file.writeText(content)
    }
}
