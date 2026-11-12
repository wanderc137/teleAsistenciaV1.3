package com.alertamayor.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.alertamayor.app.di.AppContainer

/**
 * Servicio en primer plano que mantiene viva la llamada WebRTC cuando la app
 * pasa a segundo plano. Expone la notificación persistente "Llamada en curso".
 */
class CallService : LifecycleService() {

    companion object {
        /** Acción para detener el servicio de forma limpia (quita la notificación foreground). */
        const val ACTION_STOP = "com.alertamayor.app.action.STOP_CALL"
    }

    override fun onCreate() {
        super.onCreate()
        // I-008: startForeground con el tipo de servicio explícito (cámara + micrófono)
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID_CALL,
            NotificationHelper.buildCallNotification(this),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            // Detención limpia solicitada: quita la notificación foreground y se detiene.
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return Service.START_NOT_STICKY
        }
        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        // Limpieza defensiva: garantiza que la notificación foreground desaparezca
        // aunque onStartCommand no procesara ACTION_STOP.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
