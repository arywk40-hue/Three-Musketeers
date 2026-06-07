package com.smartsuit.samsung

import com.smartsuit.data.SensorFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridge between ElderCare Guardian and Samsung Health.
 *
 * The actual Samsung Health Data SDK v1.1.0 is distributed as a local AAR
 * (downloaded from developer.samsung.com/health/data). Until that AAR is
 * dropped into app/libs/, this interface is implemented by a NoOp that keeps
 * the bridge calls in the rest of the app non-blocking.
 *
 * When the AAR is present, swap [NoOpSamsungHealthBridge] for a real
 * implementation that calls the SDK — see the marked HOOK points below.
 */
interface SamsungHealthBridge {
    val state: StateFlow<SamsungHealthState>

    suspend fun connect()
    suspend fun requestPermissions()
    suspend fun writeVitals(frame: SensorFrame)
    fun isAvailable(): Boolean
}

enum class SamsungHealthState {
    /** SDK not loaded — drop health-data-api-*.aar into app/libs/ and rebuild. */
    NeedsSdkAar,

    /** Waiting on user to grant data permissions inside Samsung Health. */
    NeedsPermission,

    /** Bridge connected and ready to write. */
    Ready,

    /** User has not opted in, or writeVitals was disabled at runtime. */
    Disabled,

    /** SDK call failed — last error captured by [lastErrorMessage]. */
    Error,
}

class NoOpSamsungHealthBridge : SamsungHealthBridge {
    private val _state = MutableStateFlow(SamsungHealthState.NeedsSdkAar)
    override val state: StateFlow<SamsungHealthState> = _state

    @Volatile
    var lastErrorMessage: String? = null
        private set

    override suspend fun connect() = Unit

    override suspend fun requestPermissions() = Unit

    override suspend fun writeVitals(frame: SensorFrame) = Unit

    override fun isAvailable(): Boolean = false
}
