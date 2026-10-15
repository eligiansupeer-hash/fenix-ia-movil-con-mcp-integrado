package com.fenix.mobilemcp.delivery.zones

import android.content.Context
import com.google.gson.Gson
import com.fenix.mobilemcp.delivery.zones.ZoneEntry

/** Proveedor del índice de zonas desde assets/fenix/san_juan_zones.v1.json */
object ZoneIndexProvider {

    fun provideIndex(): List<ZoneEntry> {
        // En producción se lee del asset; aquí devolvemos lista vacía como fallback
        // La implementación real se inyecta vía Hilt/Dagger con Context
        return emptyList()
    }

    /** Versión para inyección con Context (para Hilt). */
    @Suppress("UNUSED_PARAMETER")
    fun provideIndex(context: android.content.Context): List<ZoneEntry> {
        return try {
            val json = context.assets.open("fenix/san_juan_zones.v1.json").bufferedReader().use { it.readText() }
            val gson = com.google.gson.Gson()
            val wrapper = gson.fromJson(json, ZonesWrapper::class.java)
            wrapper.zones
        } catch (e: Exception) {
            emptyList()
        }
    }

    @com.google.gson.annotations.SerializedName("zones")
    data class ZonesWrapper(
        val zones: List<ZoneEntry>
    )
}