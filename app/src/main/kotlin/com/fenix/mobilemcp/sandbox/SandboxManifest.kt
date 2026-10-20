package com.fenix.mobilemcp.sandbox

/**
 * Immutable description of the Linux rootfs the sandbox installs.
 *
 * The artifact is Debian 12 (bookworm) arm64, built from the official
 * `library/debian:bookworm` Docker layer plus the packages the sandbox needs at
 * runtime (`curl`, `ca-certificates`, `python3-minimal`, `libpython3.11-stdlib`).
 * Nothing from a third-party rootfs is used.
 */
object SandboxManifest {
    const val VERSION: String = "debian12-bookworm-arm64-20261020-1"

    const val ROOTFS_URL: String =
        "https://github.com/eligiansupeer-hash/fenix-ia-movil-con-mcp-integrado/" +
            "releases/download/sandbox-rootfs-v1/fenix-debian12-arm64.tar.gz"

    const val SHA256: String = "7ef3389130204668efccc0dbe5664fb5d5110d12a68ef458b5af61aa8662a64b"

    const val SIZE_BYTES: Long = 66_422_998L

    /** Conservative free-space floor for extraction (rootfs is ~200 MB on disk). */
    const val REQUIRED_FREE_BYTES: Long = 450L * 1024L * 1024L

    /** Guest kernel string proot reports (some installers refuse very old kernels). */
    const val GUEST_KERNEL: String = "6.6.0-fenix-arm64"

    const val GUEST_PATH: String =
        "/root/.local/bin:/root/.opencode/bin:/usr/local/sbin:/usr/local/bin:" +
            "/usr/sbin:/usr/bin:/sbin:/bin"
}
