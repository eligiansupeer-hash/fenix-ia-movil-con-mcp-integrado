package com.fenix.mobilemcp.delivery.zones

/** Provee el índice de zonas (objeto generado, sin I/O ni dependencias). */
object ZoneIndexProvider {
    fun provideIndex(): List<ZoneEntry> = ZoneIndex.ALL
}
