package com.alertamayor.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.alertamayor.app.ui.navigation.AlertaMayorNavGraph
import com.alertamayor.app.ui.theme.AlertaMayorTheme

class MainActivity : AppCompatActivity() {

    private val batteryOptimizationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // No se requiere acción tras cerrar el diálogo de optimización de batería.
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // No se requiere acción tras responder el diálogo de notificaciones.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AlertaMayorApp
        val coordinator = app.container.callCoordinator
        coordinator.connect()

        requestIgnoreBatteryOptimizations()
        requestNotificationPermission()

        // R-003 (ciclo 4): mantener la pantalla encendida mientras la app está en primer
        // plano. Es crítico en el teléfono supervisado (central de alerta): el botón rojo
        // de ALERTA debe estar siempre visible y pulsable sin tener que encender la pantalla.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            // M-001 (ciclo 6): selector de tema. Se observa la preferencia del DataStore
            // y se aplica en caliente (collectAsState recomponer sin reiniciar la app).
            val themePref by app.container.settingsRepository.theme.collectAsState(initial = "system")
            val darkTheme = when (themePref) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            AlertaMayorTheme(darkTheme = darkTheme) {
                AlertaMayorNavGraph()
            }
        }
        processNotificationAction(intent)
    }

    /**
     * Procesa las acciones de la notificación de llamada entrante
     * (Responder / Rechazar) cuando el usuario las toca (v1.2-fix ciclo 9).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processNotificationAction(intent)
    }

    private fun processNotificationAction(intent: Intent?) {
        val action = intent?.action ?: return
        val coordinator = (application as AlertaMayorApp).container.callCoordinator
        when (action) {
            "ACCEPT_CALL" -> coordinator.acceptIncomingCall()
            "REJECT_CALL" -> {
                coordinator.rejectIncomingCall()
                val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.cancel(com.alertamayor.app.service.NotificationHelper.NOTIFICATION_ID_INCOMING)
            }
        }
    }

    /**
     * Pide al usuario el permiso de notificaciones (Android 13+).
     * I-003 (v1.2): es crítico en el modo Supervisor (familiar), que recibe la
     * emergencia vía FCM; sin el permiso, la notificación de llamada no se muestra.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Pide al usuario desactivar la optimización de batería para esta app.
     * Es crítico en Huawei/Samsung/Xiaomi, que suspenden agresivamente las apps
     * en segundo plano y cortarían la recepción de llamadas de emergencia.
     */
    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        try {
            val intent = android.content.Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            batteryOptimizationLauncher.launch(intent)
        } catch (_: Exception) {
            // Si no se puede abrir el diálogo, se ignora.
        }
    }
}
