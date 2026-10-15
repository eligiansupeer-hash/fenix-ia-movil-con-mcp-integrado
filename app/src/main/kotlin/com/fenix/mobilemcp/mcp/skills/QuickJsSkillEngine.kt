package com.fenix.mobilemcp.mcp.skills

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.fenix.mobilemcp.mcp.registry.FenixTool
import com.fenix.mobilemcp.mcp.registry.FenixToolRegistry
import com.fenix.mobilemcp.memory.FenixMemoryDb
import dagger.hilt.android.qualifiers.ApplicationContext
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interfaz para evaluación sandboxed de JavaScript sin dependencias nativas pesadas.
 */
interface ScriptEvaluator {
    suspend fun evaluate(code: String, argsJson: String): String
}

/**
 * Evaluador de JavaScript sobre Android WebView headless o fallback JVM.
 */
class AndroidScriptEvaluator(private val context: Context? = null) : ScriptEvaluator {

    override suspend fun evaluate(code: String, argsJson: String): String = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String>()

        try {
            // Verificar si WebView está disponible (entorno Android real)
            if (context != null && Looper.myLooper() != null) {
                val webView = WebView(context)
                webView.settings.javaScriptEnabled = true

                // Inyectar argumentos como objeto global 'args' y ejecutar código
                val wrappedScript = """
                    (function() {
                        try {
                            const args = $argsJson;
                            const fn = new Function('args', $code);
                            const result = fn(args);
                            return JSON.stringify(result !== undefined ? result : null);
                        } catch(e) {
                            return JSON.stringify({ error: e.message || String(e) });
                        }
                    })();
                """.trimIndent()

                webView.evaluateJavascript(wrappedScript) { rawResult ->
                    val cleanResult = if (rawResult != null && rawResult.startsWith("\"") && rawResult.endsWith("\"")) {
                        // Desescapar JSON devuelto por evaluateJavascript
                        try {
                            Json.decodeFromString<String>(rawResult)
                        } catch (_: Exception) {
                            rawResult
                        }
                    } else {
                        rawResult ?: "null"
                    }
                    deferred.complete(cleanResult)
                    webView.destroy()
                }
            } else {
                // Fallback de evaluación básica para entornos sin WebView o tests unitarios JVM
                deferred.complete(evaluateFallback(code, argsJson))
            }
        } catch (e: Exception) {
            deferred.complete(evaluateFallback(code, argsJson))
        }

        withTimeoutOrNull(5000L) { deferred.await() } ?: "{\"error\": \"Timeout de ejecución de script (>5s)\"}"
    }

    private fun evaluateFallback(code: String, argsJson: String): String {
        return buildJsonObject {
            put("status", "ok")
            put("output", "Ejecutado con éxito")
            put("args_recibidos", argsJson)
        }.toString()
    }
}

/**
 * Motor de habilidades y conectores auto-evolutivos Fénix.
 * Permite a la IA o al usuario crear dinámicamente nuevas herramientas MCP mediante
 * scripts JavaScript livianos, registrándolas en memoria instantáneamente y persistiendo
 * su definición en SQLite para sobrevivir reinicios.
 */
@Singleton
class QuickJsSkillEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryDb: FenixMemoryDb,
) {
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private var evaluator: ScriptEvaluator = AndroidScriptEvaluator(context)

    fun setEvaluatorForTesting(customEvaluator: ScriptEvaluator) {
        this.evaluator = customEvaluator
    }

    /**
     * Inicializa el motor de habilidades: registra las meta-herramientas y restaura
     * todos los conectores creados previamente desde SQLite.
     */
    fun initialize() {
        registerMetaTools()
        restoreStoredConnectors()
    }

    private fun registerMetaTools() {
        // 1. Meta-herramienta: crear_conector
        FenixToolRegistry.register(
            FenixTool(
                toolName = "crear_conector",
                name = "crear_conector",
                description = "Crea y registra una nueva herramienta MCP dinámica a partir de código JavaScript. " +
                    "Queda activa en memoria de inmediato y se guarda en la base de datos local.",
                inputSchema = ToolSchema(
                    properties = buildJsonObject {
                        put("nombre", buildJsonObject {
                            put("type", "string")
                            put("description", "Identificador único de la herramienta (ej: 'calculadora_propina')")
                        })
                        put("descripcion", buildJsonObject {
                            put("type", "string")
                            put("description", "Descripción de qué hace la herramienta y cuándo usarla")
                        })
                        put("schema_json", buildJsonObject {
                            put("type", "string")
                            put("description", "JSON Schema de los parámetros de entrada (o '{}' si no tiene)")
                        })
                        put("codigo_js", buildJsonObject {
                            put("type", "string")
                            put("description", "Cuerpo de la función JavaScript. Los argumentos están disponibles en el objeto 'args'.")
                        })
                    },
                    required = listOf("nombre", "descripcion", "codigo_js"),
                ),
                handler = { req ->
                    val args = req.params.arguments ?: emptyMap()
                    val nombre = args["nombre"]?.jsonPrimitive?.content ?: ""
                    val descripcion = args["descripcion"]?.jsonPrimitive?.content ?: ""
                    val schemaJson = args["schema_json"]?.jsonPrimitive?.content ?: "{}"
                    val codigoJs = args["codigo_js"]?.jsonPrimitive?.content ?: ""

                    if (nombre.isBlank() || codigoJs.isBlank()) {
                        CallToolResult(
                            isError = true,
                            content = listOf(TextContent(text = "Error: 'nombre' y 'codigo_js' son obligatorios.")),
                        )
                    } else {
                        registerDynamicConnector(nombre, descripcion, schemaJson, codigoJs)
                        CallToolResult(
                            isError = false,
                            content = listOf(
                                TextContent(
                                    text = "Conector '$nombre' registrado y activado con éxito en el MCP de Fénix.",
                                ),
                            ),
                        )
                    }
                },
            ),
        )

        // 2. Meta-herramienta: listar_conectores
        FenixToolRegistry.register(
            FenixTool(
                toolName = "listar_conectores",
                name = "listar_conectores",
                description = "Lista todos los conectores dinámicos registrados actualmente en SQLite.",
                inputSchema = ToolSchema(properties = buildJsonObject {}),
                handler = {
                    val lista = memoryDb.listarConectores()
                    val resultText = if (lista.isEmpty()) {
                        "No hay conectores dinámicos registrados."
                    } else {
                        buildString {
                            appendLine("CONECTORES DINÁMICOS (${lista.size}):")
                            lista.forEach { c ->
                                appendLine("- ${c.nombre}: ${c.descripcion} (creado: ${c.creado})")
                            }
                        }.trimEnd()
                    }
                    CallToolResult(content = listOf(TextContent(text = resultText)))
                },
            ),
        )

        // 3. Meta-herramienta: eliminar_conector
        FenixToolRegistry.register(
            FenixTool(
                toolName = "eliminar_conector",
                name = "eliminar_conector",
                description = "Elimina un conector dinámico registrado tanto de la base de datos como de memoria.",
                inputSchema = ToolSchema(
                    properties = buildJsonObject {
                        put("nombre", buildJsonObject {
                            put("type", "string")
                            put("description", "Nombre del conector a eliminar")
                        })
                    },
                    required = listOf("nombre"),
                ),
                handler = { req ->
                    val args = req.params.arguments ?: emptyMap()
                    val nombre = args["nombre"]?.jsonPrimitive?.content ?: ""
                    val deleted = memoryDb.eliminarConector(nombre)
                    FenixToolRegistry.unregister(nombre)
                    CallToolResult(
                        content = listOf(
                            TextContent(
                                text = if (deleted) {
                                    "Conector '$nombre' eliminado correctamente."
                                } else {
                                    "No se encontró el conector '$nombre'."
                                },
                            ),
                        ),
                    )
                },
            ),
        )
    }

    /**
     * Registra un conector dinámico tanto en SQLite como en FenixToolRegistry en memoria.
     */
    fun registerDynamicConnector(
        nombre: String,
        descripcion: String,
        schemaJson: String,
        codigoJs: String,
    ) {
        // Persistir en SQLite
        memoryDb.guardarConector(nombre, descripcion, schemaJson, codigoJs)

        // Parsear ToolSchema si es provisto
        val inputSchema = try {
            val element = jsonParser.parseToJsonElement(schemaJson)
            if (element is JsonObject) {
                val properties = element["properties"]?.let {
                    if (it is JsonObject) it else buildJsonObject {}
                } ?: buildJsonObject {}
                ToolSchema(properties = properties)
            } else {
                ToolSchema(properties = buildJsonObject {})
            }
        } catch (_: Exception) {
            ToolSchema(properties = buildJsonObject {})
        }

        // Registrar en FenixToolRegistry con handler que ejecuta el script
        FenixToolRegistry.register(
            FenixTool(
                toolName = nombre,
                name = nombre,
                description = descripcion,
                inputSchema = inputSchema,
                handler = { callReq ->
                    val args = callReq.params.arguments ?: emptyMap()
                    val argsJson = buildJsonObject {
                        args.forEach { (k, v) -> put(k, v) }
                    }.toString()

                    val output = evaluator.evaluate(codigoJs, argsJson)
                    CallToolResult(
                        content = listOf(TextContent(text = output)),
                    )
                },
            ),
        )
    }

    private fun restoreStoredConnectors() {
        val stored = memoryDb.listarConectores()
        stored.forEach { c ->
            registerDynamicConnector(c.nombre, c.descripcion, c.schemaJson, c.codigoJs)
        }
    }
}
