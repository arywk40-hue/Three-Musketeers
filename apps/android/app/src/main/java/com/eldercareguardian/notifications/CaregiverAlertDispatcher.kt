package com.eldercareguardian.notifications

import android.content.Context
import android.telephony.SmsManager
import android.location.Location
import com.eldercareguardian.data.CaregiverAlertStatus

/**
 * Caregiver alert dispatcher — Phase 7.
 *
 * Implements the four-level alert pipeline:
 *
 *  Level 1 — Normal:    No action.
 *  Level 2 — Check:     App notification only.
 *  Level 3 — Warning:   App notification + SMS to caregiver.
 *  Level 4 — Emergency: App notification + SMS with location + prompt auto-dial.
 *
 * SMS approach: Uses Android's SmsManager (requires SEND_SMS permission).
 * For production where SEND_SMS may be refused by Play Store, replace with
 * a backend endpoint (Twilio / AWS SNS) that accepts a POST request.
 *
 * Permission required: android.permission.SEND_SMS
 * Add to AndroidManifest.xml before enabling the SMS path.
 *
 * Note: Auto-dialing (ACTION_CALL) requires CALL_PHONE permission and is
 * not suitable for all markets without explicit user consent. The current
 * implementation uses ACTION_DIAL (no permission needed) which pre-fills
 * the number but requires user confirmation to place the call.
 */
object CaregiverAlertDispatcher {

    /**
     * Dispatch the appropriate alert based on level.
     *
     * @param context     Application context.
     * @param level       The alert level to dispatch.
     * @param reason      Human-readable reason for the alert (e.g. "SOS pressed").
     * @param caregiverPhone  Caregiver phone number in E.164 format.
     * @param location    Optional GPS location to include in SMS.
     * @param enableSms   Set to true when SEND_SMS permission is granted.
     */
    fun dispatch(
        context: Context,
        level: CaregiverAlertStatus,
        reason: String,
        caregiverPhone: String,
        location: Location? = null,
        enableSms: Boolean = false,
    ) {
        when (level) {
            CaregiverAlertStatus.Normal -> return  // Nothing to dispatch
            CaregiverAlertStatus.Check -> {
                // Notification only — handled by NotificationHelper
            }
            CaregiverAlertStatus.Warning -> {
                if (enableSms && caregiverPhone.isNotBlank()) {
                    sendSms(
                        phone = caregiverPhone,
                        message = buildSmsMessage(level, reason, location),
                    )
                }
            }
            CaregiverAlertStatus.Emergency -> {
                if (enableSms && caregiverPhone.isNotBlank()) {
                    sendSms(
                        phone = caregiverPhone,
                        message = buildSmsMessage(level, reason, location),
                    )
                }
                // Emergency: prompt the caregiver to call — handled by ViewModel via buildCaregiverDialIntent()
            }
        }
    }

    fun buildSmsMessage(
        level: CaregiverAlertStatus,
        reason: String,
        location: Location?,
    ): String {
        val levelTag = when (level) {
            CaregiverAlertStatus.Warning -> "⚠️ WARNING"
            CaregiverAlertStatus.Emergency -> "🚨 EMERGENCY"
            else -> "ℹ️ CHECK"
        }
        val locationStr = if (location != null) {
            "\nLocation: https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "\nLocation: unavailable"
        }
        return buildString {
            append("ElderCare Guardian — $levelTag\n")
            append("Reason: $reason\n")
            append(locationStr)
            append("\nPlease check on your patient immediately.")
        }
    }

    @Suppress("DEPRECATION")
    private fun sendSms(phone: String, message: String) {
        try {
            // SmsManager.getDefault() is deprecated in API 31+; use context-based approach in production.
            val smsManager = SmsManager.getDefault()
            // Split message if longer than 160 chars
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phone, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            }
        } catch (e: Exception) {
            // Log failure — do NOT crash the app on SMS failure
            android.util.Log.e("CaregiverAlertDispatcher", "SMS failed: ${e.message}")
        }
    }
}
