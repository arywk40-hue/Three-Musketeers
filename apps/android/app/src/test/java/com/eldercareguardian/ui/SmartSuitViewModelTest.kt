package com.eldercareguardian.ui

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SmartSuitViewModelTest {

    @Test
    fun `triggerSosDemo does not change acknowledgedUrgent`() {
        val viewModel = SmartSuitViewModel(ApplicationProvider.getApplicationContext())
        assertEquals(false, viewModel.acknowledgedUrgent.value)
        viewModel.triggerSosDemo()
        assertEquals(false, viewModel.acknowledgedUrgent.value)
    }

    @Test
    fun `clearSosDemo does not affect acknowledgedUrgent`() {
        val viewModel = SmartSuitViewModel(ApplicationProvider.getApplicationContext())
        assertEquals(false, viewModel.acknowledgedUrgent.value)
        viewModel.clearSosDemo()
        assertEquals(false, viewModel.acknowledgedUrgent.value)
    }
}
