package com.eldercareguardian.ble

import android.content.ContentResolver
import android.net.Uri
import com.eldercareguardian.data.SensorFrame
import com.eldercareguardian.data.SmartSuitDataSource
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

class JsonFileDataSource(
    contentResolver: ContentResolver,
    uri: Uri,
) : SmartSuitDataSource {
    private val gson = Gson()
    private val frameList: List<SensorFrame> = loadFrames(contentResolver, uri)

    override val frames: Flow<SensorFrame> = flow {
        if (frameList.isEmpty()) return@flow

        for (i in 1 until frameList.size) {
            emit(frameList[i - 1])
            delay((frameList[i].timestampMillis - frameList[i - 1].timestampMillis).coerceIn(100L, 5000L))
        }
        emit(frameList.last())
    }

    private fun loadFrames(contentResolver: ContentResolver, uri: Uri): List<SensorFrame> {
        return try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return emptyList()
            gson.fromJson(json, Array<SensorFrame>::class.java).toList()
        } catch (e: IOException) {
            emptyList()
        }
    }
}
