package com.eldercareguardian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eldercareguardian.consent.ConsentPreferences
import com.eldercareguardian.consent.DpdpaConsentScreen
import com.eldercareguardian.ui.SmartSuitApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val consentPreferences = ConsentPreferences.getInstance(this)

        setContent {
            val consentGranted by consentPreferences.isConsentGranted
                .collectAsState(initial = false)

            // initial = false eliminates blank screen flash on cold start
            if (consentGranted) {
                SmartSuitApp()
            } else {
                DpdpaConsentScreen(
                    consentPreferences = consentPreferences,
                    onConsentGranted = {
                        // Recomposition via the Flow handles the transition
                    },
                )
            }
        }
    }
}
