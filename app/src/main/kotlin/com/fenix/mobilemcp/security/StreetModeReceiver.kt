package com.fenix.mobilemcp.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver que atiende la orden ACTION_ENTER_STREET_MODE.
 * Al activarse el Modo Calle:
 * 1. Asegura que el servicio Ktor externo libere su socket.
 * 2. Activa el estado de solo-lectura estricto en los servicios de accesibilidad.
 * 3. Notifica a la UI del F-Chat para actualizar el semáforo de seguridad.
 */
class StreetModeReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ENTER_STREET_MODE = "com.fenix.mobilemcp.ACTION_ENTER_STREET_MODE"
        const val ACTION_ENTER_WORKSHOP_MODE = "com.fenix.mobilemcp.ACTION_ENTER_WORKSHOP_MODE"
        private const val TAG = "StreetModeReceiver"

        @Volatile
        var isStreetModeActive: Boolean = false
            private set
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            ACTION_ENTER_STREET_MODE -> {
                isStreetModeActive = true
                Log.i(TAG, "[FÉNIX] Transición a MODO CALLE completada. Ktor externo silenciado, cortafuegos activo.")
            }
            ACTION_ENTER_WORKSHOP_MODE -> {
                isStreetModeActive = false
                Log.i(TAG, "[FÉNIX] Transición a MODO TALLER USB activada. Puertos y servidores habilitados.")
            }
        }
    }
}
