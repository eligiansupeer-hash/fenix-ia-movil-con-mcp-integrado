package com.fenix.mobilemcp.delivery.monitor

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.fenix.mobilemcp.delivery.mirror.MirrorAuth
import com.fenix.mobilemcp.delivery.zones.ZoneEntry
import com.fenix.mobilemcp.delivery.zones.ZoneHit
import com.fenix.mobilemcp.delivery.zones.ZoneMatcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.ArrayDeque
import java.util.Locale

/** Paquetes de delivery monitoreados (whitelist; bancarias excluidas). */
private val DELIVERY_PACKAGES =
    setOf(
        "com.ubercab",
        "com.pedidosya",
        "com.pedidosya.repartidor",
        "com.rappi.courier",
    )

/**
 * Monitor pasivo de delivery: solo LEE el árbol de accesibilidad
 * (jamás inyecta gestos en estas apps) y ante zona peligrosa
 * emite TTS + espejo al nodo B.
 */
class DeliveryMonitor(
    private val context: Context,
    private val zoneMatcher: ZoneMatcher,
    private val zoneIndex: List<ZoneEntry>,
    private val mirrorSecret: String = MirrorAuth.DEFAULT_SECRET,
    private val peerBaseUrl: String = "http://fenix-node-b.local:8080",
) {
    private val tag = "Fenix:DeliveryMonitor"
    private var isMonitoring = false
    private val tts =
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.i(tag, "TTS listo")
            }
        }.apply {
            setLanguage(Locale.forLanguageTag("es-AR"))
        }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun start() {
        if (isMonitoring) return
        isMonitoring = true
        Log.i(tag, "Monitor delivery ACTIVO")
    }

    fun stop() {
        isMonitoring = false
        tts.shutdown()
        scope.cancel()
        Log.i(tag, "Monitor delivery DETENIDO")
    }

    /** Llamado desde McpAccessibilityService ante TYPE_WINDOW_CONTENT_CHANGED. */
    fun onContentChanged(packageName: CharSequence?, rootNode: AccessibilityNodeInfo?) {
        val pkg = packageName?.toString().orEmpty()
        if (!isMonitoring || pkg.isBlank()) return
        if (pkg !in DELIVERY_PACKAGES) return

        scope.launch {
            val text = extractTextFromTree(rootNode)
            if (text.isBlank()) return@launch
            val hits = zoneMatcher.match(text, zoneIndex)
            if (hits.isNotEmpty()) handleDeliveryHits(pkg, hits)
        }
    }

    /** BFS del árbol extrayendo texto visible. */
    private fun extractTextFromTree(root: AccessibilityNodeInfo?): String {
        val builder = StringBuilder()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        root?.let { stack.add(it) }
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            node.text?.let { builder.append(it).append(' ') }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.add(it) }
            }
        }
        return builder.toString()
    }

    private fun handleDeliveryHits(packageName: String, hits: List<ZoneHit>) {
        val hit = hits.first()
        Log.i(tag, "Zona detectada: ${hit.zoneId} (${hit.department}) ${hit.name}")
        val msg =
            "Alerta: zona ${hit.name}, ${hit.department}." +
                if (hit.nightExtreme) " Riesgo extremo nocturno." else " Zona de riesgo."
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "delivery_alert")
        sendMirrorToNodeB(hit)
        Log.w(tag, "ALERTA: $packageName -> $hit")
    }

    /** Espejo al nodo B con secreto compartido (nunca en URL ni logs). */
    private fun sendMirrorToNodeB(hit: ZoneHit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client =
                    HttpClient(OkHttp) {
                        install(HttpTimeout) {
                            requestTimeoutMillis = 1500L
                            connectTimeoutMillis = 500L
                        }
                    }
                val response =
                    client.get("$peerBaseUrl/mirror") {
                        header(MirrorAuth.HEADER, mirrorSecret)
                        parameter("zone", hit.zoneId)
                        parameter("dept", hit.department)
                        parameter("name", hit.name)
                    }.bodyAsText()
                Log.d(tag, "Espejo B OK (${response.length} chars)")
            } catch (e: Exception) {
                Log.w(tag, "Espejo B falló", e)
            }
        }
    }

    @Suppress("UnusedPrivateMember")
    private fun encoded(value: String): String = URLEncoder.encode(value, "UTF-8")
}
