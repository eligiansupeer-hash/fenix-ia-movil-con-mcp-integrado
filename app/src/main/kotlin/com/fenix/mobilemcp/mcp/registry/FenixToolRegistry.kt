package com.fenix.mobilemcp.mcp.registry

import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/**
 * Categorías funcionales de habilidades (Skills) de Fénix Mobile.
 */
enum class FenixSkillCategory(
    val id: String,
    val displayName: String,
    val description: String,
) {
    ARCHIVOS(
        "archivos",
        "Gestión Universal de Archivos",
        "Búsqueda profunda, lectura, escritura universal, inserción, grep, clasificación, carpetas y borrado seguro 100% Non-Root.",
    ),
    MEMORIA(
        "memoria",
        "Memoria Soberana SQLite",
        "Almacenamiento persistente clave-valor y consultas locales estructuradas.",
    ),
    DISPOSITIVO(
        "dispositivo",
        "Hardware y Estado del Móvil",
        "Cámaras, sensores, aplicaciones instaladas, portapapeles y métricas físicas.",
    ),
    SEGURIDAD(
        "seguridad",
        "Perímetro y Privacidad",
        "Inmunidad en calle, zonas seguras, auditoría de delivery y anonimización NER local de datos sensibles.",
    ),
    RED(
        "red",
        "Red y Conectividad",
        "Túneles Cloudflare Zero Trust, estado de cuotas de proveedores y conectividad soberana.",
    ),
    ACCESIBILIDAD(
        "accesibilidad",
        "Automatización de Pantalla",
        "Control a demanda del servicio de accesibilidad, clics, escritura y gestos remotos.",
    ),
    CONECTORES(
        "conectores",
        "Conectores Dinámicos OAuth",
        "Integraciones externas con Google Drive, Gmail, GitHub, WhatsApp y Supabase.",
    ),
    DIAGNOSTICO(
        "diagnostico",
        "Caja Negra y Telemetría",
        "Métricas del sistema, diagnóstico de fallos, eventos de latencia y auditoría permanente.",
    ),
    GENERAL(
        "general",
        "Operaciones Generales",
        "Autodescubrimiento y meta-habilidades del asistente.",
    );

    companion object {
        fun fromId(id: String): FenixSkillCategory =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GENERAL
    }
}

/**
 * Definición interna de una herramienta registrada en Fénix.
 */
data class FenixTool(
    val toolName: String,
    val name: String,
    val description: String,
    val inputSchema: ToolSchema,
    val handler: suspend (CallToolRequest) -> CallToolResult,
    val category: FenixSkillCategory = FenixToolRegistry.inferCategory(name),
)

/**
 * Resultado de una ejecución in-process de herramienta con métricas de rendimiento.
 */
data class FenixToolExecutionResult(
    val result: CallToolResult,
    val durationNs: Long,
    val durationMs: Double,
    val isSuccess: Boolean,
    val outputText: String,
)

/**
 * Dispatcher en memoria de herramientas MCP Fénix.
 * Permite a Fénix IA ejecutar cualquier herramienta registrada en el mismo proceso
 * con latencia < 0.05 ms (50 microsegundos), sin loopback HTTP/Ktor ni serialización de red.
 */
object FenixToolRegistry {
    private val tools = ConcurrentHashMap<String, FenixTool>()

    fun register(tool: FenixTool) {
        // Remove any previous entry with the same base toolName (e.g., when transitioning
        // from unprefixed startup registration to prefixed McpServer registration) so
        // /openapi.json and POST /mcp maintain exact 1:1 parity.
        val existingKey = tools.entries.firstOrNull { it.value.toolName == tool.toolName && it.key != tool.name }?.key
        if (existingKey != null) {
            tools.remove(existingKey)
        }
        tools[tool.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
        val byBase = tools.entries.firstOrNull { it.value.toolName == name }?.key
        if (byBase != null) {
            tools.remove(byBase)
        }
    }

    fun get(name: String): FenixTool? =
        tools[name] ?: tools.values.firstOrNull { it.toolName == name || it.name == "android_$name" }

    fun getAll(): List<FenixTool> = tools.values.sortedBy { it.name }

    fun count(): Int = tools.size

    fun clear() {
        tools.clear()
    }

    /**
     * Ejecuta una herramienta directamente en el proceso actual sin pasar por la red.
     */
    suspend fun executeInProcess(
        name: String,
        arguments: JsonObject? = null,
    ): FenixToolExecutionResult {
        val tool = get(name)
        if (tool == null) {
            val errResult =
                CallToolResult(
                    content = listOf(TextContent("Error: Herramienta '$name' no encontrada en el registro local")),
                    isError = true,
                )
            return FenixToolExecutionResult(
                result = errResult,
                durationNs = 0L,
                durationMs = 0.0,
                isSuccess = false,
                outputText = "Herramienta '$name' no encontrada",
            )
        }

        val startNs = System.nanoTime()
        val request =
            CallToolRequest(
                params =
                    CallToolRequestParams(
                        name = tool.name,
                        arguments = arguments,
                    ),
            )

        return try {
            val result = tool.handler(request)
            val durationNs = System.nanoTime() - startNs
            val durationMs = durationNs / 1_000_000.0
            val text = result.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
            FenixToolExecutionResult(
                result = result,
                durationNs = durationNs,
                durationMs = durationMs,
                isSuccess = result.isError != true,
                outputText = text,
            )
        } catch (e: Exception) {
            val durationNs = System.nanoTime() - startNs
            val durationMs = durationNs / 1_000_000.0
            val errResult =
                CallToolResult(
                    content = listOf(TextContent("Error al ejecutar $name: ${e.message ?: "Fallo desconocido"}")),
                    isError = true,
                )
            FenixToolExecutionResult(
                result = errResult,
                durationNs = durationNs,
                durationMs = durationMs,
                isSuccess = false,
                outputText = "Error: ${e.message}",
            )
        }
    }

    /**
     * Deduce la categoría temática de una herramienta en base a su nombre o prefijo.
     */
    fun inferCategory(name: String): FenixSkillCategory {
        val lower = name.lowercase()
        return when {
            lower.startsWith("file_") || lower.contains("file") || lower.contains("storage_location") -> FenixSkillCategory.ARCHIVOS
            lower.contains("memory") || lower.contains("session") || lower.contains("sesion") -> FenixSkillCategory.MEMORIA
            lower.contains("camera") || lower.contains("clipboard") || lower.contains("app") || lower.contains("flash") -> FenixSkillCategory.DISPOSITIVO
            lower.contains("zone") || lower.contains("street") || lower.contains("sanitize") || lower.contains("immunity") -> FenixSkillCategory.SEGURIDAD
            lower.contains("cloudflare") || lower.contains("cooldown") || lower.contains("route") -> FenixSkillCategory.RED
            lower.contains("accessibility") || lower.contains("click") || lower.contains("node") || lower.contains("type") || lower.contains("scroll") -> FenixSkillCategory.ACCESIBILIDAD
            lower.contains("conector") || lower.contains("connector") || lower.contains("google") || lower.contains("gmail") || lower.contains("drive") || lower.contains("github") || lower.contains("whatsapp") || lower.contains("supabase") -> FenixSkillCategory.CONECTORES
            lower.contains("blackbox") || lower.contains("diagnostic") || lower.contains("telemetry") -> FenixSkillCategory.DIAGNOSTICO
            lower.contains("skill") || lower.contains("habilidad") -> FenixSkillCategory.GENERAL
            else -> FenixSkillCategory.GENERAL
        }
    }

    /**
     * Retorna las herramientas pertenecientes a una categoría específica.
     */
    fun getByCategory(category: FenixSkillCategory): List<FenixTool> =
        tools.values.filter { it.category == category }

    /**
     * Genera un catálogo sintético y liviano de habilidades para autodescubrimiento dinámico.
     */
    fun getSkillCatalog(): List<JsonObject> {
        return FenixSkillCategory.values().filter { it != FenixSkillCategory.GENERAL }.map { cat ->
            val catTools = getByCategory(cat)
            buildJsonObject {
                put("id", cat.id)
                put("nombre", cat.displayName)
                put("descripcion", cat.description)
                put("cantidad_herramientas", catTools.size)
                put("herramientas", JsonArray(catTools.map { JsonPrimitive(it.name) }))
            }
        }
    }

    /**
     * Analiza el requerimiento del usuario y detecta qué habilidades coinciden con la intención
     * para activarlas a demanda de forma instantánea sin volcar todo el catálogo.
     */
    fun detectRelevantCategories(userText: String): Set<FenixSkillCategory> {
        val lower = userText.lowercase()
        val detected = mutableSetOf<FenixSkillCategory>()

        if (lower.contains("archivo") || lower.contains("carpeta") || lower.contains("directorio") ||
            lower.contains("documento") || lower.contains("pdf") || lower.contains("txt") ||
            lower.contains("leer archivo") || lower.contains("escribir archivo") || lower.contains("buscar archivo") ||
            lower.contains("grep") || lower.contains("clasificar") || lower.contains("sdcard") ||
            lower.contains("almacenamiento") || lower.contains("insertar en")
        ) {
            detected.add(FenixSkillCategory.ARCHIVOS)
        }

        if (lower.contains("memoria") || lower.contains("guardar en memoria") || lower.contains("recordar") ||
            lower.contains("recuperar memoria") || lower.contains("clave-valor") || lower.contains("sqlite") ||
            lower.contains("sesion") || lower.contains("sesión") || lower.contains("conversacion") ||
            lower.contains("conversación") || lower.contains("historial")
        ) {
            detected.add(FenixSkillCategory.MEMORIA)
        }

        if (lower.contains("app") || lower.contains("aplicacion") || lower.contains("aplicaciones") ||
            lower.contains("camara") || lower.contains("camaras") || lower.contains("foto") ||
            lower.contains("portapapeles") || lower.contains("copiar") || lower.contains("pegar") ||
            lower.contains("bateria")
        ) {
            detected.add(FenixSkillCategory.DISPOSITIVO)
        }

        if (lower.contains("zona") || lower.contains("segura") || lower.contains("peligro") ||
            lower.contains("calle") || lower.contains("inmunidad") || lower.contains("delivery") ||
            lower.contains("rappi") || lower.contains("pedidosya") || lower.contains("uber") ||
            lower.contains("sanitizar") || lower.contains("anonimizar") || lower.contains("dni") ||
            lower.contains("tarjeta") || lower.contains("privacidad")
        ) {
            detected.add(FenixSkillCategory.SEGURIDAD)
        }

        if (lower.contains("cloudflare") || lower.contains("tunel") || lower.contains("cooldown") ||
            lower.contains("cuota") || lower.contains("proveedor") || lower.contains("red")
        ) {
            detected.add(FenixSkillCategory.RED)
        }

        if (lower.contains("accesibilidad") || lower.contains("permiso") || lower.contains("tocar pantalla") ||
            lower.contains("clic") || lower.contains("escribir en pantalla")
        ) {
            detected.add(FenixSkillCategory.ACCESIBILIDAD)
        }

        if (lower.contains("conector") || lower.contains("conectores") || lower.contains("oauth") ||
            lower.contains("gmail") || lower.contains("correo") || lower.contains("drive") ||
            lower.contains("github") || lower.contains("whatsapp") || lower.contains("supabase")
        ) {
            detected.add(FenixSkillCategory.CONECTORES)
        }

        if (lower.contains("diagnostico") || lower.contains("caja negra") || lower.contains("telemetria") ||
            lower.contains("auditar") || lower.contains("blackbox") || lower.contains("metricas")
        ) {
            detected.add(FenixSkillCategory.DIAGNOSTICO)
        }

        return detected
    }

    /**
     * Exporta las especificaciones de herramientas en formato OpenAI Tools para LLMs.
     * Si no se indican categorías activas, solo expone la meta-herramienta de autodescubrimiento
     * para proteger el contexto del modelo y ahorrar cuotas drásticamente.
     */
    fun toOpenAiToolDefinitions(
        activeCategories: Set<FenixSkillCategory>? = null,
        includeDiscovery: Boolean = true,
    ): List<JsonObject> {
        val selectedTools = if (activeCategories == null) {
            tools.values.toList()
        } else {
            val list = mutableListOf<FenixTool>()
            tools.values.forEach { tool ->
                if (activeCategories.contains(tool.category)) {
                    list.add(tool)
                } else if (includeDiscovery && (tool.category == FenixSkillCategory.GENERAL || tool.name.contains("skill"))) {
                    list.add(tool)
                }
            }
            list
        }

        return selectedTools.map { tool ->
            buildJsonObject {
                put("type", "function")
                put(
                    "function",
                    buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        tool.inputSchema.properties?.let { props ->
                            put(
                                "parameters",
                                buildJsonObject {
                                    put("type", "object")
                                    put("properties", props)
                                    tool.inputSchema.required?.let { req ->
                                        put(
                                            "required",
                                            JsonArray(req.map { JsonPrimitive(it) }),
                                        )
                                    }
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}


