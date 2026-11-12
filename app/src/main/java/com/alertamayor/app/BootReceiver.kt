package com.alertamayor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver de arranque del sistema (ciclo 12 / M-003).
 *
 * Cuando el teléfono se enciende (BOOT_COMPLETED), abre la app automáticamente.
 * Esto es crítico en el teléfono supervisado (central de alerta del anciano):
 * al encender, la app debe quedar en la pantalla de la campanilla ALERTA sin
 * que el anciano tenga que tocar nada.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val launch = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(launch)
        }
    }
}