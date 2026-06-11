package com.eldercareguardian.notifications

import com.eldercareguardian.data.CaregiverAlertStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

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

            val body = buildString {
                append("{\"token\":\"")
                append(caregiverFcmToken)
                append("\",\"level\":\"")
                append(level.name)
                append("\",\"reason\":\"")
                append(reason.replace("\"", "\\\""))
                append("\",\"patientName\":\"")
                append(patientName.replace("\"", "\\\""))
                append("\"}")
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

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
