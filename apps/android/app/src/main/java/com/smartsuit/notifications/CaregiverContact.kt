package com.smartsuit.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Builds the tap-to-dial intent for the configured caregiver phone number.
 *
 * Uses ACTION_DIAL (not ACTION_CALL) so no CALL_PHONE permission is required —
 * the user is shown a dialer with the number pre-filled, and they tap call.
 *
 * For a production deployment, [caregiverPhoneNumber] should be configurable by
 * the user in a settings screen. For the showcase it is a hard-coded
 * placeholder.
 */
object CaregiverContact {
    const val CAREGIVER_PHONE_NUMBER = "+1-555-0100"
    const val CAREGIVER_DISPLAY_NAME = "Primary caregiver"

    fun dialIntent(phoneNumber: String = CAREGIVER_PHONE_NUMBER): Intent =
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun launchDialer(context: Context, phoneNumber: String = CAREGIVER_PHONE_NUMBER): Boolean =
        try {
            val intent = dialIntent(phoneNumber)
            context.startActivity(intent)
            true
        } catch (_: android.content.ActivityNotFoundException) {
            false
        }
}
