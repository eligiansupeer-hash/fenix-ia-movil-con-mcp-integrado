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
            val norm = normalize(addressText)
            if (norm.isBlank()) return emptyList()
            val interHits = mutableListOf<ZoneHit>()
            val keyHits = mutableListOf<ZoneHit>()
            for (zone in index) {
                if (zone.intersection.size >= 2 &&
                    zone.intersection.all { normalize(it) in norm }
                ) {
                    interHits.add(zone.toHit())
                    continue
                }
                val allTerms = zone.names + zone.aliases
                if (allTerms.any { normalize(it).isNotBlank() && normalize(it) in norm }) {
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
