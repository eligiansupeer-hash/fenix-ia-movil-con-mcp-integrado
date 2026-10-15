package com.fenix.mobilemcp.delivery.mirror

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.application.call.*
import io.ktor.http.ContentType
import kotlinx.serialization.Serializable

/**
 * Endpoint /mirror para recibir alertas de zonas del nodo A (hotspot)
 * y abrir Google Maps con la capa de zonas peligrosas.
 * 
 * Uso: GET /mirror?zone=RAW-01&dept=Rawson&name=Valle%20Grande
 * Respuesta: JSON con status + deep-link de Maps para abrir en la app.
 */
fun Route.mirrorRoutes(expectedSecret: String = MirrorAuth.DEFAULT_SECRET) {
    get("/mirror") {
        // Auditoría F9: el espejo vive en LAN/hotspot visible → secreto compartido
        // por header (configurado al emparejar nodos), nunca en URL ni logs.
        val secret = call.request.headers["X-Fenix-Mirror"]
        if (secret != expectedSecret) {
            call.respondText("forbidden", status = io.ktor.http.HttpStatusCode.Forbidden)
            return@get
        }
        val zoneId = call.request.queryParameters["zone"] ?: return@get call.respondText("Missing zone", status = 400)
        val dept = call.request.queryParameters["dept"] ?: ""
        val name = call.request.queryParameters["name"] ?: ""
        
        val mapsUrl = "https://www.google.com/maps/search/?api=1&query=${kotlin.net.urlencode(dept + " " + name + " San Juan Argentina")}&layers=traffic"
        
        val response = MirrorResponse(
            status = "ok",
            zoneId = zoneId,
            department = dept,
            name = name,
            mapsUrl = mapsUrl,
            timestamp = System.currentTimeMillis()
        )
        
        call.respond(mapOf(
            "status" to "ok",
            "zoneId" to zoneId,
            "department" to dept,
            "name" to name,
            "mapsUrl" to mapsUrl,
            "timestamp" to System.currentTimeMillis()
        )
    }
}

@Serializable
data class MirrorResponse(
    val status: String,
    val zoneId: String,
    val department: String,
    val name: String,
    val mapsUrl: String,
    val timestamp: Long
)