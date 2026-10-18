package com.fenix.mobilemcp.services.tunnel

import android.content.Context
import android.os.Environment
import android.util.Log
import com.fenix.mobilemcp.data.model.TunnelIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TunnelIdentityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fenixDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "fenix"
    )
    private val identityFile = File(fenixDir, "tunnel_identity.json")

    suspend fun getIdentity(): TunnelIdentity? = withContext(Dispatchers.IO) {
        try {
            if (!identityFile.exists()) return@withContext null
            
            val jsonStr = identityFile.readText()
            Json.decodeFromString<TunnelIdentity>(jsonStr)
        } catch (e: Exception) {
            Log.e("TunnelIdentity", "Failed to read tunnel identity", e)
            null
        }
    }

    suspend fun saveIdentity(identity: TunnelIdentity) = withContext(Dispatchers.IO) {
        try {
            if (!fenixDir.exists()) {
                fenixDir.mkdirs()
            }
            
            val jsonStr = Json.encodeToString(identity)
            identityFile.writeText(jsonStr)
            Log.i("TunnelIdentity", "Tunnel identity saved to ${identityFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("TunnelIdentity", "Failed to save tunnel identity", e)
        }
    }
}
