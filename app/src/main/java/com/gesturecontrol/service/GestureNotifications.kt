package com.gesturecontrol.service

import com.gesturecontrol.ui.AppLanguage
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gesturecontrol.R
import com.gesturecontrol.ui.MainActivity

object GestureNotifications {
    const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "gesture_control"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(base: Context, statusText: String): Notification {
        // Wrapped on every build: a language switch applies to the running service's notification too.
        val context = AppLanguage.wrap(base)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            context,
            1,
            Intent(context, GestureService::class.java).setAction(GestureService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(statusText)
            .setContentIntent(openApp)
            .addAction(0, context.getString(R.string.notification_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun statusText(base: Context, status: ServiceStatus): String = statusTextIn(AppLanguage.wrap(base), status)

    private fun statusTextIn(context: Context, status: ServiceStatus): String = when (status) {
        ServiceStatus.Off -> context.getString(R.string.service_status_off)
        ServiceStatus.Waiting -> context.getString(R.string.service_status_waiting)
        is ServiceStatus.Active -> context.getString(R.string.service_status_active, status.appLabel)
        ServiceStatus.PausedDebug -> context.getString(R.string.service_status_paused_debug)
        ServiceStatus.ScreenOff -> context.getString(R.string.service_status_screen_off)
    }
}
