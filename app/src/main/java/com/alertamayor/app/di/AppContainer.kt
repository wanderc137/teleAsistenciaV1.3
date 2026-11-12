package com.alertamayor.app.di

import android.content.Context
import android.content.Intent
import android.os.Build
import com.alertamayor.app.data.local.SettingsRepository
import com.alertamayor.app.data.network.ConnectivityObserver
import com.alertamayor.app.R
import com.alertamayor.app.service.CallService
import com.alertamayor.app.service.GuardService
import com.alertamayor.app.webrtc.AudioSpeakerController
import com.alertamayor.app.webrtc.CallCoordinator
import com.alertamayor.app.webrtc.WebRtcManager

/**
 * Contenedor de dependencias manual (plan de menor riesgo frente a Hilt/KSP).
 * Creado en [com.alertamayor.app.AlertaMayorApp] y expuesto a los ViewModel vía Factory.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    /** Observador de conectividad a internet (WIFI/CELLULAR/NONE). — M-002 (ciclo 6) */
    val connectivityObserver: ConnectivityObserver by lazy { ConnectivityObserver(appContext) }

    val webRtcManager: WebRtcManager by lazy { WebRtcManager(appContext) }

    val audioSpeakerController: AudioSpeakerController by lazy { AudioSpeakerController(appContext) }

    val callCoordinator: CallCoordinator by lazy {
        CallCoordinator(
            context = appContext,
            settings = settingsRepository,
            webRtc = webRtcManager,
            audio = audioSpeakerController,
            onForegroundStart = { startCallService() },
            onForegroundStop = { stopCallService() },
            onGuardStart = { startGuardService() },
            onGuardStop = { stopGuardService() },
        )
    }

    private fun startCallService() {
        val intent = Intent(appContext, CallService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    private fun stopCallService() {
        // I-002 (ciclo 3): stopService() es la vía principal. El onDestroy() de
        // CallService ya ejecuta stopForeground(STOP_FOREGROUND_REMOVE), por lo que
        // stopService basta para retirar la notificación "Tu llamada sigue activa".
        val intent = Intent(appContext, CallService::class.java)

        // Entrega de ACTION_STOP (detención limpia) solo si el servicio está corriendo,
        // usando startService (nunca startForegroundService: evitaría
        // ForegroundServiceStartNotAllowedException y parpadeo de notificación).
        if (isServiceRunning(CallService::class.java)) {
            val stopIntent = Intent(appContext, CallService::class.java).apply {
                action = CallService.ACTION_STOP
            }
            runCatching { appContext.startService(stopIntent) }
        }

        // Respaldo: stopService directo (no-op si el servicio ya se detuvo por ACTION_STOP).
        runCatching { appContext.stopService(intent) }
    }

    /** Comprueba si un servicio concreto está actualmente en ejecución. */
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        return am.getRunningServices(Int.MAX_VALUE).any { it.service.className == serviceClass.name }
    }

    /** Inicia el servicio de guardia (modo Supervisor residente en segundo plano). */
    fun startGuardService() {
        val intent = Intent(appContext, GuardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    /** Detiene el servicio de guardia. */
    fun stopGuardService() {
        val intent = Intent(appContext, GuardService::class.java)
        appContext.stopService(intent)
    }

    /** I-002 (V1.3): nombre por defecto localizado del par vinculado (Español/English). */
    fun defaultPeerName(): String = appContext.getString(R.string.default_peer_name)
}
