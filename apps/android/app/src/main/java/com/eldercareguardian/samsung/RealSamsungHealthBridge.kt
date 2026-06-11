package com.eldercareguardian.samsung

import android.content.Context
import com.eldercareguardian.data.SensorFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Real Samsung Health Data SDK bridge, invoked via reflection so the module
 * compiles even when the proprietary AAR is absent from app/libs/.
 *
 * 1. At init time, checks whether the SDK classes are loadable.
 * 2. If yes, uses [HealthDataService] / [HealthDataStore] to write vitals.
 * 3. If no, transitions to [SamsungHealthState.NeedsSdkAar].
 *
 * The AAR (samsung-health-data-api-*.aar) is downloaded from
 * https://developer.samsung.com/health/data and placed in app/libs/.
 */
class RealSamsungHealthBridge(
    private val context: Context,
) : SamsungHealthBridge {

    private val _state = MutableStateFlow(checkSdkAvailability())
    override val state: StateFlow<SamsungHealthState> = _state

    @Volatile
    var lastErrorMessage: String? = null
        private set

    // Reflection handles
    private var healthDataServiceCls: Class<*>? = null
    private var healthDataStoreCls: Class<*>? = null
    private var healthDataPointCls: Class<*>? = null
    private var healthConstantsCls: Class<*>? = null
    private var healthPermissionCls: Class<*>? = null

    // SDK instances
    private var healthDataService: Any? = null
    private var healthDataStore: Any? = null

    private fun checkSdkAvailability(): SamsungHealthState {
        try {
            healthDataServiceCls = Class.forName("com.samsung.android.sdk.healthdata.HealthDataService")
            healthDataStoreCls = Class.forName("com.samsung.android.sdk.healthdata.HealthDataStore")
            healthDataPointCls = Class.forName("com.samsung.android.sdk.healthdata.HealthDataPoint")
            healthConstantsCls = Class.forName("com.samsung.android.sdk.healthdata.HealthConstants")
            healthPermissionCls = Class.forName("com.samsung.android.sdk.healthdata.HealthPermission")
            return SamsungHealthState.NeedsPartnerApproval
        } catch (_: ClassNotFoundException) {
            return SamsungHealthState.NeedsSdkAar
        }
    }

    override suspend fun connect() {
        val svcCls = healthDataServiceCls ?: run {
            _state.value = SamsungHealthState.NeedsSdkAar
            return
        }

        try {
            // new HealthDataService()
            healthDataService = svcCls.getDeclaredConstructor().newInstance()
            val connectMethod = svcCls.getMethod("connect", Context::class.java)
            val listenerCls = Class.forName("com.samsung.android.sdk.healthdata.HealthDataService\$ConnectionListener")
            // We call connect(context) and handle the result via a callback proxy.
            // For simplicity, use the blocking variant if available or the listener-based one.
            @Suppress("UNCHECKED_CAST")
            val listener = java.lang.reflect.Proxy.newProxyInstance(
                listenerCls.classLoader,
                arrayOf(listenerCls),
            ) { _, method, args ->
                when (method.name) {
                    "onConnected" -> {
                        // HealthDataStore store = healthDataService.getStore()
                        val getStore = svcCls.getMethod("getStore")
                        healthDataStore = getStore.invoke(healthDataService)
                        _state.value = SamsungHealthState.Ready
                    }
                    "onConnectionFailed" -> {
                        lastErrorMessage = "Samsung Health connection failed"
                        _state.value = SamsungHealthState.Error
                    }
                    "onDisconnected" -> {
                        _state.value = SamsungHealthState.NeedsPartnerApproval
                    }
                }
                null
            }
            connectMethod.invoke(healthDataService, context)
        } catch (e: Exception) {
            lastErrorMessage = e.message
            _state.value = SamsungHealthState.Error
        }
    }

    override suspend fun requestPermissions() {
        val store = healthDataStore ?: run {
            _state.value = SamsungHealthState.NeedsSdkAar
            return
        }
        try {
            // Use reflection to call store.requestPermissions(permissionSet, listener)
            val requestPermsMethod = healthDataStoreCls!!.getMethod(
                "requestPermissions",
                Set::class.java,
                Class.forName("com.samsung.android.sdk.healthdata.HealthResultHolder\$ResultListener"),
            )

            val heartRateField = Class.forName("com.samsung.android.sdk.healthdata.HealthConstants\$HeartRate")
                .getField("HEART_RATE")
            val spo2Field = Class.forName("com.samsung.android.sdk.healthdata.HealthConstants\$OxygenSaturation")
                .getField("OXYGEN_SATURATION")
            val tempField = Class.forName("com.samsung.android.sdk.healthdata.HealthConstants\$BodyTemperature")
                .getField("BODY_TEMPERATURE")
            val bpField = Class.forName("com.samsung.android.sdk.healthdata.HealthConstants\$BloodPressure")
                .getField("BLOOD_PRESSURE")

            val readPerm = healthPermissionCls!!.getMethod("read", String::class.java)
            val writePerm = healthPermissionCls!!.getMethod("write", String::class.java)

            val permissions = setOf(
                readPerm.invoke(null, heartRateField.get(null)),
                writePerm.invoke(null, heartRateField.get(null)),
                readPerm.invoke(null, spo2Field.get(null)),
                writePerm.invoke(null, spo2Field.get(null)),
                readPerm.invoke(null, tempField.get(null)),
                writePerm.invoke(null, tempField.get(null)),
                readPerm.invoke(null, bpField.get(null)),
                writePerm.invoke(null, bpField.get(null)),
            )

            val listenerCls = Class.forName("com.samsung.android.sdk.healthdata.HealthResultHolder\$ResultListener")
            val listener = java.lang.reflect.Proxy.newProxyInstance(
                listenerCls.classLoader,
                arrayOf(listenerCls),
            ) { _, method, args2 ->
                if (method.name == "onResult") {
                    _state.value = SamsungHealthState.Ready
                }
                null
            }
            requestPermsMethod.invoke(store, permissions, listener)
        } catch (e: Exception) {
            lastErrorMessage = e.message
            _state.value = SamsungHealthState.Error
        }
    }

    override suspend fun writeVitals(frame: SensorFrame) {
        val store = healthDataStore ?: return
        try {
            // Build HealthDataPoints and call store.insert()
            val builderMethod = healthDataPointCls!!.getMethod(
                "builder",
                String::class.java,
                Class.forName("com.samsung.android.sdk.healthdata.HealthDataPoint\$Type"),
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
            )
            val typeUniqueField = Class.forName("com.samsung.android.sdk.healthdata.HealthDataPoint\$Type")
                .getField("TYPE_UNIQUE")
            val buildMethod = healthDataPointCls!!.getMethod("build")
            val setValueMethod = healthDataPointCls!!.getMethod("setValue", String::class.java, Any::class.java)

            val heartRateField = Class.forName("com.samsung.android.sdk.healthdata.HealthConstants\$HeartRate")
                .getField("HEART_RATE")
            val spo2Field = Class.forName("com.samsung.android.sdk.healthdata.HealthConstants\$OxygenSaturation")
                .getField("OXYGEN_SATURATION")

            val now = System.currentTimeMillis()
            val startTime = now - 5_000L
            val endTime = now

            fun buildPoint(dataType: String, value: Any): Any? {
                val builder = builderMethod.invoke(null, dataType, typeUniqueField.get(null), startTime, endTime)
                setValueMethod.invoke(builder, dataType, value)
                return buildMethod.invoke(builder)
            }

            val points = mutableListOf<Any>()
            buildPoint(heartRateField.get(null) as String, frame.heartRateBpm)?.let { points.add(it) }
            buildPoint(spo2Field.get(null) as String, frame.spo2Percent.toInt())?.let { points.add(it) }

            val insertMethod = healthDataStoreCls!!.getMethod("insert", List::class.java)
            @Suppress("UNCHECKED_CAST")
            insertMethod.invoke(store, points as List<Any>)
        } catch (_: Exception) {
            // Silently swallow — Samsung Health writes are best-effort
        }
    }

    override fun isAvailable(): Boolean = _state.value == SamsungHealthState.Ready
}
