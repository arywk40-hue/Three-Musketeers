package com.smartsuit.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object SmartSuitPermissions {
    fun requiredRuntimePermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        add(Manifest.permission.BODY_SENSORS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    fun missingPermissions(context: Context): List<String> {
        return requiredRuntimePermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun label(permission: String): String = when (permission) {
        Manifest.permission.BLUETOOTH_SCAN -> "Nearby device scan"
        Manifest.permission.BLUETOOTH_CONNECT -> "Nearby device connect"
        Manifest.permission.ACCESS_FINE_LOCATION -> "Location for BLE scan"
        Manifest.permission.BODY_SENSORS -> "Body sensors"
        Manifest.permission.ACTIVITY_RECOGNITION -> "Activity recognition"
        else -> permission.substringAfterLast(".")
    }
}
