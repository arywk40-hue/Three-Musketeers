package com.eldercareguardian.notifications

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Manages the FCM device token.
 *
 * Security hardening (Session 16):
 *  - Token is now stored in [EncryptedSharedPreferences] (AES-256-GCM) instead
 *    of plaintext DataStore.
 *  - If AndroidKeyStore is unavailable, falls back to in-memory cache only
 *    (token will be re-fetched on next process start).
 */
object FcmTokenManager {

    private const val TAG = "FcmTokenManager"
    private const val PREF_FILE = "eldercare_fcm_secure"
    private const val KEY_FCM_TOKEN = "fcm_device_token"

    /** In-memory cache — used as primary when KeyStore is unavailable. */
    @Volatile
    private var cachedToken: String? = null

    private fun ensureInitialized(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
    }

    /**
     * Returns the encrypted SharedPreferences, or null if AndroidKeyStore
     * is unavailable (rooted device, factory reset, test environment).
     */
    private fun securePrefs(context: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREF_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, using in-memory cache", e)
            null
        }
    }

    suspend fun getToken(context: Context): String? {
        // Check in-memory cache first
        cachedToken?.let { return it }

        // Check encrypted storage
        val stored = securePrefs(context)?.getString(KEY_FCM_TOKEN, null)
        if (stored != null) {
            cachedToken = stored
            return stored
        }

        // Fetch from Firebase
        return try {
            ensureInitialized(context)
            val token = FirebaseMessaging.getInstance().token.await()
            storeToken(context, token)
            token
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch FCM token: ${e.message}")
            null
        }
    }

    suspend fun refreshToken(context: Context, token: String) {
        storeToken(context, token)
    }

    private fun storeToken(context: Context, token: String) {
        cachedToken = token
        try {
            securePrefs(context)?.edit()?.putString(KEY_FCM_TOKEN, token)?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist FCM token to encrypted storage", e)
            // Token remains in cachedToken for this process lifetime
        }
    }
}
