package com.alertamayor.app

import android.app.Application
import com.alertamayor.app.di.AppContainer
import com.alertamayor.app.service.CallService
import com.alertamayor.app.service.NotificationHelper

/**
 * Clase Application de la app. Crea el contenedor de dependencias manual
 * (DI sin Hilt, según plan de menor riesgo) y los canales de notificación.
 */
class AlertaMayorApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
    }
}
