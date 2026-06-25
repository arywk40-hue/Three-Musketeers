package com.eldercareguardian.samsung

import android.content.Context
import com.eldercareguardian.data.SensorFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bridge between ElderCare Guardian and Samsung Health.
 *
 * The actual Samsung Health Data SDK v1.1.0 is distributed as a local AAR
 * (downloaded from developer.samsung.com/health/data). Until that AAR is
 * dropped into app/libs/, this bridge uses [RealSamsungHealthBridge] which
 * accesses the SDK via reflection so the module always compiles.
 *
 * If the AAR is absent, the bridge stays in [SamsungHealthState.NeedsSdkAar].
 * If present, it attempts to connect and transitions to [Ready] on success.
 */
interface SamsungHealthBridge {
    val state: StateFlow<SamsungHealthState>

    suspend fun connect()
    suspend fun requestPermissions()
    suspend fun writeVitals(frame: SensorFrame)
    fun isAvailable(): Boolean
}

enum class SamsungHealthState {
    /** App not registered in the Samsung Health Partner Program. The app ID
     *  must be approved at developer.samsung.com/health before the SDK works. */
    NeedsPartnerApproval,

    /** SDK AAR not loaded — drop health-data-api-*.aar into app/libs/ and rebuild. */
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

/**
 * Bridge provider — returns a [RealSamsungHealthBridge] when the AAR is
 * detected at runtime, otherwise returns [NoOpSamsungHealthBridge].
 */
object SamsungHealthBridgeProvider {
    fun create(context: Context): SamsungHealthBridge {
        return try {
            Class.forName("com.samsung.android.sdk.health.data.HealthDataService")
            RealSamsungHealthBridge(context)
        } catch (_: ClassNotFoundException) {
            NoOpSamsungHealthBridge()
        }
    }
}

class NoOpSamsungHealthBridge : SamsungHealthBridge {
    private val _state = MutableStateFlow(SamsungHealthState.NeedsSdkAar)
    override val state: StateFlow<SamsungHealthState> = _state

    @Volatile
    var lastErrorMessage: String? = null
        private set

    override suspend fun connect() {
        _state.value = SamsungHealthState.NeedsSdkAar
    }

    override suspend fun requestPermissions() = Unit

    override suspend fun writeVitals(frame: SensorFrame) = Unit

    override fun isAvailable(): Boolean = false
}
