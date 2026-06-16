package com.eldercareguardian.notifications

import android.content.Context
import android.os.Build
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
 *
 * Security hardening (Session 16):
 *  - SMS body no longer includes GPS coordinates (PHI leak over unencrypted
 *    carrier network). Location detail is available in-app only.
 *  - Alert reason is genericized to a category label rather than raw sensor
 *    values to minimize PHI exposure.
 */
object CaregiverAlertDispatcher {

    /**
     * Dispatch the appropriate alert based on level.
     *
     * @param context     Application context.
     * @param level       The alert level to dispatch.
     * @param reason      Human-readable reason for the alert (e.g. "SOS pressed").
     * @param caregiverPhone  Caregiver phone number in E.164 format.
     * @param location    Optional GPS location (used for in-app display only, NOT sent via SMS).
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
                        context = context,
                        phone = caregiverPhone,
                        message = buildSmsMessage(level, reason),
                    )
                }
            }
            CaregiverAlertStatus.Emergency -> {
                if (enableSms && caregiverPhone.isNotBlank()) {
                    sendSms(
                        context = context,
                        phone = caregiverPhone,
                        message = buildSmsMessage(level, reason),
                    )
                }
                // Emergency: prompt the caregiver to call — handled by ViewModel via buildCaregiverDialIntent()
            }
        }
    }

    /**
     * Builds a PHI-safe SMS message.
     *
     * - NO GPS coordinates (SMS travels unencrypted over carrier network).
     * - Reason is genericized to a category label to minimize PHI.
     * - Full detail (location, raw vitals) is available in the app notification.
     */
    fun buildSmsMessage(
        level: CaregiverAlertStatus,
        reason: String,
    ): String {
        val levelTag = when (level) {
            CaregiverAlertStatus.Warning -> "⚠️ WARNING"
            CaregiverAlertStatus.Emergency -> "🚨 EMERGENCY"
            else -> "ℹ️ CHECK"
        }
        // Genericize the reason to a safe category label.
        val safeReason = sanitizeReason(reason)
        return buildString {
            append("ElderCare Guardian — $levelTag\n")
            append("Alert: $safeReason\n")
            append("Please open the app for details.")
        }
    }

    /**
     * Maps raw alert reasons to safe category labels that don't contain PHI.
     * Raw sensor values, coordinates, and patient-specific info are stripped.
     */
    private fun sanitizeReason(reason: String): String {
        val lower = reason.lowercase()
        return when {
            lower.contains("sos") -> "SOS activated"
            lower.contains("fall") -> "Fall detected"
            lower.contains("heart") || lower.contains("hr") || lower.contains("bpm") -> "Vitals alert"
            lower.contains("spo2") || lower.contains("oxygen") -> "Vitals alert"
            lower.contains("ecg") || lower.contains("afib") -> "Vitals alert"
            lower.contains("inactiv") -> "Inactivity alert"
            lower.contains("dehydra") -> "Wellness alert"
            lower.contains("fatigue") || lower.contains("exert") -> "Wellness alert"
            lower.contains("battery") -> "Device alert"
            else -> "Health alert"
        }
    }

    private fun sendSms(context: Context, phone: String, message: String) {
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    ?: return  // getSystemService can return null — guard it
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(message)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phone, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            }
        } catch (e: Exception) {
            android.util.Log.e("CaregiverAlertDispatcher", "SMS failed: ${e.message}")
        }
    }
}
