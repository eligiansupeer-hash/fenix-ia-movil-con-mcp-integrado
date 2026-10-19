package com.fenix.mobilemcp.termux

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AccountSlotStatus(
    val id: String,
    val label: String,
    val hasToken: Boolean,
    val email: String? = null,
    val isActive: Boolean = false,
)

data class TermuxBridgeStatus(
    val isAppInstalled: Boolean = false,
    val isBridgeActive: Boolean = false,
    val opencodeInstalled: Boolean = false,
    val opencodeConnected: Boolean = false,
    val antigravityInstalled: Boolean = false,
    val antigravityConnected: Boolean = false,
    val activeSlot: String = "slot_1",
    val autoFailover: Boolean = true,
    val slots: List<AccountSlotStatus> = listOf(
        AccountSlotStatus("slot_1", "Cuenta 1", false, null, true),
        AccountSlotStatus("slot_2", "Cuenta 2", false, null, false),
        AccountSlotStatus("slot_3", "Cuenta 3", false, null, false),
    ),
)

@Singleton
class TermuxBridgeManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(600, TimeUnit.MILLISECONDS)
                    readTimeout(2000, TimeUnit.MILLISECONDS)
                }
            }
        }
    }

    private val directDownloadUrl =
        "https://github.com/termux/termux-app/releases/download/v0.118.1/termux-app_v0.118.1+github-debug_arm64-v8a.apk"

    fun isTermuxInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.termux", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getDirectDownloadUri(): Uri = Uri.parse(directDownloadUrl)

    suspend fun activateTermux(): Boolean = withContext(Dispatchers.IO) {
        if (!isTermuxInstalled()) return@withContext false

        try {
            // Intentar disparar el comando de activación por RUN_COMMAND de Termux
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/python")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-m", "http.server", "8081"))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            }
            context.startService(intent)
            true
        } catch (e: Exception) {
            // Fallback: Si no tiene servicio de fondo aún, lanzar Termux por Intent principal
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.termux")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    true
                } else {
                    false
                }
            } catch (e2: Exception) {
                false
            }
        }
    }

    suspend fun queryStatus(): TermuxBridgeStatus = withContext(Dispatchers.IO) {
        val installed = isTermuxInstalled()
        if (!installed) {
            return@withContext TermuxBridgeStatus(isAppInstalled = false)
        }

        try {
            val response = httpClient.get("http://127.0.0.1:8081/v1/status")
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                val obj = json.parseToJsonElement(body).jsonObject

                val opencodeInst = obj["opencode_installed"]?.jsonPrimitive?.booleanOrNull ?: false
                val opencodeConn = obj["opencode_connected"]?.jsonPrimitive?.booleanOrNull ?: false
                val agyInst = obj["antigravity_installed"]?.jsonPrimitive?.booleanOrNull ?: false
                val agyConn = obj["antigravity_connected"]?.jsonPrimitive?.booleanOrNull ?: false
                val activeSlot = obj["active_slot"]?.jsonPrimitive?.content ?: "slot_1"
                val autoFailover = obj["auto_failover"]?.jsonPrimitive?.booleanOrNull ?: true

                val slotsObj = obj["slots"]?.jsonObject
                val slotsList = mutableListOf<AccountSlotStatus>()
                for (i in 1..3) {
                    val key = "slot_$i"
                    val item = slotsObj?.get(key)?.jsonObject
                    val has = item?.get("has_token")?.jsonPrimitive?.booleanOrNull ?: false
                    val email = item?.get("email")?.jsonPrimitive?.content
                    val label = item?.get("label")?.jsonPrimitive?.content ?: "Cuenta $i"
                    slotsList.add(
                        AccountSlotStatus(
                            id = key,
                            label = label,
                            hasToken = has,
                            email = email,
                            isActive = (key == activeSlot),
                        )
                    )
                }

                return@withContext TermuxBridgeStatus(
                    isAppInstalled = true,
                    isBridgeActive = true,
                    opencodeInstalled = opencodeInst,
                    opencodeConnected = opencodeConn,
                    antigravityInstalled = agyInst,
                    antigravityConnected = agyConn,
                    activeSlot = activeSlot,
                    autoFailover = autoFailover,
                    slots = slotsList,
                )
            }
        } catch (e: Exception) {
            // Puente aún no responde o cerrado
        }

        return@withContext TermuxBridgeStatus(
            isAppInstalled = true,
            isBridgeActive = false,
        )
    }

    suspend fun installOpenCode(): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = httpClient.post("http://127.0.0.1:8081/v1/opencode/install")
            resp.status == HttpStatusCode.Accepted || resp.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    suspend fun installAntigravity(): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = httpClient.post("http://127.0.0.1:8081/v1/antigravity/install")
            resp.status == HttpStatusCode.Accepted || resp.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    suspend fun startAntigravityLogin(slot: String): String? = withContext(Dispatchers.IO) {
        try {
            val resp = httpClient.post("http://127.0.0.1:8081/v1/antigravity/login") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("slot", slot) }.toString())
            }
            if (resp.status == HttpStatusCode.OK) {
                val obj = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                return@withContext obj["login_url"]?.jsonPrimitive?.content
            }
        } catch (e: Exception) {
            // Error de conexión
        }
        return@withContext null
    }

    suspend fun logoutAntigravity(slot: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = httpClient.post("http://127.0.0.1:8081/v1/antigravity/logout") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("slot", slot) }.toString())
            }
            resp.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    suspend fun selectAntigravitySlot(slot: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = httpClient.post("http://127.0.0.1:8081/v1/antigravity/select_slot") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("slot", slot) }.toString())
            }
            resp.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    suspend fun toggleFailover(auto: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = httpClient.post("http://127.0.0.1:8081/v1/antigravity/toggle_failover") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("auto_failover", auto) }.toString())
            }
            resp.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }
}
