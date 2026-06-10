package com.eldercareguardian.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eldercareguardian.settings.isValidPhone
import com.eldercareguardian.settings.phoneDigitCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings screen — used for editing the caregiver's display name and
 * phone number. The phone field is validated: must contain at least 7
 * digits (per [com.eldercareguardian.settings.isValidPhone]).
 *
 * On save, a coroutine is launched to persist to DataStore. A "Saved."
 * indicator appears for 2 seconds before clearing.
 *
 * Caller is responsible for navigation. Pass [onBack] to receive the
 * close-event and route back to the dashboard.
 */
@Composable
fun SettingsScreen(
    initialName: String,
    initialPhone: String,
    onSave: suspend (name: String, phone: String) -> Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(TextFieldValue(initialName)) }
    var phone by remember { mutableStateOf(TextFieldValue(initialPhone)) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(saved) {
        if (saved) {
            delay(2_000)
            saved = false
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFFF8FAFC),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF0F766E),
                    )
                }
                Spacer(Modifier.size(4.dp))
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = Color(0xFF0F766E),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Caregiver contact",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "This is the person the app calls when there's an urgent alert. " +
                            "Use international format (+14155551234) so the dialer always recognises it.",
                        color = Color(0xFF475569),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            errorText = null
                        },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorText != null && name.text.isBlank(),
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it
                            errorText = null
                        },
                        label = { Text("Phone number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = errorText != null && !isValidPhone(phone.text),
                        supportingText = {
                            val digits = phoneDigitCount(phone.text)
                            if (digits in 1..6) {
                                Text(
                                    text = "Needs at least 7 digits (currently $digits).",
                                    color = Color(0xFFB45309),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                    )
                    if (errorText != null) {
                        Text(
                            text = errorText!!,
                            color = Color(0xFFB91C1C),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (saved) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF0F766E),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    text = "Saved",
                                    color = Color(0xFF0F766E),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        } else {
                            Spacer(Modifier.size(1.dp))
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val ok = onSave(name.text, phone.text)
                                    if (ok) {
                                        errorText = null
                                        saved = true
                                    } else {
                                        errorText = "Enter a name and a phone number with at least 7 digits."
                                    }
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                        ) {
                            Text("Save")
                        }
                    }
                }
            }

            Text(
                text = "Caregiver contact is stored locally in app-private DataStore. " +
                    "It is not transmitted off the device.",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen(
            initialName = "John Doe",
            initialPhone = "+1234567890",
            onSave = { _, _ -> true },
            onBack = {}
        )
    }
}
