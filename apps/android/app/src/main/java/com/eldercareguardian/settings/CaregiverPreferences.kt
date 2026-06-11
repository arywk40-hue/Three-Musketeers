package com.eldercareguardian.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "eldercare_caregiver",
)

/**
 * DataStore-backed preferences for the caregiver contact.
 *
 * Flows are non-throwing: any [IOException] reading the underlying file
 * (e.g. disk corruption on first run) emits an empty preferences snapshot
 * so the dashboard can still render with default values. Writes go
 * through [DataStore.edit], which is atomic and serialised by the
 * DataStore actor.
 */
class CaregiverPreferences private constructor(context: Context) {
    private val store: DataStore<Preferences> = context.applicationContext.dataStore

    val caregiverPhone: Flow<String> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY_PHONE] ?: DEFAULT_PHONE }

    val caregiverName: Flow<String> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[KEY_NAME] ?: DEFAULT_NAME }

    suspend fun setCaregiverPhone(phone: String) {
        store.edit { it[KEY_PHONE] = phone }
    }

    suspend fun setCaregiverName(name: String) {
        store.edit { it[KEY_NAME] = name }
    }

    suspend fun setBoth(name: String, phone: String) {
        store.edit {
            it[KEY_NAME] = name
            it[KEY_PHONE] = phone
        }
    }

    suspend fun reset() {
        store.edit { it.clear() }
    }

    companion object {
        const val DEFAULT_NAME = "Caregiver"
        const val DEFAULT_PHONE = ""
        private val KEY_PHONE = stringPreferencesKey("caregiver_phone")
        private val KEY_NAME = stringPreferencesKey("caregiver_name")

        @Volatile
        private var INSTANCE: CaregiverPreferences? = null

        fun getInstance(context: Context): CaregiverPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CaregiverPreferences(context).also { INSTANCE = it }
            }
        }
    }
}

/**
 * True if [phone] contains at least [minDigits] digit characters (0-9).
 * Returns false for null, blank, or shorter numeric content.
 */
fun isValidPhone(phone: String?, minDigits: Int = 7): Boolean {
    if (phone.isNullOrBlank()) return false
    val digitCount = phone.count { it.isDigit() }
    return digitCount >= minDigits
}

/** Returns the number of digits in [phone], or 0 for null/blank. */
fun phoneDigitCount(phone: String?): Int =
    phone?.count { it.isDigit() } ?: 0
