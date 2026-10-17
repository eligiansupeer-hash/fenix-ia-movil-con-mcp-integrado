@file:Suppress("MagicNumber", "TooGenericExceptionCaught", "LongMethod", "MaxLineLength")

package com.fenix.mobilemcp.connectors.storage

import com.fenix.mobilemcp.security.FenixFileSecurityFilter
import com.fenix.mobilemcp.telemetry.FenixBlackBoxRecorder
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class EphemeralUploadResult(
    val success: Boolean,
    val providerUsed: String,
    val fileName: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val expiresInfo: String,
    val message: String,
)

/**
 * Conector para almacenamiento efímero en la nube sin registro, sin login y sin tarjeta.
 * Soporta `file.io`, `tmpfiles.org`, `storage.to` y servidores personalizados agregados por el usuario.
 */
object EphemeralCloudStorageClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(45, TimeUnit.SECONDS)
                    writeTimeout(45, TimeUnit.SECONDS)
                }
            }
        }
    }

    suspend fun uploadFile(
        filePath: String,
        preferredProvider: String = "file.io",
        customUploadUrl: String? = null,
    ): EphemeralUploadResult = withContext(Dispatchers.IO) {
        var file = File(filePath)
        FenixFileSecurityFilter.validateSafeAccess(file, forWrite = false)

        val tabMatch = com.fenix.mobilemcp.studio.LiveWorkspaceBus.openTabsFlow.value.firstOrNull {
            it.path == filePath || it.name.equals(file.name, ignoreCase = true)
        }

        if ((!file.exists() || !file.isFile) && tabMatch == null) {
            return@withContext EphemeralUploadResult(
                success = false,
                providerUsed = preferredProvider,
                fileName = file.name,
                sizeBytes = 0L,
                downloadUrl = "",
                expiresInfo = "",
                message = "El archivo no existe o no es válido: $filePath",
            )
        }

        val bytes = if (file.exists() && file.isFile) {
            file.readBytes()
        } else {
            tabMatch!!.content.toByteArray(Charsets.UTF_8)
        }
        val providerNorm = preferredProvider.trim().lowercase(Locale.ROOT)

        try {
            if (!customUploadUrl.isNullOrBlank()) {
                val res = uploadToMultipartEndpoint(customUploadUrl, file.name, bytes, "Custom Storage Server")
                if (res.success) return@withContext res
            }

            when (providerNorm) {
                "storage.to", "storageto", "tmpfiles", "tmpfiles.org" -> {
                    val res = uploadToTmpFilesOrg(file.name, bytes)
                    if (res.success) return@withContext res
                    uploadToFileIo(file.name, bytes)
                }
                else -> {
                    val res = uploadToTmpFilesOrg(file.name, bytes)
                    if (res.success) return@withContext res
                    uploadToFileIo(file.name, bytes)
                }
            }
        } catch (e: Exception) {
            EphemeralUploadResult(
                success = false,
                providerUsed = preferredProvider,
                fileName = file.name,
                sizeBytes = bytes.size.toLong(),
                downloadUrl = "",
                expiresInfo = "",
                message = "Error al subir archivo efímero: ${e.message}",
            )
        }
    }

    private suspend fun uploadToFileIo(fileName: String, bytes: ByteArray): EphemeralUploadResult = try {
        val response = httpClient.post("https://file.io/") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    },
                ),
            )
        }

        val raw = response.bodyAsText()
        val parsed = json.parseToJsonElement(raw).jsonObject
        val ok = parsed["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val link = parsed["link"]?.jsonPrimitive?.content ?: ""
        val expires = parsed["expires"]?.jsonPrimitive?.content ?: "1 descarga / 14 días"

        if (ok && link.isNotBlank()) {
            FenixBlackBoxRecorder.recordEvent(
                category = "CLOUD_STORAGE",
                action = "FILE_IO_UPLOAD",
                component = "EphemeralCloudStorageClient",
                details = "Subido $fileName (${bytes.size} bytes) -> $link",
            )
        }

        EphemeralUploadResult(
            success = ok && link.isNotBlank(),
            providerUsed = "file.io (Zero-Auth Ephemeral Vault)",
            fileName = fileName,
            sizeBytes = bytes.size.toLong(),
            downloadUrl = link,
            expiresInfo = expires,
            message = if (ok) "Enlace efímero generado correctamente (se autodestruye tras la descarga)." else "Respuesta: $raw",
        )
    } catch (e: Exception) {
        EphemeralUploadResult(
            success = false,
            providerUsed = "file.io",
            fileName = fileName,
            sizeBytes = bytes.size.toLong(),
            downloadUrl = "",
            expiresInfo = "",
            message = "Fallback: ${e.message}",
        )
    }

    private suspend fun uploadToTmpFilesOrg(fileName: String, bytes: ByteArray): EphemeralUploadResult {
        val response = httpClient.post("https://tmpfiles.org/api/v1/upload") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    },
                ),
            )
        }

        val raw = response.bodyAsText()
        val parsed = json.parseToJsonElement(raw).jsonObject
        val status = parsed["status"]?.jsonPrimitive?.content ?: ""
        val url = parsed["data"]?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""

        return EphemeralUploadResult(
            success = status == "success" && url.isNotBlank(),
            providerUsed = "storage.to / tmpfiles.org (Zero-Auth Cloud Drop)",
            fileName = fileName,
            sizeBytes = bytes.size.toLong(),
            downloadUrl = url,
            expiresInfo = "60 minutos",
            message = if (url.isNotBlank()) "Archivo disponible temporalmente sin login." else "Error: $raw",
        )
    }

    private suspend fun uploadToMultipartEndpoint(
        endpointUrl: String,
        fileName: String,
        bytes: ByteArray,
        label: String,
    ): EphemeralUploadResult {
        val response = httpClient.post(endpointUrl) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    },
                ),
            )
        }
        val raw = response.bodyAsText().trim()
        val ok = response.status.value in 200..299
        return EphemeralUploadResult(
            success = ok,
            providerUsed = label,
            fileName = fileName,
            sizeBytes = bytes.size.toLong(),
            downloadUrl = raw.take(300),
            expiresInfo = "Según políticas del servidor",
            message = if (ok) "Subida completada." else "HTTP ${response.status.value}: $raw",
        )
    }
}
