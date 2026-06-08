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

/**
 * System notification helper for ElderCare Guardian.
 *
 * Phase 7 update: Supports the new 4-level alert system.
 *  - Emergency: high-importance notification with alarm category.
 *  - Warning: high-importance with escalation text.
 *  - Check/Normal: not posted as notifications (handled silently in UI).
 */
object NotificationHelper {
    const val CHANNEL_ID_CAREGIVER = "caregiver_alerts"
    const val CHANNEL_ID_WARNING = "caregiver_warnings"
    const val CHANNEL_NAME = "Emergency Alerts"
    const val CHANNEL_NAME_WARNING = "Caregiver Warnings"
    const val NOTIFICATION_ID_EMERGENCY = 1001
    const val NOTIFICATION_ID_WARNING = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return

        // Emergency channel — maximum importance
        if (manager.getNotificationChannel(CHANNEL_ID_CAREGIVER) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID_CAREGIVER,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "🚨 Fired on Emergency events: fall detected, SOS pressed, SpO2 < 90%, severe arrhythmia."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300, 200, 600)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }

        // Warning channel — high importance but lower urgency than Emergency
        if (manager.getNotificationChannel(CHANNEL_ID_WARNING) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID_WARNING,
                CHANNEL_NAME_WARNING,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "⚠️ Fired on Warning events: SpO2 90–94%, HR 110–130, medium fall risk."
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Post an Emergency notification. Replaces any existing emergency notification.
     * No-op if the event is not Emergency level.
     */
    fun postEmergencyAlert(context: Context, event: AlertEvent?, openAppIntent: Intent) {
        if (event?.level != CaregiverAlertStatus.Emergency) return
        ensureChannels(context)

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val body = event.reason.displayLabel.ifBlank { "Check on patient immediately" }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CAREGIVER)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("🚨 Emergency — ElderCare Guardian")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\nTap to open app and call caregiver."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)  // Emergency stays until manually dismissed
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID_EMERGENCY, notification)
    }

    /**
     * Post a Warning notification for mid-tier alert events.
     * No-op if the event is not Warning level.
     */
    fun postWarningAlert(context: Context, event: AlertEvent?, openAppIntent: Intent) {
        if (event?.level != CaregiverAlertStatus.Warning) return
        ensureChannels(context)

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val body = event.reason.displayLabel.ifBlank { "Attention needed" }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_WARNING)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("⚠️ Warning — ElderCare Guardian")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID_WARNING, notification)
    }

    /** Cancel the emergency notification (e.g., after caregiver acknowledges). */
    fun cancelEmergency(context: Context) {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        manager.cancel(NOTIFICATION_ID_EMERGENCY)
    }

    /** Cancel the warning notification. */
    fun cancelWarning(context: Context) {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        manager.cancel(NOTIFICATION_ID_WARNING)
    }

    /** Legacy alias — kept for compatibility. Use postEmergencyAlert() for new code. */
    @Deprecated("Use postEmergencyAlert()", ReplaceWith("postEmergencyAlert(context, event, openAppIntent)"))
    fun postUrgentAlert(context: Context, event: AlertEvent?, openAppIntent: Intent) =
        postEmergencyAlert(context, event, openAppIntent)

    /** Legacy alias — kept for compatibility. Use cancelEmergency() for new code. */
    @Deprecated("Use cancelEmergency()", ReplaceWith("cancelEmergency(context)"))
    fun cancelUrgent(context: Context) = cancelEmergency(context)
}
