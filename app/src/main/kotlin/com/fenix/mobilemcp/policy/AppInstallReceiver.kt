package com.fenix.mobilemcp.policy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REPLACED) {
            val data = intent.data
            val packageName = data?.schemeSpecificPart
            
            if (packageName != null) {
                Log.i("AppInstallReceiver", "New package installed or replaced: $packageName. Rescanning policies...")
                CoroutineScope(Dispatchers.IO).launch {
                    val scanner = AppPolicyScanner(context)
                    scanner.scanAndInitialize()
                }
            }
        }
    }
}
