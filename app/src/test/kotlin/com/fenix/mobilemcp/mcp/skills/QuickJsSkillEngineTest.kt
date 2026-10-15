package com.fenix.mobilemcp.mcp.skills

import android.content.Context
import com.fenix.mobilemcp.mcp.registry.FenixToolRegistry
import com.fenix.mobilemcp.memory.FenixMemoryDb
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class QuickJsSkillEngineTest {

    private lateinit var mockDb: FenixMemoryDb
    private lateinit var mockContext: Context
    private lateinit var engine: QuickJsSkillEngine

    @BeforeEach
    fun setup() {
        FenixToolRegistry.clear()
        mockDb = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        engine = QuickJsSkillEngine(mockContext, mockDb)
        engine.setEvaluatorForTesting(object : ScriptEvaluator {
            override suspend fun evaluate(code: String, argsJson: String): String {
                return "{\"calculo\": 105.5, \"args\": $argsJson}"
            }
        })
    }

    @Test
    fun testInitializeRegistersMetaTools() {
        every { mockDb.listarConectores() } returns emptyList()

        engine.initialize()

        assertNotNull(FenixToolRegistry.get("crear_conector"))
        assertNotNull(FenixToolRegistry.get("listar_conectores"))
        assertNotNull(FenixToolRegistry.get("eliminar_conector"))
    }

    @Test
    fun testCreateAndExecuteDynamicConnector() = runTest {
        every { mockDb.listarConectores() } returns emptyList()
        engine.initialize()

        // Ejecutar meta-herramienta para crear conector dinámico
        val createArgs = buildJsonObject {
            put("nombre", "calculadora_delivery")
            put("descripcion", "Calcula tarifa y propina")
            put("schema_json", "{}")
            put("codigo_js", "return args.base * 1.1;")
        }

        val createResult = FenixToolRegistry.executeInProcess(
            name = "crear_conector",
            arguments = createArgs,
        )

        assertTrue(createResult.isSuccess)
        assertTrue(createResult.outputText.contains("registrado y activado"))

        // Verificar que la herramienta recién creada existe en el registro
        val dynamicTool = FenixToolRegistry.get("calculadora_delivery")
        assertNotNull(dynamicTool)
        assertEquals("calculadora_delivery", dynamicTool?.toolName)

        // Ejecutar la herramienta creada dinámicamente en memoria
        val execArgs = buildJsonObject {
            put("base", 100)
        }

        val execResult = FenixToolRegistry.executeInProcess(
            name = "calculadora_delivery",
            arguments = execArgs,
        )

        assertTrue(execResult.isSuccess)
        assertTrue(execResult.outputText.contains("calculo"))
        assertTrue(execResult.outputText.contains("105.5"))
    }

    @Test
    fun testDeleteDynamicConnector() = runTest {
        every { mockDb.listarConectores() } returns emptyList()
        every { mockDb.eliminarConector("conector_temporal") } returns true
        engine.initialize()

        // Registrar conector directamente
        engine.registerDynamicConnector(
            nombre = "conector_temporal",
            descripcion = "Conector efímero",
            schemaJson = "{}",
            codigoJs = "return 1;",
        )

        assertNotNull(FenixToolRegistry.get("conector_temporal"))

        // Ejecutar eliminar_conector
        val deleteArgs = buildJsonObject {
            put("nombre", "conector_temporal")
        }

        val deleteResult = FenixToolRegistry.executeInProcess(
            name = "eliminar_conector",
            arguments = deleteArgs,
        )

        assertTrue(deleteResult.isSuccess)
        assertTrue(deleteResult.outputText.contains("eliminado"))
        assertNull(FenixToolRegistry.get("conector_temporal"))
    }
}
