package com.fenix.mobilemcp.mcp.skills

import android.content.Context
import android.webkit.WebView
import com.fenix.mobilemcp.mcp.registry.FenixTool
import com.fenix.mobilemcp.mcp.registry.FenixToolRegistry
import com.fenix.mobilemcp.mcp.tools.LoggedToolRegistrar
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
import kotlinx.serialization.json.jsonObject
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
 * Evaluador de JavaScript sobre Android WebView headless con aislamiento de red y destrucción garantizada.
 * Prohibido devolver fallbacks simulados ("Ejecutado con éxito"): cualquier error o timeout lanza excepción.
 */
class AndroidScriptEvaluator(private val context: Context? = null) : ScriptEvaluator {

    override suspend fun evaluate(code: String, argsJson: String): String = withContext(Dispatchers.Main) {
        val ctx = context ?: throw IllegalStateException("Contexto Android requerido para evaluación JS real")
        val deferred = CompletableDeferred<String>()
        val webView = WebView(ctx)

        try {
            webView.settings.apply {
                javaScriptEnabled = true
                blockNetworkLoads = true
                blockNetworkImage = true
                allowFileAccess = false
                allowContentAccess = false
                domStorageEnabled = false
                databaseEnabled = false
            }

            val codeEscaped = Json.encodeToString(kotlinx.serialization.serializer<String>(), code)
            val wrappedScript = """
                (function() {
                    const __logs = [];
                    const __fmt = function(a) { return a.map(function(x) { return typeof x === 'object' ? JSON.stringify(x) : String(x); }).join(' '); };
                    const console = {
                        log: function() { __logs.push(__fmt(Array.prototype.slice.call(arguments))); },
                        info: function() { __logs.push(__fmt(Array.prototype.slice.call(arguments))); },
                        warn: function() { __logs.push('[WARN] ' + __fmt(Array.prototype.slice.call(arguments))); },
                        error: function() { __logs.push('[ERROR] ' + __fmt(Array.prototype.slice.call(arguments))); }
                    };
                    try {
                        const fetch = function() { throw new Error("Network access blocked in QuickJS sandbox"); };
                        const XMLHttpRequest = function() { throw new Error("Network access blocked in QuickJS sandbox"); };
                        const WebSocket = function() { throw new Error("Network access blocked in QuickJS sandbox"); };
                        const args = $argsJson;
                        const rawCode = $codeEscaped;
                        let result;
                        if (/\breturn\b/.test(rawCode)) {
                            const fn = new Function('args', 'console', 'fetch', 'XMLHttpRequest', 'WebSocket', rawCode);
                            result = fn(args, console, fetch, XMLHttpRequest, WebSocket);
                        } else {
                            result = (0, eval)(rawCode);
                            if (result === undefined && typeof ejecutar === 'function') {
                                result = ejecutar(args);
                            }
                        }
                        if (result && typeof result.then === 'function') {
                            return JSON.stringify({ __fenix_js_error: "Promises/async network calls not allowed in synchronous sandbox" });
                        }
                        const logText = __logs.join('\n');
                        const finalVal = (result !== undefined && result !== null)
                            ? (logText.length > 0 ? logText + '\n' + (typeof result === 'object' ? JSON.stringify(result) : String(result)) : result)
                            : (logText.length > 0 ? logText : "OK");
                        return JSON.stringify({ __fenix_ok: true, value: finalVal });
                    } catch(e) {
                        return JSON.stringify({ __fenix_js_error: e && e.message ? e.message : String(e) });
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(wrappedScript) { rawResult ->
                val cleanResult = if (rawResult != null && rawResult.startsWith("\"") && rawResult.endsWith("\"")) {
                    try {
                        Json.decodeFromString<String>(rawResult)
                    } catch (_: Exception) {
                        rawResult
                    }
                } else {
                    rawResult ?: "null"
                }
                if (!deferred.isCompleted) {
                    deferred.complete(cleanResult)
                }
            }

            val rawJson = withTimeoutOrNull(5000L) { deferred.await() }
                ?: throw IllegalStateException("Timeout de ejecución de script (>5s)")

            val parsed = try {
                Json.parseToJsonElement(rawJson).jsonObject
            } catch (_: Exception) {
                null
            }

            if (parsed != null) {
                val jsErr = parsed["__fenix_js_error"]?.jsonPrimitive?.content
                if (jsErr != null) {
                    throw IllegalStateException("Error en script JS: $jsErr")
                }
                if (parsed["__fenix_ok"]?.jsonPrimitive?.content == "true") {
                    val valElem = parsed["value"]
                    return@withContext when {
                        valElem == null -> "null"
                        valElem is kotlinx.serialization.json.JsonPrimitive && valElem.isString -> valElem.content
                        else -> valElem.toString()
                    }
                }
            }
            rawJson
        } finally {
            runCatching {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }
}

/**
 * Motor de habilidades y conectores auto-evolutivos Fénix.
 * Permite a la IA o al usuario crear dinámicamente nuevas herramientas MCP mediante
 * scripts JavaScript livianos, registrándolas en memoria instantáneamente y persistiendo
 * su definición en SQLite para sobrevivir reinicios sin alterar el timestamp 'creado'.
 */
@Singleton
class QuickJsSkillEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryDb: FenixMemoryDb,
) {
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private var evaluator: ScriptEvaluator = AndroidScriptEvaluator(context)

    @Volatile
    private var activeRegistrar: LoggedToolRegistrar? = null

    fun setEvaluatorForTesting(customEvaluator: ScriptEvaluator) {
        this.evaluator = customEvaluator
    }

    /**
     * Inicializa el motor de habilidades y vincula opcionalmente el LoggedToolRegistrar de Ktor.
     */
    fun initialize(registrar: LoggedToolRegistrar? = null) {
        if (registrar != null) {
            activeRegistrar = registrar
        }
        registerMetaTools(activeRegistrar)
        restoreStoredConnectors(activeRegistrar)
    }

    private fun registerToolBoth(
        registrar: LoggedToolRegistrar?,
        toolName: String,
        name: String,
        description: String,
        inputSchema: ToolSchema,
        handler: suspend (CallToolRequest) -> CallToolResult,
    ) {
        if (registrar != null) {
            registrar.addTool(
                toolName = toolName,
                name = name,
                description = description,
                inputSchema = inputSchema,
                handler = handler,
            )
        } else {
            FenixToolRegistry.register(
                FenixTool(
                    toolName = toolName,
                    name = name,
                    description = description,
                    inputSchema = inputSchema,
                    handler = handler,
                ),
            )
        }
    }

    private fun registerMetaTools(registrar: LoggedToolRegistrar?) {
        // 1. Meta-herramienta: crear_conector
        registerToolBoth(
            registrar = registrar,
            toolName = "crear_conector",
            name = "crear_conector",
            description = "Crea y registra una nueva herramienta MCP dinámica a partir de código JavaScript. " +
                "Queda activa en memoria de inmediato y se guarda en la base de datos local.",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("nombre", buildJsonObject {
                        put("type", "string")
                        put("description", "Identificador único alfanumérico de la herramienta (ej: 'suma_qa')")
                    })
                    put("descripcion", buildJsonObject {
                        put("type", "string")
                        put("description", "Descripción de qué hace la herramienta y cuándo usarla")
                    })
                    put("schema_json", buildJsonObject {
                        put("type", "string")
                        put("description", "JSON Schema válido de los parámetros de entrada (o '{}' si no tiene)")
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
                val nombre = args["nombre"]?.jsonPrimitive?.content?.trim() ?: ""
                val descripcion = args["descripcion"]?.jsonPrimitive?.content?.trim() ?: ""
                val schemaJson = args["schema_json"]?.jsonPrimitive?.content?.trim() ?: "{}"
                val codigoJs = args["codigo_js"]?.jsonPrimitive?.content?.trim() ?: ""

                if (nombre.isBlank() || codigoJs.isBlank()) {
                    CallToolResult(
                        isError = true,
                        content = listOf(TextContent(text = "Error: 'nombre' y 'codigo_js' son obligatorios.")),
                    )
                } else if (!VALID_CONNECTOR_NAME.matches(nombre)) {
                    CallToolResult(
                        isError = true,
                        content = listOf(
                            TextContent(
                                text = "Error: 'nombre' inválido ('$nombre'). Solo se permiten letras, números y guion bajo (sin rutas ni caracteres especiales).",
                            ),
                        ),
                    )
                } else {
                    val parsedSchema = try {
                        val el = jsonParser.parseToJsonElement(schemaJson)
                        el as? JsonObject
                    } catch (_: Exception) {
                        null
                    }
                    if (parsedSchema == null) {
                        CallToolResult(
                            isError = true,
                            content = listOf(TextContent(text = "Error: 'schema_json' no es un objeto JSON válido.")),
                        )
                    } else {
                        registerDynamicConnector(nombre, descripcion, schemaJson, codigoJs, persistToDb = true)
                        CallToolResult(
                            isError = false,
                            content = listOf(
                                TextContent(
                                    text = "Conector '$nombre' registrado y activado con éxito en el MCP de Fénix.",
                                ),
                            ),
                        )
                    }
                }
            },
        )

        // 2. Meta-herramienta: listar_conectores
        registerToolBoth(
            registrar = registrar,
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
                CallToolResult(isError = false, content = listOf(TextContent(text = resultText)))
            },
        )

        // 3. Meta-herramienta: eliminar_conector
        registerToolBoth(
            registrar = registrar,
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
                val nombre = args["nombre"]?.jsonPrimitive?.content?.trim() ?: ""
                val deleted = memoryDb.eliminarConector(nombre)
                FenixToolRegistry.unregister(nombre)
                CallToolResult(
                    isError = !deleted,
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
        )
    }

    /**
     * Registra un conector dinámico en memoria (y en SQLite solo cuando persistToDb = true).
     */
    fun registerDynamicConnector(
        nombre: String,
        descripcion: String,
        schemaJson: String,
        codigoJs: String,
        persistToDb: Boolean = true,
    ) {
        if (persistToDb) {
            memoryDb.guardarConector(nombre, descripcion, schemaJson, codigoJs)
        }

        val inputSchema = try {
            val element = jsonParser.parseToJsonElement(schemaJson)
            if (element is JsonObject) {
                val properties = element["properties"]?.let {
                    if (it is JsonObject) it else element
                } ?: element
                ToolSchema(properties = properties)
            } else {
                ToolSchema(properties = buildJsonObject {})
            }
        } catch (_: Exception) {
            ToolSchema(properties = buildJsonObject {})
        }

        registerToolBoth(
            registrar = activeRegistrar,
            toolName = nombre,
            name = nombre,
            description = descripcion,
            inputSchema = inputSchema,
            handler = { callReq ->
                val args = callReq.params.arguments ?: emptyMap()
                val argsJson = buildJsonObject {
                    args.forEach { (k, v) -> put(k, v) }
                }.toString()

                try {
                    val output = evaluator.evaluate(codigoJs, argsJson)
                    CallToolResult(
                        isError = false,
                        content = listOf(TextContent(text = output)),
                    )
                } catch (e: Exception) {
                    CallToolResult(
                        isError = true,
                        content = listOf(TextContent(text = "Error en conector '$nombre': ${e.message}")),
                    )
                }
            },
        )
    }

    private fun restoreStoredConnectors(registrar: LoggedToolRegistrar?) {
        val stored = memoryDb.listarConectores()
        stored.forEach { c ->
            registerDynamicConnector(
                nombre = c.nombre,
                descripcion = c.descripcion,
                schemaJson = c.schemaJson,
                codigoJs = c.codigoJs,
                persistToDb = false,
            )
        }
    }

    companion object {
        private val VALID_CONNECTOR_NAME = Regex("^[a-zA-Z][a-zA-Z0-9_]{1,63}$")
    }
}
