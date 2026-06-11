package com.eldercareguardian.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `health_data` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `patientId` INTEGER NOT NULL DEFAULT 0,
                `timestampMillis` INTEGER NOT NULL,
                `heartRateBpm` INTEGER NOT NULL,
                `spo2Percent` REAL NOT NULL,
                `respiratoryRate` INTEGER NOT NULL,
                `skinTempC` REAL NOT NULL,
                `posture` TEXT NOT NULL,
                `fatigue` TEXT NOT NULL,
                `fallRisk` TEXT NOT NULL,
                `caregiverAlert` TEXT NOT NULL,
                `batteryPercent` INTEGER
            )
            """,
        )
    }
}
