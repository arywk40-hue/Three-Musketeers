package com.smartsuit.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.smartsuit.data.AlertEvent
import com.smartsuit.data.CaregiverAlertStatus

object NotificationHelper {
    const val CHANNEL_ID_CAREGIVER = "caregiver_alerts"
    const val CHANNEL_NAME = "Caregiver alerts"
    const val NOTIFICATION_ID_URGENT = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID_CAREGIVER) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID_CAREGIVER,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Fired when the patient's vitals or sensors trigger an Urgent caregiver alert."
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun postUrgentAlert(context: Context, event: AlertEvent?, openAppIntent: Intent) {
        if (event?.level != CaregiverAlertStatus.Urgent) return
        ensureChannel(context)

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = "Urgent caregiver alert"
        val body = event.reason.displayLabel.ifBlank { "Check on patient immediately" }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CAREGIVER)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID_URGENT, notification)
    }

    fun cancelUrgent(context: Context) {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        manager.cancel(NOTIFICATION_ID_URGENT)
    }
}
