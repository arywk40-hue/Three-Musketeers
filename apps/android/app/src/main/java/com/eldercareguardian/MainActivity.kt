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

class MainActivity : ComponentActivity() {
    private val viewModel: SmartSuitViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val consentPreferences = ConsentPreferences.getInstance(this)

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
        // Start foreground service when app is in foreground
        // _serviceStarted flag in ViewModel prevents multiple starts
        viewModel.onUiVisible()
    }
}
