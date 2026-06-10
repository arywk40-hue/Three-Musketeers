package com.eldercareguardian.consent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ── Color palette ──
private val BgDark = Color(0xFF0F172A)
private val CardBg = Color(0xFF1E293B)
private val AccentTeal = Color(0xFF14B8A6)
private val AccentBlue = Color(0xFF3B82F6)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)
private val DividerColor = Color(0xFF334155)

/**
 * Full-screen DPDPA consent screen shown on first launch.
 *
 * The user must read the data collection terms and check the
 * consent checkbox before the "Accept & Continue" button is enabled.
 *
 * @param consentPreferences  DataStore wrapper to persist consent
 * @param onConsentGranted    callback invoked after consent is recorded
 */
@Composable
fun DpdpaConsentScreen(
    consentPreferences: ConsentPreferences,
    onConsentGranted: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isChecked by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgDark, Color(0xFF0C1322))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Header ──
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Data Privacy Consent",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Under the Digital Personal Data Protection Act, 2023 (DPDPA)",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "ElderCare Guardian collects sensitive health data to keep " +
                        "your loved ones safe. Please review what we collect and how " +
                        "we protect it before proceeding.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(24.dp))

            // ── Section cards ──
            ConsentSection(
                icon = Icons.Filled.Favorite,
                iconTint = Color(0xFFF43F5E),
                title = "What We Collect",
                items = listOf(
                    "Heart rate and SpO2 (blood oxygen) from the wearable sensor",
                    "6-axis motion data (accelerometer + gyroscope) for fall detection",
                    "Skin temperature readings (when sensor is available)",
                    "SOS button activation events",
                    "Bluetooth Low Energy connection metadata",
                ),
            )

            Spacer(Modifier.height(12.dp))

            ConsentSection(
                icon = Icons.Filled.Storage,
                iconTint = AccentBlue,
                title = "How It's Stored",
                items = listOf(
                    "All health data is encrypted locally using SQLCipher (AES-256)",
                    "Encryption key is stored in Android Keystore (hardware-backed)",
                    "Data is automatically deleted after 7 days",
                    "No data is uploaded to any cloud server or third party",
                    "Data never leaves your device without your explicit action",
                ),
            )

            Spacer(Modifier.height(12.dp))

            ConsentSection(
                icon = Icons.Filled.Person,
                iconTint = Color(0xFFA78BFA),
                title = "Who Can Access",
                items = listOf(
                    "Only the registered caregiver using this specific device",
                    "The caregiver sees vitals, alert status, and health trends",
                    "No remote access — all data stays on-device",
                    "Samsung Health data is shared only if you enable the integration",
                ),
            )

            Spacer(Modifier.height(12.dp))

            ConsentSection(
                icon = Icons.Filled.Lock,
                iconTint = AccentTeal,
                title = "Your Rights Under DPDPA",
                items = listOf(
                    "Withdraw consent at any time from Settings",
                    "Request complete deletion of all stored health data",
                    "Export your data for sharing with a medical professional",
                    "Consent withdrawal stops all health data collection immediately",
                ),
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(Modifier.height(16.dp))

            // ── Consent checkbox ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { isChecked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentTeal,
                        uncheckedColor = TextSecondary,
                        checkmarkColor = BgDark,
                    ),
                )
                Text(
                    text = "I have read and agree to the collection and processing " +
                            "of my health data as described above, in accordance with " +
                            "the Digital Personal Data Protection Act, 2023.",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Accept button ──
            Button(
                onClick = {
                    scope.launch {
                        consentPreferences.grantConsent()
                        onConsentGranted()
                    }
                },
                enabled = isChecked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentTeal,
                    contentColor = BgDark,
                    disabledContainerColor = Color(0xFF1E3A3A),
                    disabledContentColor = TextSecondary,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = if (isChecked) "Accept & Continue" else "Please review and accept above",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "You can withdraw consent at any time from the app Settings.",
                color = TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConsentSection(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    items: List<String>,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = title,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            items.forEach { item ->
                Row(modifier = Modifier.padding(bottom = 6.dp)) {
                    Text(
                        text = "•",
                        color = AccentTeal,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 8.dp, top = 1.dp),
                    )
                    Text(
                        text = item,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}
