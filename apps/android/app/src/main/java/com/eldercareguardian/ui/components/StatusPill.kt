package com.eldercareguardian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.eldercareguardian.data.CaregiverAlertStatus
import com.eldercareguardian.data.FatigueStatus
import com.eldercareguardian.data.PostureStatus
import com.eldercareguardian.data.RiskStatus
import com.eldercareguardian.ui.theme.AppColors

sealed interface PillStatus {
    data class CaregiverAlert(val level: CaregiverAlertStatus) : PillStatus
    data class Posture(val status: PostureStatus) : PillStatus
    data class Fatigue(val status: FatigueStatus) : PillStatus
    data class Risk(val status: RiskStatus) : PillStatus
}

private fun colorFor(status: PillStatus): Color = when (status) {
    is PillStatus.CaregiverAlert -> when (status.level) {
        CaregiverAlertStatus.Emergency -> AppColors.danger
        CaregiverAlertStatus.Warning -> AppColors.warning
        CaregiverAlertStatus.Check -> AppColors.warning
        CaregiverAlertStatus.Normal -> AppColors.primary
    }
    is PillStatus.Posture -> when (status.status) {
        PostureStatus.Bad -> AppColors.danger
        PostureStatus.Warning -> AppColors.warning
        PostureStatus.Good -> AppColors.success
    }
    is PillStatus.Fatigue -> when (status.status) {
        FatigueStatus.Stop -> AppColors.danger
        FatigueStatus.Caution -> AppColors.warning
        FatigueStatus.Safe -> AppColors.primary
    }
    is PillStatus.Risk -> when (status.status) {
        RiskStatus.High -> AppColors.danger
        RiskStatus.Medium -> AppColors.warning
        RiskStatus.Low -> AppColors.primary
    }
}

@Composable
fun StatusPill(label: String, status: PillStatus, modifier: Modifier = Modifier) {
    val color = colorFor(status)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
