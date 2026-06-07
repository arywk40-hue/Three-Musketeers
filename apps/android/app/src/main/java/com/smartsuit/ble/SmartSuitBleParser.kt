package com.smartsuit.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

object SmartSuitBleParser {
    private const val HEART_RATE_FORMAT_UINT16_FLAG = 0x01

    fun parseHeartRateMeasurement(payload: ByteArray): Int? {
        if (payload.size < 2) return null

        val flags = payload[0].toInt()
        return if ((flags and HEART_RATE_FORMAT_UINT16_FLAG) != 0) {
            if (payload.size < 3) null else unsignedShortLe(payload, offset = 1)
        } else {
            payload[1].toInt() and 0xFF
        }
    }

    fun parseBatteryLevel(payload: ByteArray): Int? {
        return payload.firstOrNull()?.toInt()?.and(0xFF)?.coerceIn(0, 100)
    }

    fun parseFloat32(payload: ByteArray): Float? {
        if (payload.size < 4) return null
        return ByteBuffer.wrap(payload)
            .order(ByteOrder.LITTLE_ENDIAN)
            .getFloat()
    }

    fun parseFloat32Array(payload: ByteArray, expectedCount: Int? = null): List<Float> {
        if (payload.size < 4 || payload.size % 4 != 0) return emptyList()

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val values = MutableList(payload.size / 4) { buffer.getFloat() }
        return if (expectedCount == null || values.size == expectedCount) values else emptyList()
    }

    private fun unsignedShortLe(payload: ByteArray, offset: Int): Int {
        val low = payload[offset].toInt() and 0xFF
        val high = payload[offset + 1].toInt() and 0xFF
        return low or (high shl 8)
    }
}
