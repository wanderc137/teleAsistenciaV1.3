package com.alertamayor.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.alertamayor.app.MainActivity
import com.alertamayor.app.R

/** Gestiona los canales de notificación y las notificaciones de la app. */
object NotificationHelper {

    const val CHANNEL_CALLS = "calls"
    const val NOTIFICATION_ID_CALL = 1001
    const val NOTIFICATION_ID_INCOMING = 1002
    const val NOTIFICATION_ID_GUARD = 1003

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val callsChannel = NotificationChannel(
            CHANNEL_CALLS,
            context.getString(R.string.notification_channel_calls),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_calls_desc)
        }
        manager.createNotificationChannel(callsChannel)
    }

    /** Notificación persistente de "llamada en curso" para el Foreground Service. */
    fun buildCallNotification(context: Context): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_call_title))
            .setContentText(context.getString(R.string.notification_call_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    /** Notificación persistente "atento" del modo Supervisor (GuardService). */
    fun buildGuardNotification(context: Context): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_guard_title))
            .setContentText(context.getString(R.string.notification_guard_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Notificación de llamada entrante (modo Supervisor). */
    fun buildIncomingCallNotification(context: Context, peerName: String): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Sonido de alarma (más agresivo que ringtone) para alertar al supervisor.
        val alarmSound = android.media.RingtoneManager.getDefaultUri(
            android.media.RingtoneManager.TYPE_ALARM
        )
        // Ciclo 10: SIN acciones en el banner. El banner solo suena/avisa y, al tocarlo
        // (o por fullScreenIntent), abre la app que muestra SIEMPRE el overlay central
        // de llamada entrante (Aceptar/Rechazar en el centro). Así la decisión la toma
        // el overlay central, no el banner.
        return NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(
                android.graphics.BitmapFactory.decodeResource(
                    context.resources, R.drawable.ic_alerta_campana
                )
            )
            .setContentTitle(context.getString(R.string.notification_emergency_title))
            .setContentText(context.getString(R.string.notification_emergency_text, peerName))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_emergency_text, peerName)))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 800, 300, 800, 300, 800))
            .setTimeoutAfter(30000) // deja de sonar a los 30 s si no se contesta
            .setFullScreenIntent(contentIntent, true)
            .build()
    }
}
