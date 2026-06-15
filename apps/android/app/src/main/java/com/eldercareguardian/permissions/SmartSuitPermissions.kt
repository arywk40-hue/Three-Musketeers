package com.eldercareguardian.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Two-tier runtime permission system.
 *
 * Security hardening (Session 16):
 *  - Permissions are now split into [corePermissions] (required to function)
 *    and [enhancedPermissions] (progressive disclosure, requested when needed).
 *  - SEND_SMS moved to enhanced tier — only requested when the caregiver alert
 *    SMS path is explicitly enabled by the user.
 *  - ACCESS_BACKGROUND_LOCATION in enhanced tier — requested only after the
 *    user enables background monitoring.
 */
object SmartSuitPermissions {

    /**
     * Core permissions required for basic app functionality (BLE + sensors).
     * These are requested on first launch during the permission flow.
     */
    fun corePermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        add(Manifest.permission.BODY_SENSORS)

        // POST_NOTIFICATIONS required on API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Enhanced permissions requested progressively when the user enables
     * specific features (background monitoring, SMS alerts, etc.).
     */
    fun enhancedPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // Background location (API 29+) — requested when user enables background monitoring
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        // SMS — requested only when user enables the caregiver SMS alert path
        add(Manifest.permission.SEND_SMS)
    }

    /**
     * Returns the combined list of all runtime permissions for backward
     * compatibility with existing code that uses [requiredRuntimePermissions].
     */
    fun requiredRuntimePermissions(): List<String> = corePermissions() + enhancedPermissions()

    /**
     * Returns permissions from the specified tier that have not been granted.
     *
     * @param tier "core", "enhanced", or "all" (default: "all")
     */
    fun missingPermissions(context: Context, tier: String = "all"): List<String> {
        val permissions = when (tier) {
            "core" -> corePermissions()
            "enhanced" -> enhancedPermissions()
            else -> requiredRuntimePermissions()
        }
        return permissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /** Backward-compatible overload. */
    fun missingPermissions(context: Context): List<String> = missingPermissions(context, "all")

    fun label(permission: String): String = when (permission) {
        Manifest.permission.BLUETOOTH_SCAN -> "Nearby device scan"
        Manifest.permission.BLUETOOTH_CONNECT -> "Nearby device connect"
        Manifest.permission.ACCESS_FINE_LOCATION -> "Location for BLE scan"
        Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Background location"
        Manifest.permission.BODY_SENSORS -> "Body sensors"
        Manifest.permission.ACTIVITY_RECOGNITION -> "Activity recognition"
        Manifest.permission.SEND_SMS -> "Send SMS alerts"
        Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
        else -> permission.substringAfterLast(".")
    }
}
