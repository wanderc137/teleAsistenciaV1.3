package com.alertamayor.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.alertamayor.app.AlertaMayorApp

/**
 * Servicio en primer plano que mantiene viva la app en modo SUPERVISOR (hijo)
 * para que permanezca atenta a llamadas de emergencia aunque la app no esté
 * visible en pantalla. Expone una notificación persistente "Atento a llamadas".
 *
 * Mantiene un WakeLock parcial para que el sistema no suspenda la CPU mientras
 * el teléfono está en reposo (modo Doze), y re-conecta la señalización si se
 * pierde la conexión.
 */
class GuardService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID_GUARD,
            NotificationHelper.buildGuardNotification(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Asegurar que la señalización esté conectada (reconecta si se perdió).
        val app = application as AlertaMayorApp
        app.container.callCoordinator.ensureConnected()
        return Service.START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AlertaMayor::GuardService"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    override fun onDestroy() {
        // Limpieza defensiva: retira la notificación foreground al detener el servicio.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}

