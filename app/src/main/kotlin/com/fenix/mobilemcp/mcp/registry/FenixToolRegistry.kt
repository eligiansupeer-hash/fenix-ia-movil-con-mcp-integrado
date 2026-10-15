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
 * Definición interna de una herramienta registrada en Fénix.
 */
data class FenixTool(
    val toolName: String,
    val name: String,
    val description: String,
    val inputSchema: ToolSchema,
    val handler: suspend (CallToolRequest) -> CallToolResult,
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
 * Permite a F-Chat ejecutar cualquier herramienta registrada en el mismo proceso
 * con latencia < 0.05 ms (50 microsegundos), sin loopback HTTP/Ktor ni serialización de red.
 */
object FenixToolRegistry {
    private val tools = ConcurrentHashMap<String, FenixTool>()

    fun register(tool: FenixTool) {
        tools[tool.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun get(name: String): FenixTool? = tools[name]

    fun getAll(): List<FenixTool> = tools.values.toList()

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
        val tool = tools[name]
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
                        name = name,
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
     * Exporta las especificaciones de herramientas en formato OpenAI Tools para LLMs.
     */
    fun toOpenAiToolDefinitions(): List<JsonObject> {
        return tools.values.map { tool ->
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

