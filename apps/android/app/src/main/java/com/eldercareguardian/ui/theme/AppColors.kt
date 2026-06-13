package com.eldercareguardian.ui.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    val background = Color(0xFFF0F4F8)
    val surface = Color(0xFFFFFFFF)
    val surfaceSecondary = Color(0xFFF8FAFC)
    val surfaceTertiary = Color(0xFFF1F5F9)

    val primary = Color(0xFF0F766E)
    val primaryDark = Color(0xFF0D5E57)
    val primaryLight = Color(0xFFE6F7F5)

    val success = Color(0xFF059669)
    val successLight = Color(0xFFECFDF5)
    val warning = Color(0xFFD97706)
    val warningLight = Color(0xFFFFFBEB)
    val danger = Color(0xFFDC2626)
    val dangerLight = Color(0xFFFEF2F2)

    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF475569)
    val textTertiary = Color(0xFF94A3B8)
    val textOnPrimary = Color(0xFFFFFFFF)

    val border = Color(0xFFE2E8F0)
    val borderLight = Color(0xFFF1F5F9)

    fun severityColor(
        isWarning: Boolean = false,
        isDanger: Boolean = false,
    ): Color = when {
        isDanger -> danger
        isWarning -> warning
        else -> primary
    }

    fun progressColor(progress: Float): Color = when {
        progress > 0.75f -> danger
        progress > 0.45f -> warning
        else -> primary
    }
}
