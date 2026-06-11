package com.eldercareguardian.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.eldercareguardian.database.AlertEventEntity
import com.eldercareguardian.database.HealthDataEntity
import com.eldercareguardian.database.PatientEntity
import com.google.gson.GsonBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportPackage(
    val exportedAt: String,
    val patients: List<PatientEntity>,
    val healthData: List<HealthDataEntity>,
    val alertEvents: List<AlertEventEntity>,
)

object DataExporter {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    fun exportJson(
        context: Context,
        patients: List<PatientEntity>,
        healthData: List<HealthDataEntity>,
        alertEvents: List<AlertEventEntity>,
    ): File {
        val timestamp = dateFormat.format(Date())
        val export = ExportPackage(
            exportedAt = timestamp,
            patients = patients,
            healthData = healthData,
            alertEvents = alertEvents,
        )
        val json = gson.toJson(export)
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, "eldercare_export_$timestamp.json")
        file.writeText(json)
        return file
    }

    fun createShareIntent(context: Context, file: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
