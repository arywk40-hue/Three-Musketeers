package com.eldercareguardian.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.activePatientStore: DataStore<Preferences> by preferencesDataStore(name = "eldercare_active_patient")

class ActivePatientPreferences(private val context: Context) {

    companion object {
        private val KEY_ACTIVE_PATIENT_ID = longPreferencesKey("active_patient_id")
        const val DEFAULT_PATIENT_ID = 0L

        @Volatile
        private var INSTANCE: ActivePatientPreferences? = null

        fun getInstance(context: Context): ActivePatientPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ActivePatientPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val activePatientId: Flow<Long> = context.activePatientStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PATIENT_ID] ?: DEFAULT_PATIENT_ID
    }

    suspend fun setActivePatientId(id: Long) {
        context.activePatientStore.edit { prefs ->
            prefs[KEY_ACTIVE_PATIENT_ID] = id
        }
    }

    suspend fun reset() {
        context.activePatientStore.edit { it.clear() }
    }
}
