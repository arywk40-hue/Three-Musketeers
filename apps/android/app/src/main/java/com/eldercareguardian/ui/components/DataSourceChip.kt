package com.eldercareguardian.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eldercareguardian.ble.BleConnectionState

@Composable
fun DataSourceChip(bleConnectionState: BleConnectionState) {
    val (color, text) = when (bleConnectionState) {
        BleConnectionState.Connected, BleConnectionState.Bonded, BleConnectionState.Bonding -> {
            Color(0xFF0F766E) to "Live \u2014 ElderCare_v1"
        }
        else -> {
            Color(0xFF64748B) to "Simulator"
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}