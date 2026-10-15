package com.fenix.mobilemcp.delivery.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** BroadcastReceiver para el scheduler de turnos (CHECK_TURNS). */
class TurnCheckReceiver : BroadcastReceiver() {

    private val TAG = "Fenix:TurnCheckReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.fenix.mobilemcp.CHECK_TURNS") return
        
        Log.d("Fenix:TurnScheduler", "CHECK_TURNS recibido -> chequeo de turnos")
        
        // Aquí se haría la comprobación real de turnos en las apps
        // Por ahora solo log + reprogramación
        Log.d("Fenix:TurnScheduler", "Chequeo de turnos ejecutado")
        
        // Reprogramar siguiente check (el TurnScheduler se re-autorregistro via alarm)
    }
}