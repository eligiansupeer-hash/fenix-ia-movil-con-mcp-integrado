package com.fenix.mobilemcp.delivery.mirror

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import java.net.URLEncoder

/**
 * Endpoint /mirror para alertas del nodo A. Protegido por secreto compartido
 * (header X-Fenix-Mirror) porque vive en LAN/hotspot visible. Sin auth global.
 */
fun Route.mirrorRoutes(expectedSecret: String = MirrorAuth.DEFAULT_SECRET) {
    get("/mirror") {
        val secret = call.request.headers[MirrorAuth.HEADER]
        if (secret != expectedSecret) {
            call.respondText("forbidden", status = HttpStatusCode.Forbidden)
            return@get
        }
        val zoneId = call.request.queryParameters["zone"]
            ?: return@get call.respondText("Missing zone", status = HttpStatusCode.BadRequest)
        val dept = call.request.queryParameters["dept"] ?: ""
        val name = call.request.queryParameters["name"] ?: ""
        val query = URLEncoder.encode("$dept $name San Juan Argentina", "UTF-8")
        val mapsUrl = "https://www.google.com/maps/search/?api=1&query=$query"
        call.respond(
            MirrorResponse(
                status = "ok",
                zoneId = zoneId,
                department = dept,
                name = name,
                mapsUrl = mapsUrl,
                timestamp = System.currentTimeMillis(),
            ),
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
    val timestamp: Long,
)
