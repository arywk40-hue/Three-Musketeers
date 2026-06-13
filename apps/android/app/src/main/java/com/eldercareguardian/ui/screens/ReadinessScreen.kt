package com.eldercareguardian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eldercareguardian.ble.BleConnectionState
import com.eldercareguardian.ble.DiscoveredBleDevice
import com.eldercareguardian.ble.SmartSuitBleTelemetry
import com.eldercareguardian.samsung.SamsungHealthState
import com.eldercareguardian.ui.components.ChecklistRow
import com.eldercareguardian.ui.components.SectionTitle
import com.eldercareguardian.ui.theme.AppColors
import kotlin.math.sqrt

@Composable
fun ReadinessScreen(
    missingPermissions: List<String>,
    bleConnectionState: BleConnectionState,
    discoveredDevices: List<DiscoveredBleDevice>,
    bleTelemetry: SmartSuitBleTelemetry,
    samsungState: SamsungHealthState,
    onRequestPermissions: () -> Unit,
    onStartBleScan: () -> Unit,
    onStopBle: () -> Unit,
    onConnectFirstDevice: () -> Unit,
    onStartSamsung: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ReadinessPanel(missingPermissions, bleConnectionState, samsungState)
        SamsungHealthPanel(samsungState, onStartSamsung)
        BleConnectionPanel(
            missingPermissions = missingPermissions,
            bleConnectionState = bleConnectionState,
            discoveredDevices = discoveredDevices,
            bleTelemetry = bleTelemetry,
            onRequestPermissions = onRequestPermissions,
            onStartBleScan = onStartBleScan,
            onStopBle = onStopBle,
            onConnectFirstDevice = onConnectFirstDevice,
        )
        DeploymentPanel()
    }
}

@Composable
private fun ReadinessPanel(
    missingPermissions: List<String>,
    bleConnectionState: BleConnectionState,
    samsungState: SamsungHealthState,
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Showcase readiness")
            ChecklistRow("Demo data stream", true)
            ChecklistRow("Runtime permissions", missingPermissions.isEmpty())
            ChecklistRow("BLE contract defined", true)
            ChecklistRow("ElderCare_v1 connected", bleConnectionState == BleConnectionState.Connected)
            ChecklistRow("Samsung SDK AAR installed", samsungState != SamsungHealthState.NeedsSdkAar)
            ChecklistRow("Partner approval received", samsungState == SamsungHealthState.Ready)
        }
    }
}

@Composable
private fun SamsungHealthPanel(
    samsungState: SamsungHealthState,
    onStartSamsung: () -> Unit,
) {
    val stateLabel = when (samsungState) {
        SamsungHealthState.Ready -> "Ready to write"
        SamsungHealthState.NeedsPartnerApproval -> "Partner approval required"
        SamsungHealthState.NeedsSdkAar -> "AAR missing"
        SamsungHealthState.NeedsPermission -> "Permission needed"
        SamsungHealthState.Disabled -> "Disabled"
        SamsungHealthState.Error -> "Error"
    }
    val stateColor = when (samsungState) {
        SamsungHealthState.Ready -> AppColors.success
        SamsungHealthState.NeedsPermission, SamsungHealthState.Disabled -> AppColors.warning
        SamsungHealthState.NeedsPartnerApproval -> AppColors.warning
        else -> AppColors.danger
    }
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("Samsung Health")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(stateColor.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        stateLabel,
                        color = stateColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(
                text = "Data SDK v1.1.0 is a local AAR \u2014 drop it into app/libs/ to activate. Writes happen on a 5 s cadence once permissions are granted in Samsung Health.",
                color = AppColors.textSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (samsungState == SamsungHealthState.NeedsPermission ||
                samsungState == SamsungHealthState.Disabled
            ) {
                OutlinedButton(
                    onClick = onStartSamsung,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Request permission")
                }
            } else if (samsungState == SamsungHealthState.NeedsPartnerApproval) {
                Text(
                    text = "Register at developer.samsung.com/health/data to obtain partner approval, then download the AAR.",
                    color = AppColors.warning,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (samsungState == SamsungHealthState.NeedsSdkAar) {
                Text(
                    text = "Add health-data-api-1.1.0.aar to apps/android/app/libs/ to enable this section.",
                    color = AppColors.warning,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun BleConnectionPanel(
    missingPermissions: List<String>,
    bleConnectionState: BleConnectionState,
    discoveredDevices: List<DiscoveredBleDevice>,
    bleTelemetry: SmartSuitBleTelemetry,
    onRequestPermissions: () -> Unit,
    onStartBleScan: () -> Unit,
    onStopBle: () -> Unit,
    onConnectFirstDevice: () -> Unit,
) {
    val permissionsReady = missingPermissions.isEmpty()

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("BLE connection")
                BleStatePill(bleConnectionState)
            }

            Text(
                text = "Scanner looks for ElderCare_v1. Sensor frames stay on simulator until firmware exposes the custom GATT stream.",
                color = AppColors.textTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
            if (missingPermissions.any { it == "android.permission.ACCESS_BACKGROUND_LOCATION" }) {
                Text(
                    text = "Background location is required for BLE scanning when the app is in the background. Grant it via Settings \u2192 Permissions if BLE disconnects when you leave the app.",
                    color = AppColors.warning,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = if (permissionsReady) onStartBleScan else onRequestPermissions,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(if (permissionsReady) "Scan" else "Grant")
                }
                OutlinedButton(
                    onClick = onStopBle,
                    shape = RoundedCornerShape(8.dp),
                    enabled = bleConnectionState != BleConnectionState.Idle,
                ) {
                    Text("Stop")
                }
                OutlinedButton(
                    onClick = onConnectFirstDevice,
                    shape = RoundedCornerShape(8.dp),
                    enabled = permissionsReady && discoveredDevices.isNotEmpty(),
                ) {
                    Text("Connect")
                }
            }

            if (discoveredDevices.isEmpty()) {
                Text(
                    text = "No ElderCare_v1 advertisements yet.",
                    color = AppColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                discoveredDevices.take(3).forEach { device ->
                    DeviceRow(device)
                }
            }

            HorizontalDivider(color = AppColors.borderLight)
            BleTelemetrySummary(bleTelemetry)
        }
    }
}

@Composable
private fun BleTelemetrySummary(telemetry: SmartSuitBleTelemetry) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("GATT telemetry")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TelemetryChip("HR", telemetry.heartRateBpm?.toString() ?: "--", Modifier.weight(1f))
            TelemetryChip("Battery", telemetry.batteryPercent?.let { "$it%" } ?: "--", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TelemetryChip("ECG", telemetry.ecgSamples.size.toString(), Modifier.weight(1f))
            TelemetryChip("SOS", if (telemetry.sosState) "ACTIVE" else "Off", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TelemetryChip("Humidity", telemetry.humidityPercent?.let { "%.1f".format(it) } ?: "--", Modifier.weight(1f))
            TelemetryChip("Resp", telemetry.respiratoryRate?.let { "%.1f".format(it) } ?: "--", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TelemetryChip(
                "IMU mag",
                if (telemetry.wristImu.size >= 3) {
                    val a = telemetry.wristImu
                    "%.2f m/s\u00b2".format(sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]))
                } else "--",
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TelemetryChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.surfaceTertiary)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = AppColors.textSecondary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = AppColors.textPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BleStatePill(state: BleConnectionState) {
    val color = when (state) {
        BleConnectionState.Connected -> AppColors.success
        BleConnectionState.Scanning, BleConnectionState.Connecting -> Color(0xFF2563EB)
        BleConnectionState.Error, BleConnectionState.Unsupported, BleConnectionState.PermissionMissing -> AppColors.danger
        else -> AppColors.textTertiary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(state.name, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DeviceRow(device: DiscoveredBleDevice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.surfaceSecondary)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(device.name, color = AppColors.textPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(device.address, color = AppColors.textTertiary, style = MaterialTheme.typography.bodySmall)
        }
        Text("${device.rssi} dBm", color = AppColors.textSecondary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DeploymentPanel() {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("Samsung deployment")
            Text(
                text = "Phase 1 is local dashboard and BLE. Phase 2 enables Samsung Health Data SDK on a real Samsung phone after AAR install and consent setup.",
                color = AppColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
