package com.fenix.mobilemcp.security

import android.os.Environment
import com.fenix.mobilemcp.mcp.McpToolException
import java.io.File
import java.util.Locale

/**
 * Filtro de Seguridad Perimetral para Operaciones de Archivos en Fénix MCP.
 *
 * Protege contra:
 * 1. Acceso a datos de aplicaciones bancarias, financieras y billeteras digitales.
 * 2. Ataques de Path Traversal (escape hacia /data/data ajenos o rutas de sistema).
 * 3. Filtración de llaves criptográficas, certificados y almacenes de claves.
 * 4. Borrado accidental o malicioso de directorios raíz del sistema.
 */
object FenixFileSecurityFilter {

    private val RESTRICTED_PACKAGE_PATTERNS = listOf(
        "mercadopago",
        "banconacion",
        "santander",
        "brubank",
        "tarjetanaranja",
        "naranja",
        "galicia",
        "bbva",
        "macro",
        "cuenta_dni",
        "cuentadni",
        "bna",
        "afip",
        "arca",
        "authenticator",
        "authy",
        "1password",
        "bitwarden",
        "keepass"
    )

    private val RESTRICTED_FILE_EXTENSIONS = listOf(
        ".jks",
        ".keystore",
        ".p12",
        ".pfx",
        ".key",
        ".pk8"
    )

    private val RESTRICTED_FILE_NAMES = listOf(
        "id_rsa",
        "id_ed25519",
        "id_ecdsa",
        "id_dsa",
        "known_hosts",
        "authorized_keys"
    )

    /**
     * Valida y sanitiza una ruta de archivo o directorio.
     *
     * @param targetFile Archivo o carpeta objetivo.
     * @param allowSystemRoot Si es true, permite rutas relativas dentro de /sdcard.
     * @throws McpToolException.PermissionDenied si la ruta viola las políticas de seguridad perimetral.
     */
    fun validateSafeAccess(targetFile: File, forWrite: Boolean = false) {
        val canonicalPath = try {
            targetFile.canonicalPath
        } catch (e: Exception) {
            throw McpToolException.InvalidParams("Ruta inválida o inaccesible: ${targetFile.path}")
        }

        val lowerPath = canonicalPath.lowercase(Locale.ROOT)
        val fileName = targetFile.name.lowercase(Locale.ROOT)

        // 1. Verificación de aplicaciones bancarias y billeteras
        for (restricted in RESTRICTED_PACKAGE_PATTERNS) {
            if (lowerPath.contains(restricted)) {
                throw McpToolException.PermissionDenied(
                    "Acceso denegado por seguridad perimetral: La ruta contiene referencias a aplicaciones financieras o de autenticación sensibles ($restricted)."
                )
            }
        }

        // 2. Verificación de archivos criptográficos y claves privadas
        for (ext in RESTRICTED_FILE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                throw McpToolException.PermissionDenied(
                    "Acceso denegado por seguridad perimetral: No está permitido acceder a certificados o keystores privados ($ext)."
                )
            }
        }

        for (name in RESTRICTED_FILE_NAMES) {
            if (fileName == name) {
                throw McpToolException.PermissionDenied(
                    "Acceso denegado por seguridad perimetral: Archivo de llave privada protegido ($name)."
                )
            }
        }

        // 3. Verificación de escape de almacenamiento (Anti-Path Traversal)
        val storageRoot = Environment.getExternalStorageDirectory()?.canonicalPath ?: "/sdcard"
        val isInsideStorage = canonicalPath.startsWith(storageRoot) || canonicalPath.startsWith("/sdcard") || canonicalPath.startsWith("/storage/emulated/0")

        if (!isInsideStorage) {
            // Permitir solo almacenamiento privado de la app
            val isAppPrivate = canonicalPath.contains("/data/data/com.fenix.mobilemcp") ||
                    canonicalPath.contains("/Android/data/com.fenix.mobilemcp")
            if (!isAppPrivate) {
                throw McpToolException.PermissionDenied(
                    "Acceso denegado: La ruta está fuera del almacenamiento autorizado del usuario."
                )
            }
        }

        // 4. Protección contra borrado de raíces
        if (forWrite) {
            if (canonicalPath == storageRoot || canonicalPath == "/sdcard" || canonicalPath == "/storage/emulated/0") {
                throw McpToolException.PermissionDenied(
                    "Operación destructiva denegada: No está permitido modificar o eliminar la raíz del almacenamiento."
                )
            }
        }
    }
}

