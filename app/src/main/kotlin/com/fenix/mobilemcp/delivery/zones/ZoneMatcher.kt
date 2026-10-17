package com.fenix.mobilemcp.delivery.zones

import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/** Resultado del cruce de una dirección contra el índice de zonas. */
data class ZoneHit(
    val zoneId: String,
    val department: String,
    val name: String,
    val nightExtreme: Boolean,
)

/** Entrada del índice (refleja assets/fenix/san_juan_zones.v1.json). */
data class ZoneEntry(
    val id: String,
    val department: String,
    val names: List<String>,
    val aliases: List<String>,
    val intersection: List<String> = emptyList(),
    val nightExtreme: Boolean = false,
)

/**
 * Matcher F1.1: keyword sobre texto normalizado (sin tildes, minúsculas).
 * Nivel intersección: exige ambas calles presentes. Presupuesto: <15 ms.
 * Los polígonos (R-Tree) llegan en F1.1b cuando exista el KML exportado.
 */
@Singleton
class ZoneMatcher
    @Inject
    constructor() {
        companion object {
            private val RAW_COORDS_REGEX = Regex("""^\s*-?\d{1,3}\.\d+\s*,\s*-?\d{1,3}\.\d+\s*$""")
            private val KNOWN_DEPARTMENTS = listOf("rawson", "capital", "chimbas", "santa lucia", "pocito", "rivadavia")
        }

        fun isRawCoordinates(raw: String): Boolean = RAW_COORDS_REGEX.matches(raw)

        fun normalize(raw: String): String {
            val lower = raw.lowercase()
            val noAccents =
                Normalizer.normalize(lower, Normalizer.Form.NFD)
                    .replace("\\p{Mn}+".toRegex(), "")
            return noAccents
                .replace("b°", "")
                .replace("v°", "")
                .replace("barrio", "")
                .replace("villa", "")
                .replace("\\s+".toRegex(), " ")
                .trim()
        }

        /** Devuelve todos los hits ordenados: intersección primero, luego keyword. */
        fun match(
            addressText: String,
            index: List<ZoneEntry>,
        ): List<ZoneHit> {
            val normFull = normalize(addressText)
            if (normFull.isBlank()) return emptyList()

            // Detectar si el usuario especificó explícitamente un departamento en la dirección
            val mentionedDepartments = KNOWN_DEPARTMENTS.filter { dept ->
                Regex("""\b${Regex.escape(dept)}\b""").containsMatchIn(normFull)
            }

            // Evitar que "Av. Libertador General San Martín" dispare el alias "san martin" de Barrio San Martín (Pocito)
            val normForKeywords = normFull
                .replace(Regex("""\b(av\.?|avenida)\s+libertador(\s+(gral\.?|general))?(\s+san\s+martin)?\b"""), " ")
                .replace(Regex("""\blibertador\s+(gral\.?|general)\s+san\s+martin\b"""), " ")
                .replace("\\s+".toRegex(), " ")
                .trim()

            val interHits = mutableListOf<ZoneHit>()
            val keyHits = mutableListOf<ZoneHit>()
            for (zone in index) {
                val zoneDeptNorm = normalize(zone.department)
                if (mentionedDepartments.isNotEmpty() && zoneDeptNorm !in mentionedDepartments) {
                    continue
                }

                if (zone.intersection.size >= 2 &&
                    zone.intersection.all { normalize(it) in normForKeywords }
                ) {
                    interHits.add(zone.toHit())
                    continue
                }
                val allTerms = zone.names + zone.aliases
                if (allTerms.any {
                        val termNorm = normalize(it)
                        termNorm.isNotBlank() && Regex("""\b${Regex.escape(termNorm)}\b""").containsMatchIn(normForKeywords)
                    }
                ) {
                    keyHits.add(zone.toHit())
                }
            }
            return interHits + keyHits
        }

        private fun ZoneEntry.toHit(): ZoneHit =
            ZoneHit(
                zoneId = id,
                department = department,
                name = names.firstOrNull().orEmpty(),
                nightExtreme = nightExtreme || riskNight(),
            )

        private fun ZoneEntry.riskNight(): Boolean = id.startsWith("POC-")
    }
