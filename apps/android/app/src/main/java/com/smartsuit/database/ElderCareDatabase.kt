package com.smartsuit.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AlertEventEntity::class], version = 1, exportSchema = false)
abstract class ElderCareDatabase : RoomDatabase() {
    abstract fun alertEventDao(): AlertEventDao

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
                    .build().also { INSTANCE = it }
            }
        }
    }
}
