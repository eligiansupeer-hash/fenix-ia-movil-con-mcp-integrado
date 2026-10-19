package com.fenix.mobilemcp.services.storage

import android.content.Context
import android.os.Environment
import com.fenix.mobilemcp.mcp.McpToolException
import com.fenix.mobilemcp.security.FenixFileSecurityFilter
import com.fenix.mobilemcp.telemetry.FenixBlackBoxRecorder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Motor de Gestión Universal de Archivos para Fénix Mobile MCP.
 *
 * Opera 100% Sin Root sobre el almacenamiento compartido de Android (/sdcard).
 * Implementa búsqueda recursiva, clasificación por categorías y peso,
 * búsqueda interna de texto (grep), inserción quirúrgica de líneas y operaciones seguras.
 */
object FenixFileManager {

    private val storageRoot: File by lazy {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "fenix").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private fun resolveTarget(path: String): File {
        val trimmed = path.trim()
        val file = if (trimmed.startsWith("/") || trimmed.startsWith("/sdcard") || trimmed.startsWith("/storage/")) {
            File(trimmed)
        } else {
            File(storageRoot, trimmed)
        }
        FenixFileSecurityFilter.validateSafeAccess(file)
        return file
    }

    @Serializable
    data class FileMatch(
        val name: String,
        val path: String,
        val sizeBytes: Long,
        val isDirectory: Boolean,
        val lastModifiedMs: Long
    )

    fun searchFiles(
        startPath: String = "",
        query: String = "",
        extension: String? = null,
        minSizeBytes: Long = 0,
        maxSizeBytes: Long = Long.MAX_VALUE,
        maxResults: Int = 100,
        maxDepth: Int = 8
    ): List<FileMatch> {
        val root = resolveTarget(startPath)
        if (!root.exists() || !root.isDirectory) {
            throw McpToolException.InvalidParams("La ruta inicial no existe o no es una carpeta: ${root.path}")
        }

        val results = mutableListOf<FileMatch>()
        val cleanExt = extension?.trim()?.removePrefix(".")?.lowercase(Locale.ROOT)
        val cleanQuery = query.trim().lowercase(Locale.ROOT)

        fun walk(dir: File, currentDepth: Int) {
            if (currentDepth > maxDepth || results.size >= maxResults) return
            val children = dir.listFiles() ?: return

            for (file in children) {
                if (results.size >= maxResults) break
                val nameLower = file.name.lowercase(Locale.ROOT)

                // Filtro de seguridad perimetral
                try {
                    FenixFileSecurityFilter.validateSafeAccess(file)
                } catch (_: Exception) {
                    continue
                }

                if (file.isDirectory) {
                    if (cleanQuery.isNotEmpty() && nameLower.contains(cleanQuery)) {
                        results.add(
                            FileMatch(
                                name = file.name,
                                path = file.absolutePath,
                                sizeBytes = 0L,
                                isDirectory = true,
                                lastModifiedMs = file.lastModified()
                            )
                        )
                    }
                    walk(file, currentDepth + 1)
                } else {
                    val extMatch = cleanExt == null || nameLower.endsWith(".$cleanExt")
                    val queryMatch = cleanQuery.isEmpty() || nameLower.contains(cleanQuery)
                    val sizeMatch = file.length() in minSizeBytes..maxSizeBytes

                    if (extMatch && queryMatch && sizeMatch) {
                        results.add(
                            FileMatch(
                                name = file.name,
                                path = file.absolutePath,
                                sizeBytes = file.length(),
                                isDirectory = false,
                                lastModifiedMs = file.lastModified()
                            )
                        )
                    }
                }
            }
        }

        walk(root, 0)
        return results
    }

    fun countAndClassify(targetPath: String = ""): JsonObject {
        val root = resolveTarget(targetPath)
        if (!root.exists() || !root.isDirectory) {
            throw McpToolException.InvalidParams("La carpeta no existe: ${root.path}")
        }

        var totalFiles = 0L
        var totalBytes = 0L
        val categoryCounts = mutableMapOf<String, Long>()
        val categoryBytes = mutableMapOf<String, Long>()
        val largeFiles = mutableListOf<FileMatch>()

        val docExts = setOf("pdf", "doc", "docx", "txt", "md", "xls", "xlsx", "csv", "ppt", "pptx", "epub")
        val codeExts = setOf("kt", "java", "py", "js", "ts", "json", "xml", "html", "css", "sh", "sql", "yaml", "yml")
        val imgExts = setOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp")
        val audioExts = setOf("mp3", "wav", "m4a", "aac", "ogg", "flac")
        val videoExts = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp")
        val archiveExts = setOf("zip", "rar", "7z", "tar", "gz", "apk")

        fun getCategory(ext: String): String = when {
            ext in docExts -> "Documentos"
            ext in codeExts -> "Código"
            ext in imgExts -> "Imágenes"
            ext in audioExts -> "Audio"
            ext in videoExts -> "Video"
            ext in archiveExts -> "Archivos Comprimidos"
            else -> "Otros"
        }

        root.walkTopDown().maxDepth(10).forEach { file ->
            if (file.isFile) {
                try {
                    FenixFileSecurityFilter.validateSafeAccess(file)
                } catch (_: Exception) {
                    return@forEach
                }

                val size = file.length()
                totalFiles++
                totalBytes += size

                val ext = file.extension.lowercase(Locale.ROOT)
                val cat = getCategory(ext)
                categoryCounts[cat] = (categoryCounts[cat] ?: 0L) + 1L
                categoryBytes[cat] = (categoryBytes[cat] ?: 0L) + size

                // Archivos pesados (> 50 MB)
                if (size > 50 * 1024 * 1024) {
                    largeFiles.add(
                        FileMatch(
                            name = file.name,
                            path = file.absolutePath,
                            sizeBytes = size,
                            isDirectory = false,
                            lastModifiedMs = file.lastModified()
                        )
                    )
                }
            }
        }

        val topHeavy = largeFiles.sortedByDescending { it.sizeBytes }.take(15)

        return buildJsonObject {
            put("scanned_path", root.absolutePath)
            put("total_files", totalFiles)
            put("total_size_mb", totalBytes / (1024 * 1024))
            put("categories", buildJsonObject {
                categoryCounts.forEach { (cat, count) ->
                    val bytes = categoryBytes[cat] ?: 0L
                    put(cat, buildJsonObject {
                        put("count", count)
                        put("size_mb", bytes / (1024 * 1024))
                    })
                }
            })
            put("heavy_files_over_50mb", buildJsonArray {
                topHeavy.forEach {
                    add(buildJsonObject {
                        put("name", it.name)
                        put("path", it.path)
                        put("size_mb", it.sizeBytes / (1024 * 1024))
                    })
                }
            })
        }
    }

    fun grepInFiles(
        startPath: String = "",
        phrase: String,
        fileExtension: String? = null,
        maxFileCount: Int = 50,
        maxMatchesPerFile: Int = 10
    ): JsonObject {
        if (phrase.isBlank()) throw McpToolException.InvalidParams("La frase a buscar no puede estar vacía.")
        val root = resolveTarget(startPath)
        val cleanExt = fileExtension?.trim()?.removePrefix(".")?.lowercase(Locale.ROOT)
        val resultsArray = buildJsonArray {
            var scannedFiles = 0

            root.walkTopDown().maxDepth(6).forEach { file ->
                if (scannedFiles >= maxFileCount) return@forEach
                if (file.isFile && file.length() < 10 * 1024 * 1024) { // Límite de 10 MB para escaneo de texto
                    if (cleanExt != null && !file.name.lowercase(Locale.ROOT).endsWith(".$cleanExt")) {
                        return@forEach
                    }

                    try {
                        FenixFileSecurityFilter.validateSafeAccess(file)
                    } catch (_: Exception) {
                        return@forEach
                    }

                    try {
                        val matches = mutableListOf<JsonObject>()
                        var lineNum = 1
                        file.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                            for (line in lines) {
                                if (line.contains(phrase, ignoreCase = true)) {
                                    matches.add(buildJsonObject {
                                        put("line_number", lineNum)
                                        put("content", line.trim().take(300))
                                    })
                                    if (matches.size >= maxMatchesPerFile) break
                                }
                                lineNum++
                            }
                        }

                        if (matches.isNotEmpty()) {
                            scannedFiles++
                            add(buildJsonObject {
                                put("file_name", file.name)
                                put("file_path", file.absolutePath)
                                put("matches_count", matches.size)
                                put("matches", buildJsonArray { matches.forEach { add(it) } })
                            })
                        }
                    } catch (_: Exception) {
                        // Ignorar archivos binarios no decodificables como UTF-8
                    }
                }
            }
        }

        return buildJsonObject {
            put("query_phrase", phrase)
            put("results_count", resultsArray.size)
            put("results", resultsArray)
        }
    }

    fun insertInFile(
        path: String,
        contentToInsert: String,
        atLineNumber: Int? = null,
        afterPattern: String? = null
    ): JsonObject {
        val file = resolveTarget(path)
        FenixFileSecurityFilter.validateSafeAccess(file, forWrite = true)
        if (!file.exists() || !file.isFile) {
            throw McpToolException.ActionFailed("El archivo no existe o no es un archivo válido: ${file.path}")
        }

        val originalLines = file.readLines(StandardCharsets.UTF_8).toMutableList()
        var insertedIndex = -1

        when {
            atLineNumber != null -> {
                val idx = (atLineNumber - 1).coerceIn(0, originalLines.size)
                originalLines.add(idx, contentToInsert)
                insertedIndex = idx + 1
            }
            afterPattern != null -> {
                val matchIdx = originalLines.indexOfFirst { it.contains(afterPattern, ignoreCase = true) }
                if (matchIdx == -1) {
                    throw McpToolException.ActionFailed("No se encontró el patrón '$afterPattern' en el archivo.")
                }
                originalLines.add(matchIdx + 1, contentToInsert)
                insertedIndex = matchIdx + 2
            }
            else -> {
                originalLines.add(contentToInsert)
                insertedIndex = originalLines.size
            }
        }

        val joinedText = originalLines.joinToString("\n")
        file.writeText(joinedText, StandardCharsets.UTF_8)
        com.fenix.mobilemcp.studio.LiveWorkspaceBus.notifyFileTouched(
            file = file,
            action = com.fenix.mobilemcp.studio.DocumentActionType.MODIFIED,
            actor = "Agente IA (MCP)",
            contentOverride = joinedText,
        )
        FenixBlackBoxRecorder.recordEvent(
            category = "TOOL",
            action = "FILE_INSERT",
            component = "FenixFileManager",
            details = "Insertado en ${file.name} (línea $insertedIndex)"
        )

        return buildJsonObject {
            put("success", true)
            put("path", file.absolutePath)
            put("inserted_at_line", insertedIndex)
            put("total_lines", originalLines.size)
        }
    }

    fun readUniversal(
        path: String,
        startLine: Int = 1,
        maxLines: Int = 300
    ): JsonObject {
        val file = resolveTarget(path)
        FenixFileSecurityFilter.validateSafeAccess(file)
        if (!file.exists() || !file.isFile) {
            throw McpToolException.ActionFailed("El archivo no existe: ${file.path}")
        }

        val lines = file.readLines(StandardCharsets.UTF_8)
        val totalLines = lines.size
        val fromIndex = (startLine - 1).coerceAtLeast(0)
        val toIndex = (fromIndex + maxLines).coerceAtMost(totalLines)

        val slice = if (fromIndex < totalLines) {
            lines.subList(fromIndex, toIndex).joinToString("\n")
        } else {
            ""
        }

        com.fenix.mobilemcp.studio.LiveWorkspaceBus.notifyFileTouched(
            file = file,
            action = com.fenix.mobilemcp.studio.DocumentActionType.READ_BY_AI,
            actor = "Agente IA (MCP)",
        )

        return buildJsonObject {
            put("path", file.absolutePath)
            put("name", file.name)
            put("size_bytes", file.length())
            put("total_lines", totalLines)
            put("start_line", startLine)
            put("lines_returned", toIndex - fromIndex)
            put("content", slice)
            put("has_more", toIndex < totalLines)
        }
    }

    fun writeUniversal(
        path: String,
        content: String,
        append: Boolean = false
    ): JsonObject {
        val file = resolveTarget(path)
        FenixFileSecurityFilter.validateSafeAccess(file, forWrite = true)

        file.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }

        if (append && file.exists()) {
            file.appendText(content, StandardCharsets.UTF_8)
        } else {
            file.writeText(content, StandardCharsets.UTF_8)
        }

        com.fenix.mobilemcp.studio.LiveWorkspaceBus.notifyFileTouched(
            file = file,
            action = if (append) com.fenix.mobilemcp.studio.DocumentActionType.MODIFIED else com.fenix.mobilemcp.studio.DocumentActionType.CREATED,
            actor = "Agente IA (MCP)",
        )

        FenixBlackBoxRecorder.recordEvent(
            category = "TOOL",
            action = if (append) "FILE_APPEND" else "FILE_WRITE",
            component = "FenixFileManager",
            details = "Archivo escrito: ${file.name} (${file.length()} bytes)"
        )

        return buildJsonObject {
            put("success", true)
            put("path", file.absolutePath)
            put("size_bytes", file.length())
            put("append", append)
        }
    }

    fun createDirectory(path: String): JsonObject {
        val dir = resolveTarget(path)
        FenixFileSecurityFilter.validateSafeAccess(dir, forWrite = true)
        val created = if (!dir.exists()) dir.mkdirs() else true

        FenixBlackBoxRecorder.recordEvent(
            category = "TOOL",
            action = "DIR_CREATE",
            component = "FenixFileManager",
            details = "Carpeta creada: ${dir.absolutePath} ($created)"
        )

        return buildJsonObject {
            put("success", created)
            put("path", dir.absolutePath)
            put("already_existed", dir.exists() && !created)
        }
    }

    fun deleteSafe(path: String): JsonObject {
        val target = resolveTarget(path)
        FenixFileSecurityFilter.validateSafeAccess(target, forWrite = true)
        if (!target.exists()) {
            throw McpToolException.ActionFailed("El archivo o carpeta no existe: ${target.path}")
        }

        val deleted = if (target.isDirectory) {
            target.deleteRecursively()
        } else {
            target.delete()
        }

        FenixBlackBoxRecorder.recordEvent(
            category = "TOOL",
            action = "FILE_DELETE",
            component = "FenixFileManager",
            details = "Eliminado: ${target.name} ($deleted)"
        )

        return buildJsonObject {
            put("success", deleted)
            put("path", target.absolutePath)
            put("was_directory", target.isDirectory)
        }
    }
}

