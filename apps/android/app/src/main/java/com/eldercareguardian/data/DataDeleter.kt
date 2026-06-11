package com.eldercareguardian.data

import android.content.Context
import com.eldercareguardian.consent.ConsentPreferences
import com.eldercareguardian.database.AlertEventDao
import com.eldercareguardian.database.HealthDataDao
import com.eldercareguardian.database.PatientDao
import com.eldercareguardian.settings.ActivePatientPreferences
import com.eldercareguardian.settings.CaregiverPreferences
import com.eldercareguardian.settings.DataRetentionPreferences

object DataDeleter {

    suspend fun deleteAllData(
        context: Context,
        alertEventDao: AlertEventDao,
        healthDataDao: HealthDataDao,
        patientDao: PatientDao,
        consentPreferences: ConsentPreferences,
    ) {
        patientDao.deleteAll()
        alertEventDao.deleteAll()
        healthDataDao.deleteAll()

        CaregiverPreferences.getInstance(context).reset()
        ActivePatientPreferences.getInstance(context).reset()
        DataRetentionPreferences.getInstance(context).reset()
        consentPreferences.revokeConsent()
    }
}
