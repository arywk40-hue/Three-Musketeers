package com.eldercareguardian.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `patients` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `caregiverName` TEXT NOT NULL DEFAULT '',
                `caregiverPhone` TEXT NOT NULL DEFAULT '',
                `notes` TEXT NOT NULL DEFAULT '',
                `createdAt` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("ALTER TABLE `alert_events` ADD COLUMN `patientId` INTEGER NOT NULL DEFAULT 0")
    }
}
