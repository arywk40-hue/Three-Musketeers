package com.eldercareguardian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eldercareguardian.consent.ConsentPreferences
import com.eldercareguardian.consent.DpdpaConsentScreen
import com.eldercareguardian.ui.SmartSuitApp
import com.eldercareguardian.ui.SmartSuitViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private val viewModel: SmartSuitViewModel by viewModels()
    private lateinit var consentPreferences: ConsentPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        consentPreferences = ConsentPreferences.getInstance(this)

        setContent {
            val consentGranted by consentPreferences.isConsentGranted
                .collectAsState(initial = false)

            // initial = false eliminates blank screen flash on cold start
            if (consentGranted) {
                SmartSuitApp(smartSuitViewModel = viewModel)
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

    override fun onResume() {
        super.onResume()
        // Only start the foreground monitoring service after DPDPA consent
        // is granted. Without this guard the service notification appears
        // on the consent screen and can crash on some OEMs.
        val consented = runBlocking { consentPreferences.isConsentGranted.first() }
        if (consented) {
            viewModel.onUiVisible()
        }
    }
}
