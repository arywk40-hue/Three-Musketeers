package com.eldercareguardian.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AlertEventEntity::class, PatientEntity::class, HealthDataEntity::class], version = 4, exportSchema = false)
abstract class ElderCareDatabase : RoomDatabase() {
    abstract fun alertEventDao(): AlertEventDao
    abstract fun patientDao(): PatientDao
    abstract fun healthDataDao(): HealthDataDao

    companion object {
        @Volatile private var INSTANCE: ElderCareDatabase? = null

        fun getInstance(context: Context): ElderCareDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): ElderCareDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                ElderCareDatabase::class.java,
                "eldercare_encrypted.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

            // SQLCipher native library is not available in unit test (Robolectric) environments.
            // Fall back to a plain unencrypted database so ViewModel tests can run without crashing.
            if (DatabaseEncryption.isSqlCipherAvailable()) {
                builder.openHelperFactory(DatabaseEncryption.supportFactory(context))
            }

            return builder.build()
        }
    }
}
