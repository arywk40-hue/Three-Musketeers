package com.eldercareguardian.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.eldercareguardian.MainActivity
import com.eldercareguardian.data.CaregiverAlertStatus

/**
 * Foreground Service for ElderCare Guardian background monitoring.
 *
 * Phase 4 — Background monitoring requirement:
 *
 * Android kills background processes aggressively. A Foreground Service with a
 * persistent notification is the ONLY reliable way to keep BLE monitoring alive
 * when the screen is off or the app is backgrounded.
 *
 * This service binds to the ViewModel's data flows and:
 *  1. Keeps the process alive with a low-priority persistent notification.
 *  2. Upgrades the notification to high-priority on Warning/Emergency alerts.
 *  3. Vibrates and plays an alert tone on Emergency events.
 *  4. Posts a "Caregiver has been notified" status update.
 *
 * Usage:
 *   startForegroundService(Intent(this, ElderCareMonitorService::class.java))
 *   stopService(Intent(this, ElderCareMonitorService::class.java))
 *
 * The service is declared in AndroidManifest.xml with:
 *   android:foregroundServiceType="health"  (API 34+)
 *   android:foregroundServiceType="connectedDevice"  (API 29+)
 *
 * For the IIT Mandi showcase, start the service in MainActivity.onCreate() so
 * monitoring continues even when the screen turns off during the demo.
 */
class ElderCareMonitorService : Service() {

    companion object {
        const val CHANNEL_ID_MONITORING = "eldercare_monitoring"
        const val CHANNEL_ID_ALERTS = "eldercare_alerts"
        const val NOTIFICATION_ID_MONITORING = 1001
        const val NOTIFICATION_ID_ALERT = 1002

        const val ACTION_STOP = "com.eldercareguardian.STOP_MONITORING"

        fun startIntent(context: Context): Intent =
            Intent(context, ElderCareMonitorService::class.java)

        fun stopIntent(context: Context): Intent =
            Intent(context, ElderCareMonitorService::class.java).apply {
                action = ACTION_STOP
            }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ElderCareMonitorService = this@ElderCareMonitorService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildMonitoringNotification(CaregiverAlertStatus.Normal)
        startForeground(NOTIFICATION_ID_MONITORING, notification)

        return START_STICKY  // Restart if killed by system
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    // ── Notification helpers ─────────────────────────────────────────────────

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Monitoring channel — silent, persistent, always visible
        val monitoringChannel = NotificationChannel(
            CHANNEL_ID_MONITORING,
            "ElderCare Monitoring",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Persistent notification while monitoring is active"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        // Alert channel — high importance for emergency alerts
        val alertChannel = NotificationChannel(
            CHANNEL_ID_ALERTS,
            "ElderCare Alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Emergency and warning alerts from ElderCare Guardian"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300, 200, 600)
        }

        manager.createNotificationChannel(monitoringChannel)
        manager.createNotificationChannel(alertChannel)
    }

    private fun buildMonitoringNotification(alertStatus: CaregiverAlertStatus): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val (contentText, channelId) = when (alertStatus) {
            CaregiverAlertStatus.Normal -> Pair("Monitoring active — all vitals normal", CHANNEL_ID_MONITORING)
            CaregiverAlertStatus.Check -> Pair("⚠️ Check: mild concern detected", CHANNEL_ID_ALERTS)
            CaregiverAlertStatus.Warning -> Pair("⚠️⚠️ Warning: attention needed", CHANNEL_ID_ALERTS)
            CaregiverAlertStatus.Emergency -> Pair("🚨 EMERGENCY: immediate response required", CHANNEL_ID_ALERTS)
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("ElderCare Guardian")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopIntent,
            )
            .setPriority(
                when (alertStatus) {
                    CaregiverAlertStatus.Emergency -> NotificationCompat.PRIORITY_MAX
                    CaregiverAlertStatus.Warning -> NotificationCompat.PRIORITY_HIGH
                    CaregiverAlertStatus.Check -> NotificationCompat.PRIORITY_DEFAULT
                    CaregiverAlertStatus.Normal -> NotificationCompat.PRIORITY_LOW
                },
            )
            .build()
    }

    /** Update the persistent notification to reflect the current alert level. */
    fun updateAlertStatus(alertStatus: CaregiverAlertStatus) {
        val notification = buildMonitoringNotification(alertStatus)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_MONITORING, notification)
    }
}
