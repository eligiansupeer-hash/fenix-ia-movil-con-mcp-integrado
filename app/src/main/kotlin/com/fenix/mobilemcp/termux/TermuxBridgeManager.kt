package com.fenix.mobilemcp.termux

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
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
import kotlinx.coroutines.suspendCancellableCoroutine
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
import kotlin.coroutines.resume

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

    private val companionPackage = "com.fenix.runtime"
    private val companionControlAction = "com.fenix.runtime.CONTROL"
    private val companionPort = 8766
    private val msgGetToken = 2
    private val msgStart = 3

    @Volatile
    private var companionToken: String? = null

    fun isCompanionInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(companionPackage, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

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
        if (isCompanionInstalled()) {
            val token = obtainCompanionToken()
            if (token != null && sendCompanionCommand(msgStart)) {
                return@withContext true
            }
        }
        if (!isTermuxInstalled()) return@withContext false

        try {
            // Intentar disparar el comando de activación por RUN_COMMAND de Termux
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra(
                    "com.termux.RUN_COMMAND_ARGUMENTS",
                    arrayOf("-c", "python /sdcard/Documents/fenix/termux/fenix_termux_bridge.py")
                )
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
        if (isCompanionInstalled()) {
            return@withContext queryCompanionStatus()
        }

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
        if (isCompanionInstalled()) {
            return@withContext companionPost("/v1/opencode/install")
        }
        try {
            val resp = httpClient.post("http://127.0.0.1:8081/v1/opencode/install")
            resp.status == HttpStatusCode.Accepted || resp.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    suspend fun installAntigravity(): Boolean = withContext(Dispatchers.IO) {
        if (isCompanionInstalled()) {
            return@withContext companionPost("/v1/antigravity/install")
        }
        try {
            val resp = httpClient.post("http://127.0.0.1:8081/v1/antigravity/install")
            resp.status == HttpStatusCode.Accepted || resp.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun queryCompanionStatus(retryAuth: Boolean = true): TermuxBridgeStatus {
        if (!isCompanionInstalled()) {
            return TermuxBridgeStatus(isAppInstalled = false)
        }
        val token = obtainCompanionToken()
            ?: return TermuxBridgeStatus(isAppInstalled = true, isBridgeActive = false)
        return try {
            val response = httpClient.get("http://127.0.0.1:$companionPort/v1/status") {
                header("Authorization", "Bearer $token")
            }
            if (response.status == HttpStatusCode.Unauthorized && retryAuth) {
                companionToken = null
                return queryCompanionStatus(retryAuth = false)
            }
            if (response.status == HttpStatusCode.OK) {
                val obj = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val opencodeInst = obj["opencode_installed"]?.jsonPrimitive?.booleanOrNull ?: false
                val agyInst = obj["antigravity_installed"]?.jsonPrimitive?.booleanOrNull ?: false
                TermuxBridgeStatus(
                    isAppInstalled = true,
                    isBridgeActive = true,
                    opencodeInstalled = opencodeInst,
                    opencodeConnected = opencodeInst,
                    antigravityInstalled = agyInst,
                    antigravityConnected = agyInst,
                )
            } else {
                TermuxBridgeStatus(isAppInstalled = true, isBridgeActive = false)
            }
        } catch (e: Exception) {
            TermuxBridgeStatus(isAppInstalled = true, isBridgeActive = false)
        }
    }

    private suspend fun companionPost(path: String, retryAuth: Boolean = true): Boolean {
        val token = obtainCompanionToken() ?: return false
        return try {
            val response = httpClient.post("http://127.0.0.1:$companionPort$path") {
                header("Authorization", "Bearer $token")
            }
            if (response.status == HttpStatusCode.Unauthorized && retryAuth) {
                companionToken = null
                return companionPost(path, retryAuth = false)
            }
            response.status == HttpStatusCode.Accepted || response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun obtainCompanionToken(): String? {
        companionToken?.let { return it }
        if (!isCompanionInstalled()) {
            return null
        }
        val token = withContext(Dispatchers.IO) {
            suspendCancellableCoroutine<String?> { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (binder == null) {
                            if (continuation.isActive) continuation.resume(null)
                            return
                        }
                        val messenger = Messenger(binder)
                        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
                            val value = message.data.getString("token")
                            if (continuation.isActive) continuation.resume(value)
                            try {
                                context.unbindService(this)
                            } catch (_: Exception) {
                            }
                            true
                        })
                        try {
                            messenger.send(Message.obtain(null, msgGetToken).apply { replyTo = reply })
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                val intent = Intent(companionControlAction).setPackage(companionPackage)
                try {
                    context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }
                continuation.invokeOnCancellation {
                    try {
                        context.unbindService(connection)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        if (!token.isNullOrBlank()) {
            companionToken = token
        }
        return token
    }

    private suspend fun sendCompanionCommand(what: Int): Boolean {
        if (!isCompanionInstalled()) {
            return false
        }
        return withContext(Dispatchers.IO) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (binder == null) {
                            if (continuation.isActive) continuation.resume(false)
                            return
                        }
                        val messenger = Messenger(binder)
                        val reply = Messenger(Handler(Looper.getMainLooper()) { _ ->
                            if (continuation.isActive) continuation.resume(true)
                            try {
                                context.unbindService(this)
                            } catch (_: Exception) {
                            }
                            true
                        })
                        try {
                            messenger.send(Message.obtain(null, what).apply { replyTo = reply })
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resume(false)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
                val intent = Intent(companionControlAction).setPackage(companionPackage)
                try {
                    context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resume(false)
                }
                continuation.invokeOnCancellation {
                    try {
                        context.unbindService(connection)
                    } catch (_: Exception) {
                    }
                }
            }
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
