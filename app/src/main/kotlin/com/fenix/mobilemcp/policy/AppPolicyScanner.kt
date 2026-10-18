package com.fenix.mobilemcp.policy

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPolicyScanner @Inject constructor(private val context: Context) {
    private val policyFile = File(context.filesDir, "installed_apps_policy.json")

    suspend fun scanAndInitialize() = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            
            val existingPolicy = if (policyFile.exists()) {
                val json = policyFile.readText()
                Json.decodeFromString<Map<String, Int>>(json).toMutableMap()
            } else {
                mutableMapOf()
            }

            var updated = false
            for (pkg in packages) {
                val name = pkg.packageName
                if (!existingPolicy.containsKey(name)) {
                    // Nivel 0 por defecto (Cuarentena)
                    existingPolicy[name] = 0
                    updated = true
                }
            }

            if (updated) {
                val newJson = Json.encodeToString(existingPolicy)
                policyFile.writeText(newJson)
                Log.i("AppPolicyScanner", "Updated app policies with new packages.")
            }
        } catch (e: Exception) {
            Log.e("AppPolicyScanner", "Failed to scan apps", e)
        }
    }
}
