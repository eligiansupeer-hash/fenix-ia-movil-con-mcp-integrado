package com.fenix.mobilemcp.skills

/**
 * F6: registro de skills formato Edge (carpeta + SKILL.md con frontmatter
 * name/description). Tipos: text-only (v1), js-webview e intents nativos (F6b).
 */
data class FenixSkill(
    val name: String,
    val description: String,
    val kind: SkillKind,
    val requireSecret: Boolean = false,
)

enum class SkillKind { TEXT, JS_WEBVIEW, NATIVE_INTENT }

/** Catálogo inicial (las 11 de Gallery se migran como skills Fénix en F6b). */
object SkillCatalog {
    val BUILT_IN: List<FenixSkill> = listOf(
        FenixSkill(
            name = "turno-radar",
            description = "Explica cómo pedir vigilancia de turnos y qué significa cada alerta.",
            kind = SkillKind.TEXT,
        ),
        FenixSkill(
            name = "zona-sj",
            description = "Explica las 41 zonas peligrosas de San Juan y cómo leer una alerta.",
            kind = SkillKind.TEXT,
        ),
    )

    fun find(name: String): FenixSkill? = BUILT_IN.firstOrNull { it.name == name }
}
