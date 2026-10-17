@file:Suppress("MagicNumber", "TooGenericExceptionCaught", "MaxLineLength")

package com.fenix.mobilemcp.studio

import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

@Serializable
enum class DocumentActionType {
    CREATED,
    MODIFIED,
    READ_BY_AI,
    OPENED_BY_USER,
    EXECUTED,
}

@Serializable
data class WorkspaceDocumentTab(
    val path: String,
    val name: String,
    val language: String,
    val content: String,
    val lastAction: DocumentActionType,
    val lastActor: String,
    val timestampMs: Long,
    val isDirty: Boolean = false,
)

@Serializable
data class StudioConsoleOutput(
    val title: String,
    val stdout: String,
    val stderr: String,
    val engineUsed: String,
    val coresAssigned: String,
    val durationMs: Long,
    val success: Boolean,
    val timestampMs: Long = System.currentTimeMillis(),
)

/**
 * Bus en tiempo real del Espacio de Trabajo (F-Studio).
 * Sincroniza instantáneamente los documentos que la IA está leyendo, escribiendo o modificando
 * para que el usuario pueda verlos en vivo, editarlos o compilarlos como en VS Code.
 */
object LiveWorkspaceBus {

    private val defaultWorkspaceDir: File by lazy {
        val appFilesBase = System.getProperty("java.io.tmpdir")?.let { File(it).parentFile }
        val internalWorkspace = if (appFilesBase != null) {
            File(appFilesBase, "files/fstudio_workspace")
        } else {
            val base = Environment.getExternalStorageDirectory() ?: File("/sdcard")
            File(base, "Download/FenixWorkspace")
        }
        internalWorkspace.apply {
            if (!exists()) runCatching { mkdirs() }
        }
    }

    private val _openTabsFlow = MutableStateFlow<List<WorkspaceDocumentTab>>(defaultStarterTabs())
    val openTabsFlow: StateFlow<List<WorkspaceDocumentTab>> = _openTabsFlow.asStateFlow()

    private val _activeTabPathFlow = MutableStateFlow<String>(_openTabsFlow.value.firstOrNull()?.path ?: "")
    val activeTabPathFlow: StateFlow<String> = _activeTabPathFlow.asStateFlow()

    private val _lastAiActivityBanner = MutableStateFlow<String?>(null)
    val lastAiActivityBanner: StateFlow<String?> = _lastAiActivityBanner.asStateFlow()

    private val _consoleOutputFlow = MutableStateFlow<StudioConsoleOutput?>(null)
    val consoleOutputFlow: StateFlow<StudioConsoleOutput?> = _consoleOutputFlow.asStateFlow()

    fun getWorkspaceRootPath(): String = defaultWorkspaceDir.absolutePath

    fun detectLanguageFromName(fileName: String): String {
        val ext = fileName.substringAfterLast(".", "").lowercase(Locale.ROOT)
        return when (ext) {
            "py" -> "python"
            "js", "mjs" -> "javascript"
            "ts", "tsx" -> "typescript"
            "kt", "kts" -> "kotlin"
            "c", "h" -> "c"
            "cpp", "cc", "cxx", "hpp" -> "cpp"
            "rs" -> "rust"
            "go" -> "go"
            "sh", "bash" -> "bash"
            "sql" -> "sql"
            "html", "htm" -> "html"
            "css" -> "css"
            "json" -> "json"
            "md", "markdown" -> "markdown"
            "java" -> "java"
            else -> "plaintext"
        }
    }

    /**
     * Notifica que un archivo fue creado, leído o modificado por la IA o por el usuario,
     * abriéndolo o actualizándolo en vivo en las pestañas de F-Studio.
     */
    fun notifyFileTouched(
        file: File,
        action: DocumentActionType,
        actor: String = "Agente IA (MCP)",
        contentOverride: String? = null,
    ) {
        val textContent = contentOverride ?: runCatching {
            if (file.exists() && file.isFile && file.length() <= 512 * 1024) {
                file.readText(StandardCharsets.UTF_8)
            } else {
                ""
            }
        }.getOrDefault("")

        val tab = WorkspaceDocumentTab(
            path = file.absolutePath,
            name = file.name,
            language = detectLanguageFromName(file.name),
            content = textContent,
            lastAction = action,
            lastActor = actor,
            timestampMs = System.currentTimeMillis(),
            isDirty = false,
        )

        val current = _openTabsFlow.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.path == tab.path }
        if (existingIdx >= 0) {
            current[existingIdx] = tab
        } else {
            current.add(0, tab)
        }

        _openTabsFlow.value = current.take(12)
        _activeTabPathFlow.value = tab.path

        val verb = when (action) {
            DocumentActionType.CREATED -> "creó"
            DocumentActionType.MODIFIED -> "editó en vivo"
            DocumentActionType.READ_BY_AI -> "inspeccionó"
            DocumentActionType.OPENED_BY_USER -> "abrió"
            DocumentActionType.EXECUTED -> "ejecutó"
        }
        _lastAiActivityBanner.value = "⚡ $actor $verb ${file.name}"
    }

    fun updateActiveTabContent(path: String, newContent: String) {
        _openTabsFlow.value = _openTabsFlow.value.map { tab ->
            if (tab.path == path) {
                tab.copy(content = newContent, isDirty = true, timestampMs = System.currentTimeMillis())
            } else {
                tab
            }
        }
    }

    fun selectTab(path: String) {
        _activeTabPathFlow.value = path
    }

    fun closeTab(path: String) {
        val remaining = _openTabsFlow.value.filterNot { it.path == path }
        _openTabsFlow.value = remaining
        if (_activeTabPathFlow.value == path) {
            _activeTabPathFlow.value = remaining.firstOrNull()?.path ?: ""
        }
    }

    fun publishConsoleOutput(output: StudioConsoleOutput) {
        _consoleOutputFlow.value = output
    }

    fun clearConsole() {
        _consoleOutputFlow.value = null
    }

    private fun defaultStarterTabs(): List<WorkspaceDocumentTab> {
        val welcomeMd = File(defaultWorkspaceDir, "BIENVENIDA_FSTUDIO.md")
        val demoPy = File(defaultWorkspaceDir, "demo_agente.py")
        val demoC = File(defaultWorkspaceDir, "motor_nativo.c")

        val mdContent = """
            # 🚀 Bienvenido a F-Studio (IDE Móvil + Workspace en Vivo)
            
            Este es tu entorno de programación y visualización documental integrado en **Fénix Mobile MCP**.
            
            ### ¿Qué puedes hacer aquí?
            1. **Ver en vivo lo que hace la IA:** Cada vez que un agente en **F-Chat**, **Gemini**, **Claude** o **Manus** cree o edite un archivo en tu móvil, aparecerá automáticamente en una pestaña aquí arriba.
            2. **Programar y Compilar en Múltiples Lenguajes:** Escribe en **Python, JavaScript, C, C++, Rust, Go, Bash o SQL** y presiona el botón **▶ Ejecutar**.
            3. **Cuidado Inteligente de RAM y Núcleos CPU:** El orquestador detecta los núcleos *Efficiency* y *Performance* de tu teléfono o delega compilaciones pesadas a servidores virtuales gratuitos sin login (**Wandbox / Piston / H2.nexus**).
        """.trimIndent()

        val pyContent = """
            # Ejemplo Python 3.12 en F-Studio
            import platform
            import math

            def analizar_sistema():
                print("=== Fénix Polyglot Runtime ===")
                print(f"Python Version : {platform.python_version()}")
                print(f"Arquitectura   : {platform.machine()}")
                primos = [n for n in range(2, 50) if all(n % d != 0 for d in range(2, int(math.isqrt(n)) + 1))]
                print(f"Primos calculados ({len(primos)}): {primos}")

            if __name__ == "__main__":
                analizar_sistema()
        """.trimIndent()

        val cContent = """
            // Ejemplo C / Clang 17 en F-Studio
            #include <stdio.h>

            int main() {
                printf("¡Hola desde el compilador C/Clang en Fénix Studio!\n");
                long suma = 0;
                for (int i = 1; i <= 1000; i++) {
                    suma += i;
                }
                printf("Suma verificada (1..1000) = %ld\n", suma);
                return 0;
            }
        """.trimIndent()

        runCatching {
            if (!welcomeMd.exists()) welcomeMd.writeText(mdContent, StandardCharsets.UTF_8)
            if (!demoPy.exists()) demoPy.writeText(pyContent, StandardCharsets.UTF_8)
            if (!demoC.exists()) demoC.writeText(cContent, StandardCharsets.UTF_8)
        }

        return listOf(
            WorkspaceDocumentTab(
                path = welcomeMd.absolutePath,
                name = welcomeMd.name,
                language = "markdown",
                content = mdContent,
                lastAction = DocumentActionType.CREATED,
                lastActor = "F-Studio Core",
                timestampMs = System.currentTimeMillis(),
            ),
            WorkspaceDocumentTab(
                path = demoPy.absolutePath,
                name = demoPy.name,
                language = "python",
                content = pyContent,
                lastAction = DocumentActionType.CREATED,
                lastActor = "AXIOM Polyglot Architect",
                timestampMs = System.currentTimeMillis(),
            ),
            WorkspaceDocumentTab(
                path = demoC.absolutePath,
                name = demoC.name,
                language = "c",
                content = cContent,
                lastAction = DocumentActionType.CREATED,
                lastActor = "Clang/LLVM Kit",
                timestampMs = System.currentTimeMillis(),
            ),
        )
    }
}
