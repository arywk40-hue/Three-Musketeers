package com.eldercareguardian.samsung

import android.content.Context
import com.eldercareguardian.data.SensorFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Real Samsung Health Data SDK v1.1.0 bridge, invoked via reflection so the
 * module compiles even when the proprietary AAR is absent from app/libs/.
 *
 * API surface (from architecture.md):
 *   HealthDataService.getStore(context) → HealthDataStore
 *   Permission.of(DataTypes.X, AccessType.Y) → permission
 *   HealthDataPoint.builder().setStartTime(t).setEndTime(t).addFieldData(...).build()
 *   DataTypes.HEART_RATE.insertDataRequestBuilder.addData(point).build()
 *   healthDataStore.insertData(request)
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

    // Reflection handles — SDK v1.1.0 package: com.samsung.android.sdk.health.data
    private var healthDataServiceCls: Class<*>? = null
    private var healthDataStoreCls: Class<*>? = null
    private var dataTypesCls: Class<*>? = null
    private var permissionCls: Class<*>? = null
    private var accessTypeCls: Class<*>? = null
    private var healthDataPointCls: Class<*>? = null

    // SDK instances
    private var healthDataStore: Any? = null

    private val SDK_PACKAGE = "com.samsung.android.sdk.health.data"

    private fun checkSdkAvailability(): SamsungHealthState {
        return try {
            healthDataServiceCls = Class.forName("$SDK_PACKAGE.HealthDataService")
            healthDataStoreCls = Class.forName("$SDK_PACKAGE.HealthDataStore")
            dataTypesCls = Class.forName("$SDK_PACKAGE.DataTypes")
            permissionCls = Class.forName("$SDK_PACKAGE.Permission")
            accessTypeCls = Class.forName("$SDK_PACKAGE.AccessType")
            healthDataPointCls = Class.forName("$SDK_PACKAGE.HealthDataPoint")
            SamsungHealthState.NeedsPartnerApproval
        } catch (_: ClassNotFoundException) {
            SamsungHealthState.NeedsSdkAar
        }
    }

    override suspend fun connect() {
        val svcCls = healthDataServiceCls ?: run {
            _state.value = SamsungHealthState.NeedsSdkAar
            return
        }
        try {
            // HealthDataStore store = HealthDataService.getStore(context)
            val getStoreMethod = svcCls.getMethod("getStore", Context::class.java)
            healthDataStore = getStoreMethod.invoke(null, context)
            _state.value = SamsungHealthState.Ready
        } catch (e: Exception) {
            lastErrorMessage = e.message
            _state.value = SamsungHealthState.Error
        }
    }

    override suspend fun requestPermissions() {
        val store = healthDataStore ?: return
        val permCls = permissionCls ?: return
        val accTypeCls = accessTypeCls ?: return
        try {
            // Permission.of(DataTypes.HEART_RATE, AccessType.WRITE)
            val writeAccess = accTypeCls.getDeclaredField("WRITE").get(null)
            val heartRateDataType = dataTypesCls!!.getDeclaredField("HEART_RATE").get(null)
            val spo2DataType = dataTypesCls!!.getDeclaredField("BLOOD_OXYGEN").get(null)
            val bpDataType = dataTypesCls!!.getDeclaredField("BLOOD_PRESSURE").get(null)
            val tempDataType = dataTypesCls!!.getDeclaredField("BODY_TEMPERATURE").get(null)

            // Permission.of() takes (DataType, AccessType) — DataType is an object, not a String
            val ofMethod = permCls.getMethod("of", heartRateDataType::class.java, accTypeCls)

            val permissions = setOf(
                ofMethod.invoke(null, heartRateDataType, writeAccess),
                ofMethod.invoke(null, spo2DataType, writeAccess),
                ofMethod.invoke(null, bpDataType, writeAccess),
                ofMethod.invoke(null, tempDataType, writeAccess),
            )

            // store.requestPermissions(permissions)
            val requestPermsMethod = healthDataStoreCls!!.getMethod(
                "requestPermissions",
                Set::class.java
            )
            requestPermsMethod.invoke(store, permissions)
            _state.value = SamsungHealthState.NeedsPermission
        } catch (e: Exception) {
            lastErrorMessage = e.message
            _state.value = SamsungHealthState.Error
        }
    }

    override suspend fun writeVitals(frame: SensorFrame) {
        val store = healthDataStore ?: return
        try {
            val heartRateDataType = dataTypesCls!!.getDeclaredField("HEART_RATE").get(null)
            val spo2DataType = dataTypesCls!!.getDeclaredField("BLOOD_OXYGEN").get(null)
            val heartRateField = heartRateDataType::class.java
                .getDeclaredField("HEART_RATE").also { it.isAccessible = true }
            val spo2Field = spo2DataType::class.java
                .getDeclaredField("BLOOD_OXYGEN").also { it.isAccessible = true }
            val hrFieldKey = heartRateField.get(heartRateDataType) as String
            val spo2FieldKey = spo2Field.get(spo2DataType) as String

            val now = System.currentTimeMillis()
            val startTime = now - 5_000L

            // Build points via HealthDataPoint.builder()
            val builderMethod = healthDataPointCls!!.getMethod("builder")
            val setStartTimeMethod = healthDataPointCls!!.getMethod("setStartTime", Long::class.javaPrimitiveType)
            val setEndTimeMethod = healthDataPointCls!!.getMethod("setEndTime", Long::class.javaPrimitiveType)
            val addFieldDataMethod = healthDataPointCls!!.getMethod("addFieldData", String::class.java, Any::class.java)
            val buildMethod = healthDataPointCls!!.getMethod("build")

            fun buildPoint(fieldKey: String, value: Any): Any {
                val builder = builderMethod.invoke(null)
                setStartTimeMethod.invoke(builder, startTime)
                setEndTimeMethod.invoke(builder, now)
                addFieldDataMethod.invoke(builder, fieldKey, value)
                return buildMethod.invoke(builder)
            }

            val hrPoint = buildPoint(hrFieldKey, frame.heartRateBpm)
            val spo2Point = buildPoint(spo2FieldKey, frame.spo2Percent.toInt())

            // Create insert requests via DataTypes.HEART_RATE.insertDataRequestBuilder
            val hrBuilder = heartRateDataType::class.java
                .getMethod("insertDataRequestBuilder")
                .invoke(heartRateDataType)
            val spo2Builder = spo2DataType::class.java
                .getMethod("insertDataRequestBuilder")
                .invoke(spo2DataType)

            val addDataMethod = hrBuilder::class.java
                .getMethod("addData", healthDataPointCls)
            val buildRequestMethod = hrBuilder::class.java
                .getMethod("build")

            addDataMethod.invoke(hrBuilder, hrPoint)
            addDataMethod.invoke(spo2Builder, spo2Point)
            val hrRequest = buildRequestMethod.invoke(hrBuilder)
            val spo2Request = buildRequestMethod.invoke(spo2Builder)

            // store.insertData(request)
            val insertDataMethod = healthDataStoreCls!!.getMethod("insertData", Any::class.java)
            insertDataMethod.invoke(store, hrRequest)
            insertDataMethod.invoke(store, spo2Request)
        } catch (_: Exception) {
            // Silently swallow — Samsung Health writes are best-effort
        }
    }

    override fun isAvailable(): Boolean = _state.value == SamsungHealthState.Ready
}
