package com.smartsuit.notifications

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Builds the tap-to-dial intent for a caregiver phone number.
 *
 * Uses [Intent.ACTION_DIAL] (not [Intent.ACTION_CALL]) so no `CALL_PHONE`
 * permission is required — the user is shown a dialer with the number
 * pre-filled and they tap to place the call.
 *
 * The phone number is no longer hard-coded here; the caller is expected to
 * read it from [com.smartsuit.settings.CaregiverPreferences] and validate
 * it with [com.smartsuit.settings.isValidPhone] before calling this
 * object's methods.
 */
object CaregiverContact {
    fun dialIntent(phoneNumber: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun launchDialer(context: Context, phoneNumber: String): Boolean =
        try {
            val intent = dialIntent(phoneNumber)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
}
