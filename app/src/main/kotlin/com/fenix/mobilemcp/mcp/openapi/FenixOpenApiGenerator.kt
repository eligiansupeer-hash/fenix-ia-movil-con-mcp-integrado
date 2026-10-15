package com.fenix.mobilemcp.mcp.openapi

import com.fenix.mobilemcp.mcp.registry.FenixTool
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Generador dinámico de especificación OpenAPI 3.0.3 a partir de las herramientas
 * MCP registradas en FenixToolRegistry.
 */
object FenixOpenApiGenerator {

    fun generate(version: String, tools: List<FenixTool>): String {
        val root = buildJsonObject {
            put("openapi", "3.0.3")
            putJsonObject("info") {
                put("title", "Fénix IA Móvil con MCP Integrado")
                put(
                    "description",
                    "API OpenAPI auto-generada para el servidor MCP Móvil Fénix. " +
                        "Permite la interacción transparente con el dispositivo, memoria distribuida y herramientas locales.",
                )
                put("version", version)
            }
            putJsonArray("servers") {
                add(buildJsonObject {
                    put("url", "/")
                    put("description", "Servidor Fénix (Local Loopback / Túnel Cloudflare)")
                })
            }
            putJsonObject("paths") {
                putJsonObject("/health") {
                    putJsonObject("get") {
                        put("summary", "Estado de salud del servidor")
                        putJsonObject("responses") {
                            putJsonObject("200") {
                                put("description", "Servidor operativo")
                            }
                        }
                    }
                }
                putJsonObject("/openapi.json") {
                    putJsonObject("get") {
                        put("summary", "Especificación OpenAPI en formato JSON")
                        putJsonObject("responses") {
                            putJsonObject("200") {
                                put("description", "Esquema OpenAPI completo")
                            }
                        }
                    }
                }
                putJsonObject("/mcp") {
                    putJsonObject("post") {
                        put("summary", "Endpoint JSON-RPC de Model Context Protocol (MCP)")
                        put("description", "Transporte Stateless Streamable HTTP para llamadas MCP.")
                        putJsonObject("responses") {
                            putJsonObject("200") {
                                put("description", "Respuesta RPC MCP")
                            }
                        }
                    }
                }
                tools.forEach { tool ->
                    putJsonObject("/tools/${tool.name}") {
                        putJsonObject("post") {
                            put("summary", tool.description.take(100))
                            put("description", tool.description)
                            putJsonObject("responses") {
                                putJsonObject("200") {
                                    put("description", "Resultado de ejecución de la herramienta")
                                }
                            }
                        }
                    }
                }
            }
        }
        return root.toString()
    }
}

