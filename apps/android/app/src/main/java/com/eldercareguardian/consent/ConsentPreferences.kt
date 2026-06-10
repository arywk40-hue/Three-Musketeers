package com.eldercareguardian.consent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.consentDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "eldercare_dpdpa_consent",
)

/**
 * DataStore-backed preferences for DPDPA consent state.
 *
 * Tracks whether the user has accepted the data collection terms
 * required under India's Digital Personal Data Protection Act 2023.
 */
class ConsentPreferences private constructor(context: Context) {
    private val store: DataStore<Preferences> = context.applicationContext.consentDataStore

    /**
     * Emits `true` if the user has granted DPDPA consent, `false` otherwise.
     * On any I/O error reading the file, emits `false` (consent not granted).
     */
    val isConsentGranted: Flow<Boolean> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY_CONSENT_GRANTED] ?: false }

    /**
     * Emits the epoch-millis timestamp when consent was last granted,
     * or `0L` if consent has never been granted.
     */
    val consentTimestamp: Flow<Long> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY_CONSENT_TIMESTAMP] ?: 0L }

    /**
     * Records that the user has accepted the DPDPA data-collection terms.
     * Stores the acceptance timestamp for audit purposes.
     */
    suspend fun grantConsent() {
        store.edit {
            it[KEY_CONSENT_GRANTED] = true
            it[KEY_CONSENT_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    /**
     * Revokes previously granted consent (right to withdraw under DPDPA).
     */
    suspend fun revokeConsent() {
        store.edit {
            it[KEY_CONSENT_GRANTED] = false
            it[KEY_CONSENT_TIMESTAMP] = 0L
        }
    }

    companion object {
        private val KEY_CONSENT_GRANTED = booleanPreferencesKey("dpdpa_consent_granted")
        private val KEY_CONSENT_TIMESTAMP = longPreferencesKey("dpdpa_consent_timestamp")

        @Volatile
        private var INSTANCE: ConsentPreferences? = null

        fun getInstance(context: Context): ConsentPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConsentPreferences(context).also { INSTANCE = it }
            }
        }
    }
}
