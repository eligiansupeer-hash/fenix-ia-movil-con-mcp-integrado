package com.fenix.mobilemcp.mcp.openapi

import com.fenix.mobilemcp.mcp.registry.FenixTool
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FenixOpenApiGeneratorTest {

    @Test
    fun testGenerateOpenApiDocument() {
        val testTool = FenixTool(
            toolName = "obtener_coordenadas",
            name = "fenix_obtener_coordenadas",
            description = "Devuelve latitud y longitud actuales",
            inputSchema = ToolSchema(),
            handler = { CallToolResult(content = emptyList()) },
        )

        val jsonString = FenixOpenApiGenerator.generate("1.0.0-test", listOf(testTool))
        assertNotNull(jsonString)

        val root = Json.parseToJsonElement(jsonString).jsonObject
        assertEquals("3.0.3", root["openapi"]?.jsonPrimitive?.content)

        val info = root["info"]?.jsonObject
        assertNotNull(info)
        assertEquals("1.0.0-test", info?.get("version")?.jsonPrimitive?.content)

        val paths = root["paths"]?.jsonObject
        assertNotNull(paths)
        assertTrue(paths!!.containsKey("/health"))
        assertTrue(paths.containsKey("/openapi.json"))
        assertTrue(paths.containsKey("/mcp"))
        assertTrue(paths.containsKey("/tools/fenix_obtener_coordenadas"))
    }
}

