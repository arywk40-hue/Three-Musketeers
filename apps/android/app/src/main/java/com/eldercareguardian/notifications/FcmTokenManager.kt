package com.eldercareguardian.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

private val Context.fcmStore: DataStore<Preferences> by preferencesDataStore(name = "eldercare_fcm")

object FcmTokenManager {

    private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_device_token")

    private fun ensureInitialized(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
    }

    suspend fun getToken(context: Context): String? {
        val store = context.applicationContext.fcmStore
        val stored = store.data.first()[KEY_FCM_TOKEN]
        if (stored != null) return stored

        return try {
            ensureInitialized(context)
            val token = FirebaseMessaging.getInstance().token.await()
            store.edit { it[KEY_FCM_TOKEN] = token }
            token
        } catch (e: Exception) {
            android.util.Log.w("FcmTokenManager", "Failed to fetch FCM token: ${e.message}")
            null
        }
    }

    suspend fun refreshToken(context: Context, token: String) {
        context.applicationContext.fcmStore.edit { it[KEY_FCM_TOKEN] = token }
    }
}
