package com.alertamayor.app.service

import com.alertamayor.app.AlertaMayorApp
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio FCM (Firebase Cloud Messaging). Recibe los push de alta prioridad que
 * el servidor envía cuando llega una llamada/emergencia y el Supervisor no tiene
 * conexión WebSocket activa (app en segundo plano o suspendida por el sistema).
 *
 * v1.2: el push es data-only (sin `notification`) para que `onMessageReceived`
 * se ejecute también en segundo plano y pueda disparar el flujo de llamada real
 * (fijar RINGING + notificación de llamada entrante de alta prioridad).
 */
class FcmService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = application as? AlertaMayorApp ?: return
        // Persistir el token para que CallCoordinator lo registre en el servidor
        // cuando (re)conecte la señalización.
        scope.launch {
            app.container.settingsRepository.saveFcmToken(token)
            app.container.callCoordinator.registerFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        if (data["type"] != "call") return
        val fromDeviceId = data["fromDeviceId"] ?: return
        val fromName = data["fromName"] ?: fromDeviceId

        // Mostrar la notificación de llamada entrante DIRECTAMENTE desde el servicio.
        // Así funciona aunque la UI (NavGraph) no esté montada (app en segundo plano):
        // la notificación con sonido + fullScreenIntent alerta al supervisor y al
        // tocarla abre la pantalla de llamada (que muestra el overlay Aceptar/Rechazar).
        val app = application as? AlertaMayorApp
        NotificationHelper.createChannels(this)
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(
            NotificationHelper.NOTIFICATION_ID_INCOMING,
            NotificationHelper.buildIncomingCallNotification(this, fromName)
        )

        // Disparar el flujo de llamada entrante en el CallCoordinator (fija RINGING
        // y notifica a la UI cuando está en primer plano). No lanzar Activity
        // directamente (restricción de background activity launch en Android 10+).
        app?.container?.callCoordinator?.onFcmIncomingCall(fromDeviceId, fromName)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}