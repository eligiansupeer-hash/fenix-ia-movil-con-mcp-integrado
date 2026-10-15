package com.fenix.mobilemcp.delivery.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.util.Log
import com.fenix.mobilemcp.delivery.zones.ZoneEntry
import com.fenix.mobilemcp.delivery.zones.ZoneHit
import com.fenix.mobilemcp.delivery.zones.ZoneMatcher
import com.fenix.mobilemcp.services.accessibility.McpAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Monitor pasivo de aplicaciones de delivery (Uber, PedidosYa, Rappi).
 * Se engancha al AccessibilityService existente y detecta ofertas/pedidos en pantalla.
 * Si detecta zona peligrosa → TTS alerta + envía deep-link al nodo B (mirror 1150ms).
 */
class DeliveryMonitor
    @Suppress("UNUSED_PARAMETER")
    constructor(
        private val context: Context,
        private val zoneMatcher: ZoneMatcher,
        private val zoneIndex: List<com.fenix.mobilemcp.delivery.zones.ZoneEntry>,
    ) {

    private val TAG = "Fenix:DeliveryMonitor"
    private var isMonitoring = false
    private val tts = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            it.setLanguage(Locale.forLanguageTag("es-AR"))
        }
    }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    /** Inicia monitoreo pasivo en apps de delivery conocidas. */
    fun start() {
        if (isMonitoring) return
        isMonitoring = true
        Log.i("Fenix:DeliveryMonitor", "Monitor delivery ACTIVO")
    }

    /** Detiene el monitoreo. */
    fun stop() {
        isMonitoring = false
        tts.shutdown()
        scope.cancel()
        Log.i("Fenix:DeliveryMonitor", "Monitor delivery DETENIDO")
    }

    /**
     * Punto de entrada llamado desde McpAccessibilityService.onAccessibilityEvent
     * cuando TYPE_WINDOW_CONTENT_CHANGED en apps de delivery permitidas.
     */
    fun onContentChanged(packageName: String, rootNode: android.view.accessibility.AccessibilityNodeInfo?) {
        if (!isMonitoring || packageName.isNullOrBlank() || packageName == "com.android.settings") return
        if (!isDeliveryApp(packageName)) return

        scope.launch {
            val hits = scanForDeliveryOffer(packageName, rootNode)
            if (hits.isNotEmpty()) {
                handleDeliveryHits(packageName, hits)
            }
        }
    }

    private fun isDeliveryApp(packageName: String): Boolean {
        return when (packageName) {
            "com.ubercab" -> true            // Uber Driver / Uber Eats
            "com.pedidosya" -> true
            "com.pedidosya.repartidor" -> true
            "com.grabtaxi.driver" -> false   // no opera en SJ
            "com.rapiddo.courier" -> false
            "com.rappi.courier" -> true      // Soy Rappi repartidor
            "com.grab.food.passenger" -> false
            else -> false
        }
    }

    /** Extrae texto visible y busca direcciones/texto de oferta. */
    private suspend fun scanForDeliveryOffer(
        packageName: String,
        rootNode: android.view.accessibility.AccessibilityNodeInfo?
    ): List<ZoneHit> {
        val text = extractTextFromTree(rootNode)
        return if (text.isNotBlank()) {
            zoneMatcher.match(text)
        } else emptyList()
    }

    /** Recorrido BFS del árbol de accesibilidad extrayendo todo el texto visible. */
    private fun extractTextFromTree(root: android.view.accessibility.AccessibilityNodeInfo?): String {
        val builder = StringBuilder()
        val stack = java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
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

    private suspend fun handleDeliveryHits(packageName: String, hits: List<com.fenix.mobilemcp.delivery.zones.ZoneHit>) {
        val hit = hits.first() // el más crítico (intersección primero)
        Log.i("Fenix:DeliveryMonitor", "Zona detectada: ${hit.zoneId} (${hit.department}) ${hit.name}")

        // 1) TTS alerta inmediata (sin bloquear UI)
        val msg = "Alerta: zona ${hit.name}, ${hit.department}. ${if (hit.nightExtreme) "Riesgo extremo nocturno" else "Zona de riesgo"}"
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "delivery_alert")

        // 2) Envía deep-link al nodo B (espejo mapa 1150ms)
        val address = "dirección detectada en ${hit.department}"
        sendMirrorToNodeB(hit)

        // 3) Log local
        Log.w("Fenix:DeliveryMonitor", "ALERTA: $packageName -> $hit")
    }

    /** Envía deep-link al nodo B vía hotspot local (HTTP GET al servidor MCP del nodo B). */
    private fun sendMirrorToNodeB(hit: com.fenix.mobilemcp.delivery.zones.ZoneHit) {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        scope.launch {
            val url = "http://fenix-node-b.local:8080/mirror?zone=${hit.zoneId}&dept=${hit.department}&name=${hit.name.encode()}"
            try {
                val client = io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
                    install(io.ktor.client.plugins.HttpTimeout) {
                        requestTimeoutMillis = 1500
                        connectTimeoutMillis = 500
                    }
                }
                val response = client.get<String>(url).get()
                Log.d("Fenix:DeliveryMirror", "Espejo B enviado: $response")
            } catch (e: Exception) {
                Log.w("Fenix:DeliveryMirror", "No se pudo enviar espejo a nodo B", e)
            }
        }
    }

    /** Filtro de paquetes de delivery activos (whitelist). */
    private fun isDeliveryApp(pkg: String): Boolean = when (pkg) {
        "com.ubercab" -> true
        "com.pedidosya" -> true
        "com.pedidosya.repartidor" -> true
        "com.rappi.courier" -> true
        else -> false
    }
}