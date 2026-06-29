package com.eldercareguardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eldercareguardian.consent.ConsentPreferences
import com.eldercareguardian.service.ElderCareMonitorService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Only restart the monitoring service if the user has previously
            // granted DPDPA consent. Without this guard, the service could
            // start with no database initialized and crash.
            val consented = runBlocking {
                ConsentPreferences.getInstance(context).isConsentGranted.first()
            }
            if (consented) {
                context.startForegroundService(
                    ElderCareMonitorService.startIntent(context)
                )
            }
        }
    }
}

