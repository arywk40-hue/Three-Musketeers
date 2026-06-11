package com.eldercareguardian.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BatteryRow(percent: Int?) {
    val display = percent?.let { "$it%" } ?: "\u2014"
    val progress = ((percent ?: 0).coerceIn(0, 100)) / 100f
    val isLow = percent != null && percent < 15
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Device battery", color = Color(0xFF475569), style = MaterialTheme.typography.bodyMedium)
            Text(display, color = Color(0xFF0F172A), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (isLow) Color(0xFFB91C1C) else Color(0xFF0F766E),
            trackColor = Color(0xFFE2E8F0),
        )
        if (isLow) {
            Text(
                "Battery low \u2014 please charge the device",
                color = Color(0xFFB91C1C),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
