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
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ElderCareDatabase::class.java,
                    "eldercare_encrypted.db",
                )
                    .openHelperFactory(DatabaseEncryption.supportFactory(context))
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
