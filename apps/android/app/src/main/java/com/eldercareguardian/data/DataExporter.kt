package com.eldercareguardian.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.eldercareguardian.database.AlertEventEntity
import com.eldercareguardian.database.HealthDataEntity
import com.eldercareguardian.database.PatientEntity
import com.google.gson.GsonBuilder
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportPackage(
    val exportedAt: String,
    val patients: List<PatientEntity>,
    val healthData: List<HealthDataEntity>,
    val alertEvents: List<AlertEventEntity>,
)

/**
 * Exports patient data as JSON for sharing / backup.
 *
 * Security hardening (Session 16):
 *  - Export files are now written to app-private [Context.getFilesDir] instead
 *    of [Context.getCacheDir] which may be accessible to other apps on
 *    rooted devices or via backup extraction.
 *  - Added [cleanup] method to delete export files after sharing.
 */
object DataExporter {

    private const val TAG = "DataExporter"
    private const val EXPORT_DIR = "exports"
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

        // Write to app-private storage (not cacheDir) for better security
        val dir = File(context.filesDir, EXPORT_DIR)
        if (!dir.mkdirs() && !dir.exists()) {
            throw IOException("Failed to create export directory: ${dir.absolutePath}")
        }

        val file = File(dir, "eldercare_export_$timestamp.json")
        try {
            file.writeText(json)
        } catch (e: IOException) {
            Log.e(TAG, "Export write failed: ${e.message}")
            throw IOException("Export failed — check device storage: ${e.message}", e)
        }

        // Restrict file permissions to owner only
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)

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

    /**
     * Deletes all export files from the exports directory.
     * Call this after the share intent completes to avoid leaving PHI on disk.
     */
    fun cleanup(context: Context) {
        val dir = File(context.filesDir, EXPORT_DIR)
        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                if (file.delete()) {
                    Log.d(TAG, "Cleaned up export: ${file.name}")
                }
            }
        }
    }

    /**
     * Deletes a specific export file.
     */
    fun cleanup(file: File) {
        if (file.exists() && file.delete()) {
            Log.d(TAG, "Cleaned up export: ${file.name}")
        }
    }
}
