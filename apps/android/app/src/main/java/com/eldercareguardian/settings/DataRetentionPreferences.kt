package com.eldercareguardian.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.retentionStore: DataStore<Preferences> by preferencesDataStore(
    name = "eldercare_data_retention",
)

class DataRetentionPreferences private constructor(private val context: Context) {
    val retentionDays: Flow<Int> = context.retentionStore.data.map { prefs ->
        prefs[KEY_RETENTION_DAYS] ?: DEFAULT_RETENTION_DAYS
    }

    suspend fun setRetentionDays(days: Int) {
        context.retentionStore.edit { it[KEY_RETENTION_DAYS] = days.coerceIn(7, 90) }
    }

    suspend fun reset() {
        context.retentionStore.edit { it.clear() }
    }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 7
        private val KEY_RETENTION_DAYS = intPreferencesKey("retention_days")

        @Volatile
        private var INSTANCE: DataRetentionPreferences? = null

        fun getInstance(context: Context): DataRetentionPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataRetentionPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
