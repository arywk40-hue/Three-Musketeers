package com.smartsuit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartsuit.data.CaregiverAlertStatus
import com.smartsuit.data.FatigueStatus
import com.smartsuit.data.PostureStatus
import com.smartsuit.data.RiskStatus

/**
 * Sealed set of status values that a [StatusPill] can render. Replacing the
 * old `status: Any` parameter with this sealed interface makes the
 * `when` block exhaustive — adding a new case to any of the referenced
 * enums (e.g. a new [CaregiverAlertStatus] tier) is now a compile error
 * inside [StatusPill.colorFor] until the mapping is updated.
 */
sealed interface PillStatus {
    data class CaregiverAlert(val level: CaregiverAlertStatus) : PillStatus
    data class Posture(val status: PostureStatus) : PillStatus
    data class Fatigue(val status: FatigueStatus) : PillStatus
    data class Risk(val status: RiskStatus) : PillStatus
}

private fun colorFor(status: PillStatus): Color = when (status) {
    is PillStatus.CaregiverAlert -> when (status.level) {
        CaregiverAlertStatus.Urgent -> Color(0xFFB91C1C)
        CaregiverAlertStatus.Check -> Color(0xFFB45309)
        CaregiverAlertStatus.Normal -> Color(0xFF0F766E)
    }
    is PillStatus.Posture -> when (status.status) {
        PostureStatus.Bad -> Color(0xFFB91C1C)
        PostureStatus.Warning -> Color(0xFFB45309)
        PostureStatus.Good -> Color(0xFF0F766E)
    }
    is PillStatus.Fatigue -> when (status.status) {
        FatigueStatus.Stop -> Color(0xFFB91C1C)
        FatigueStatus.Caution -> Color(0xFFB45309)
        FatigueStatus.Safe -> Color(0xFF0F766E)
    }
    is PillStatus.Risk -> when (status.status) {
        RiskStatus.High -> Color(0xFFB91C1C)
        RiskStatus.Medium -> Color(0xFFB45309)
        RiskStatus.Low -> Color(0xFF0F766E)
    }
}

@Composable
fun StatusPill(label: String, status: PillStatus, modifier: Modifier = Modifier) {
    val color = colorFor(status)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
