package com.fenix.mobilemcp.mcp.registry

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FenixToolRegistryTest {

    @BeforeEach
    fun setup() {
        FenixToolRegistry.clear()
    }

    @Test
    fun register_and_get_tool() {
        val tool = FenixTool(
            toolName = "test_tool",
            name = "fenix_test_tool",
            description = "Herramienta de prueba",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("key", buildJsonObject { put("type", "string") })
                },
                required = listOf("key"),
            ),
            handler = { req ->
                CallToolResult(content = listOf(TextContent("recibido: ${req.params.arguments}")))
            },
        )

        FenixToolRegistry.register(tool)

        assertEquals(1, FenixToolRegistry.count())
        val retrieved = FenixToolRegistry.get("fenix_test_tool")
        assertNotNull(retrieved)
        assertEquals("test_tool", retrieved?.toolName)
        assertEquals("Herramienta de prueba", retrieved?.description)
    }

    @Test
    fun executeInProcess_success() = runTest {
        val tool = FenixTool(
            toolName = "echo",
            name = "fenix_echo",
            description = "Eco local",
            inputSchema = ToolSchema(),
            handler = { req ->
                CallToolResult(content = listOf(TextContent("echo_ok")))
            },
        )

        FenixToolRegistry.register(tool)

        val result = FenixToolRegistry.executeInProcess("fenix_echo")
        assertTrue(result.isSuccess)
        assertEquals("echo_ok", result.outputText)
        assertTrue(result.durationMs >= 0.0)
    }

    @Test
    fun executeInProcess_not_found() = runTest {
        val result = FenixToolRegistry.executeInProcess("non_existent_tool")
        assertFalse(result.isSuccess)
        assertTrue(result.outputText.contains("no encontrada"))
    }

    @Test
    fun toOpenAiToolDefinitions_formats_json() {
        val tool = FenixTool(
            toolName = "guardar",
            name = "fenix_guardar",
            description = "Guarda en memoria",
            inputSchema = ToolSchema(
                properties = buildJsonObject {
                    put("valor", buildJsonObject { put("type", "string") })
                },
                required = listOf("valor"),
            ),
            handler = { CallToolResult(content = emptyList()) },
        )

        FenixToolRegistry.register(tool)

        val definitions = FenixToolRegistry.toOpenAiToolDefinitions()
        assertEquals(1, definitions.size)
        val def = definitions[0]

        assertEquals("function", def["type"]?.jsonPrimitive?.content)
        val fn = def["function"]?.jsonObject
        assertNotNull(fn)
        assertEquals("fenix_guardar", fn?.get("name")?.jsonPrimitive?.content)
        assertEquals("Guarda en memoria", fn?.get("description")?.jsonPrimitive?.content)
        val params = fn?.get("parameters")?.jsonObject
        assertNotNull(params)
        assertEquals("object", params?.get("type")?.jsonPrimitive?.content)
        val required = params?.get("required")?.jsonArray
        assertNotNull(required)
        assertEquals("valor", required?.get(0)?.jsonPrimitive?.content)
    }
}

