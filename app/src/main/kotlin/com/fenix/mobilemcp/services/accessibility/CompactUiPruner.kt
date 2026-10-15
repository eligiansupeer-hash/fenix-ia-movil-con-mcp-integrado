package com.fenix.mobilemcp.services.accessibility

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Podador ultra-compacto de árbol de accesibilidad (<250 tokens).
 * Diseñado para permitir que modelos locales pequeños (SLM como Qwen2.5-0.5B o Llama-3.2-1B
 * corriendo en el Moto G77) o LLMs remotos razonen sobre la pantalla sin saturar
 * su ventana de contexto con contenedores estériles ni decoraciones.
 */
@Singleton
class CompactUiPruner @Inject constructor() {

    fun prune(root: AccessibilityNodeData, maxElements: Int = 40): String {
        val interactiveElements = mutableListOf<String>()
        walkAndPrune(root, interactiveElements, maxElements)

        if (interactiveElements.isEmpty()) {
            return "PANTALLA: Sin elementos interactivos detectados."
        }

        val sb = StringBuilder()
        sb.appendLine("PANTALLA_COMPACTA (${interactiveElements.size} elementos clave):")
        interactiveElements.forEach { sb.appendLine(it) }
        return sb.toString().trimEnd()
    }

    private fun walkAndPrune(
        node: AccessibilityNodeData,
        collector: MutableList<String>,
        maxElements: Int,
    ) {
        if (collector.size >= maxElements || !node.visible) return

        val isInteractive = node.clickable || node.longClickable || node.editable || node.scrollable
        val hasText = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()

        // Determinar tipo legible
        val shortType = when {
            node.webRole != null -> node.webRole
            node.editable -> "Input"
            node.clickable && (node.className?.contains("Button") == true || node.className?.contains("Image") == true) -> "Button"
            node.clickable -> "Clickable"
            node.scrollable -> "Scroll"
            hasText -> "Text"
            else -> null
        }

        if (shortType != null && (isInteractive || hasText)) {
            val rawLabel = when {
                !node.text.isNullOrBlank() -> node.text
                !node.contentDescription.isNullOrBlank() -> node.contentDescription
                !node.hintText.isNullOrBlank() -> node.hintText
                else -> ""
            }
            val label = rawLabel?.trim().orEmpty()
            if (label.isNotBlank() || isInteractive) {
                val flags = buildString {
                    if (node.clickable) append("CLK ")
                    if (node.editable) append("EDT ")
                    if (node.scrollable) append("SCR ")
                    if (node.isPassword) append("PWD ")
                }.trim()

                val line = buildString {
                    append("[${node.id}] $shortType")
                    if (label.isNotBlank()) append(" \"$label\"")
                    if (flags.isNotBlank()) append(" [$flags]")
                    node.targetUrl?.let { append(" url:$it") }
                    val b = node.bounds
                    val width = b.right - b.left
                    val height = b.bottom - b.top
                    append(" (${b.left},${b.top},${width}x${height})")
                }
                collector.add(line)
            }
        }

        for (child in node.children) {
            walkAndPrune(child, collector, maxElements)
        }
    }
}
