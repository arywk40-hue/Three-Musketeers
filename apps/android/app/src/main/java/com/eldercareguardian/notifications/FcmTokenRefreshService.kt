package com.eldercareguardian.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmTokenRefreshService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            FcmTokenManager.refreshToken(this@FcmTokenRefreshService, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Notification payloads are auto-displayed by the system tray when
        // the app is backgrounded. Data payloads arrive here. For the pilot,
        // we log them for debugging; future sessions may add custom handling
        // (e.g. triggering an in-app alert overlay when the app is foregrounded).
        val data = message.data
        if (data.isNotEmpty()) {
            android.util.Log.i(
                "FcmTokenRefreshService",
                "FCM data message received: alertLevel=${data["alertLevel"]}, " +
                    "reason=${data["reason"]}, patient=${data["patientName"]}"
            )
        }
        message.notification?.let { notification ->
            android.util.Log.i(
                "FcmTokenRefreshService",
                "FCM notification: title=${notification.title}, body=${notification.body}"
            )
        }
    }
}
