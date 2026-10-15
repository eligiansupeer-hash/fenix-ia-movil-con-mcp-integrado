package com.fenix.mobilemcp.services.accessibility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CompactUiPrunerTest {

    private lateinit var pruner: CompactUiPruner

    @BeforeEach
    fun setup() {
        pruner = CompactUiPruner()
    }

    @Test
    fun testPruneEmptyOrInvisibleTreeReturnsPlaceholder() {
        val invisibleRoot = AccessibilityNodeData(
            id = "node_0",
            bounds = BoundsData(0, 0, 1080, 2400),
            visible = false,
        )

        val result = pruner.prune(invisibleRoot)
        assertEquals("PANTALLA: Sin elementos interactivos detectados.", result)
    }

    @Test
    fun testPruneInteractiveTreeFormatsCorrectly() {
        val childButton = AccessibilityNodeData(
            id = "btn_accept",
            className = "android.widget.Button",
            text = "Aceptar Pedido",
            clickable = true,
            visible = true,
            bounds = BoundsData(100, 500, 980, 620),
        )

        val childInput = AccessibilityNodeData(
            id = "input_notes",
            className = "android.widget.EditText",
            text = "",
            hintText = "Escribe una nota...",
            editable = true,
            visible = true,
            bounds = BoundsData(100, 300, 980, 450),
        )

        val root = AccessibilityNodeData(
            id = "root_container",
            className = "android.widget.FrameLayout",
            visible = true,
            bounds = BoundsData(0, 0, 1080, 2400),
            children = listOf(childInput, childButton),
        )

        val result = pruner.prune(root)

        assertTrue(result.contains("PANTALLA_COMPACTA (2 elementos clave):"))
        assertTrue(result.contains("[input_notes] Input \"Escribe una nota...\" [EDT] (100,300,880x150)"))
        assertTrue(result.contains("[btn_accept] Button \"Aceptar Pedido\" [CLK] (100,500,880x120)"))
    }

    @Test
    fun testPruneTokenDensityUnderLimit() {
        val children = (1..20).map { i ->
            AccessibilityNodeData(
                id = "item_$i",
                className = if (i % 2 == 0) "android.widget.Button" else "android.widget.TextView",
                text = "Elemento interactivo de prueba $i",
                clickable = i % 2 == 0,
                visible = true,
                bounds = BoundsData(0, i * 100, 1080, (i + 1) * 100),
            )
        }

        val root = AccessibilityNodeData(
            id = "list_root",
            className = "androidx.recyclerview.widget.RecyclerView",
            scrollable = true,
            visible = true,
            bounds = BoundsData(0, 0, 1080, 2400),
            children = children,
        )

        val result = pruner.prune(root)
        // 250 tokens in Spanish / code is roughly 1000 - 1200 characters
        // Verify that 20 dense elements produce a concise output
        val tokenEstimate = result.split("\\s+".toRegex()).size
        assertTrue(tokenEstimate < 250, "Estimated tokens ($tokenEstimate) should be under 250")
    }
}

