package com.fenix.mobilemcp.delivery.zones

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tests F1.1 del matcher de zonas (keyword + intersección, sin coords). */
class ZoneMatcherTest {
    private val matcher = ZoneMatcher()
    private val index =
        listOf(
            ZoneEntry("RAW-04", "Rawson", listOf("Vidart y Doctor Ortega"), listOf("vidart y ortega"), listOf("vidart", "ortega")),
            ZoneEntry("CHI-05", "Chimbas", listOf("Villa El Piojo"), listOf("el piojo", "piojo")),
            ZoneEntry("POC-01", "Pocito", listOf("Villa Paolini"), listOf("paolini")),
        )

    @Test
    fun normalize_quitaTildesMayusculasYRuido() {
        assertEquals("vidart y ortega", matcher.normalize("  Vidart y Órtega "))
        assertEquals("la estacion", matcher.normalize("B° La Estación"))
    }

    @Test
    fun match_keywordConAlias() {
        val hits = matcher.match("Vidart y Ortega", index)
        assertTrue(hits.any { it.zoneId == "RAW-04" })
    }

    @Test
    fun match_interseccionExigeAmbasCalles() {
        val solo = matcher.match("Vidart", index.filter { it.id == "RAW-04" })
        assertTrue(solo.none { it.zoneId == "RAW-04" })
        val ambas = matcher.match("Vidart y Ortega", index.filter { it.id == "RAW-04" })
        assertTrue(ambas.any { it.zoneId == "RAW-04" })
    }

    @Test
    fun match_textoVacioDaVacio() {
        assertTrue(matcher.match("   ", index).isEmpty())
    }

    @Test
    fun match_pocMarcaNocheExtrema() {
        val hits = matcher.match("Villa Paolini", index)
        val hit = hits.first { it.zoneId == "POC-01" }
        assertTrue(hit.nightExtreme)
    }

    @Test
    fun match_aliasCorto() {
        val hits = matcher.match("voy por el piojo", index)
        assertTrue(hits.any { it.zoneId == "CHI-05" })
    }
}
