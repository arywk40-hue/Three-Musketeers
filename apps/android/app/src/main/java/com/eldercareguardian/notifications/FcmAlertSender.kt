package com.eldercareguardian.notifications

import com.eldercareguardian.data.CaregiverAlertStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends caregiver alerts to the FCM backend.
 *
 * Security hardening (Session 16):
 *  - Replaced manual `buildString` JSON construction with [JSONObject]
 *    which properly escapes all values, preventing JSON injection.
 */
object FcmAlertSender {

    suspend fun sendAlert(
        backendUrl: String,
        level: CaregiverAlertStatus,
        reason: String,
        patientName: String,
        caregiverFcmToken: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (backendUrl.isBlank() || caregiverFcmToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Backend URL or FCM token is empty"))
        }

        try {
            val url = URL("$backendUrl/notify")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000

            // JSONObject.put() automatically escapes special characters,
            // preventing injection attacks from crafted reason/patientName values.
            val body = JSONObject().apply {
                put("token", caregiverFcmToken)
                put("level", level.name)
                put("reason", reason)
                put("patientName", patientName)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

            val code = conn.responseCode
            if (code in 200..299) {
                Result.success(Unit)
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                Result.failure(Exception("FCM backend returned $code: $error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
