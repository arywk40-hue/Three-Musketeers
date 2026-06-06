# Smart Workout Suit — Dependencies Reference
**Last verified against Samsung docs:** June 2026  
**Purpose:** Full dependency list for the Android app — paste this into LLM context when building

---

## ⚠️ CRITICAL — Read Before Building

### Samsung Health SDK for Android is DEPRECATED
The old **Samsung Health SDK for Android** (`com.samsung.android.sdk.healthdata`) was **officially deprecated on July 31, 2025**. Do NOT use it. Any LLM or tutorial that references `HealthDataStore` from `com.samsung.android.sdk.healthdata` is giving you dead code.

**Use instead:** Samsung Health Data SDK v1.1.0 (see below). This is the current supported SDK.

### The Two SDKs Are Completely Different Things
| SDK | Purpose | Where it runs | Download |
|-----|---------|---------------|----------|
| **Samsung Health Data SDK v1.1.0** | Read/write health data to Samsung Health on the phone | Android phone | Samsung Developer Portal |
| **Samsung Health Sensor SDK v1.4.1** | Raw sensor access from Galaxy Watch BioActive Sensor | Galaxy Watch (Wear OS) | Samsung Developer Portal |
| ~~Samsung Health SDK for Android~~ | ~~Old phone-side SDK~~ | ~~Phone~~ | ~~Deprecated July 2025~~ |

### Accessory SDK is a Spec, Not a Library
The Samsung Health Accessory SDK is **a GATT compatibility specification document**, not a Gradle dependency. You download the spec PDF, follow the BLE service/characteristic structure it defines, and your suit firmware implements it. Your Android app handles custom GATT services directly through the standard Android BLE APIs.

---

## System Requirements

| Requirement | Value |
|-------------|-------|
| Android minimum SDK | API 29 (Android 10) |
| Android target SDK | API 35 (Android 15) |
| Samsung Health minimum version | 6.30.2 |
| Java version (Data SDK) | Java 17 or later |
| Java version (Sensor SDK) | Java 1.8 |
| Kotlin | 2.0.x |
| Android Studio | Ladybug (2024.2.1) or later |
| Gradle | 8.x |
| No emulator support | Both Samsung SDKs require real hardware |

---

## How Samsung SDKs Are Installed

**Not on Maven.** Both Samsung Health SDKs are downloaded as `.aar` files from the Samsung Developer Portal — they are not available via Gradle from any public repo.

Download locations:
- Data SDK v1.1.0 (1.78 MB): https://developer.samsung.com/health/data/process.html
- Sensor SDK v1.4.1 (70.8 KB): https://developer.samsung.com/health/sensor/process.html

After download, unzip and copy the `.aar` file into your project's `app/libs/` folder. The Gradle dependency then points to that local file.

---

## Project Structure

```
smart-suit-app/
├── app/
│   ├── libs/
│   │   ├── health-data-api-1.0.0.aar       ← Samsung Health Data SDK
│   │   └── samsung-health-sensor-api.aar   ← Samsung Health Sensor SDK (if using Galaxy Watch)
│   ├── src/main/
│   │   ├── java/com/smartsuit/
│   │   │   ├── ble/
│   │   │   ├── samsung/
│   │   │   ├── ml/
│   │   │   └── ui/
│   │   ├── assets/
│   │   │   ├── ecg_anomaly.tflite
│   │   │   ├── rep_counter.tflite
│   │   │   ├── form_scorer.tflite
│   │   │   ├── dehydration.tflite
│   │   │   └── overexertion.tflite
│   │   └── res/
│   └── build.gradle.kts
├── settings.gradle.kts
└── build.gradle.kts (project level)
```

---

## settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // For MPAndroidChart (ECG rendering), if you choose to use it:
        maven { url = uri("https://jitpack.io") }
    }
}
```

---

## build.gradle.kts (project level)

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
}
```

---

## app/build.gradle.kts (full)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")          // ← REQUIRED by Samsung Health Data SDK
    id("com.google.devtools.ksp")   // ← For Room database
}

android {
    namespace = "com.smartsuit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smartsuit"
        minSdk = 29                 // ← Samsung Health Data SDK minimum
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17   // ← Data SDK requires Java 17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // ─────────────────────────────────────────────────────────
    // SAMSUNG SDKs — local AAR files (downloaded from Samsung Developer Portal)
    // ─────────────────────────────────────────────────────────

    // Samsung Health Data SDK v1.1.0
    // Download: https://developer.samsung.com/health/data/process.html
    // File: health-data-api-1.0.0.aar → place in app/libs/
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("health-data-api-1.0.0.aar"))))

    // Required by Samsung Health Data SDK
    implementation("com.google.code.gson:gson:2.9.0")

    // Samsung Health Sensor SDK v1.4.1 (only if targeting Galaxy Watch companion)
    // Download: https://developer.samsung.com/health/sensor/process.html
    // File: samsung-health-sensor-api.aar → place in app/libs/
    // Uncomment if needed:
    // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("samsung-health-sensor-api.aar"))))

    // ─────────────────────────────────────────────────────────
    // ANDROID CORE
    // ─────────────────────────────────────────────────────────

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-compose:1.9.1")

    // ─────────────────────────────────────────────────────────
    // JETPACK COMPOSE
    // ─────────────────────────────────────────────────────────

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ─────────────────────────────────────────────────────────
    // LIFECYCLE + VIEWMODEL
    // ─────────────────────────────────────────────────────────

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // ─────────────────────────────────────────────────────────
    // COROUTINES
    // ─────────────────────────────────────────────────────────

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // ─────────────────────────────────────────────────────────
    // BLE (Bluetooth Low Energy)
    // Android's built-in BluetoothManager is used directly — no extra dependency.
    // However, Nordic's BLE library massively simplifies GATT client code:
    // ─────────────────────────────────────────────────────────

    implementation("no.nordicsemi.android:ble:2.8.1")
    // Adds: BleManager base class, automatic bonding, MTU negotiation,
    //       request queuing, connection state management — all the boilerplate gone.

    // ─────────────────────────────────────────────────────────
    // TENSORFLOW LITE — On-device ML inference
    // ─────────────────────────────────────────────────────────

    implementation("org.tensorflow:tensorflow-lite:2.15.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")
    // GPU delegate (optional — speeds up CNN models significantly):
    implementation("org.tensorflow:tensorflow-lite-gpu:2.15.0")
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")

    // ─────────────────────────────────────────────────────────
    // ROOM — Local database for sensor history
    // ─────────────────────────────────────────────────────────

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ─────────────────────────────────────────────────────────
    // CHARTING — ECG waveform + vitals graphs
    // ─────────────────────────────────────────────────────────

    // Vico — Compose-native charting (for line charts, HR graphs)
    implementation("com.patrykandpatrick.vico:compose-m3:1.14.0")
    implementation("com.patrykandpatrick.vico:core:1.14.0")

    // MPAndroidChart — if you need the scrolling ECG waveform view (View-based)
    // Requires jitpack repo in settings.gradle
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // ─────────────────────────────────────────────────────────
    // PERMISSIONS
    // ─────────────────────────────────────────────────────────

    // Accompanist — Compose permission handling (BLE, body sensors)
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // ─────────────────────────────────────────────────────────
    // TESTING
    // ─────────────────────────────────────────────────────────

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

---

## AndroidManifest.xml — Required Permissions

```xml
<manifest>

    <!-- BLE scanning and connecting -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
    <!-- Legacy BLE for Android < 12 -->
    <uses-permission android:name="android.permission.BLUETOOTH"
        android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
        android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
        android:maxSdkVersion="30" />

    <!-- Body sensors (heart rate, etc.) — required by Samsung Health Data SDK -->
    <uses-permission android:name="android.permission.BODY_SENSORS" />
    <uses-permission android:name="android.permission.BODY_SENSORS_BACKGROUND" />

    <!-- Activity recognition (for step detection) -->
    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />

    <!-- Health data (Samsung Health Data SDK internal) -->
    <uses-permission android:name="com.samsung.android.providers.context.permission.WRITE_USE_APP_FEATURE_SURVEY" />

    <!-- TFLite GPU delegate needs this -->
    <uses-permission android:name="android.permission.INTERNET" />

    <uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />

</manifest>
```

---

## Samsung Health Data SDK — Key API Surface

This is what the LLM needs to know when writing Samsung Health integration code.

### Connecting to Samsung Health

```kotlin
// Import from the local AAR — not from any Maven package
import com.samsung.android.sdk.healthdata.HealthDataStore
import com.samsung.android.sdk.healthdata.HealthConnectionErrorResult
import com.samsung.android.sdk.healthdata.HealthConstants
import com.samsung.android.sdk.healthdata.HealthDataResolver
import com.samsung.android.sdk.healthdata.HealthPermissionManager
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionKey
import com.samsung.android.sdk.healthdata.HealthPermissionManager.PermissionType

val store = HealthDataStore(context, object : HealthDataStore.ConnectionListener {
    override fun onConnected() { /* store is ready */ }
    override fun onConnectionFailed(error: HealthConnectionErrorResult) { }
    override fun onDisconnected() { }
})
store.connectService()

// Always call in onDestroy:
store.disconnectService()
```

### Requesting Permissions

```kotlin
val pmsManager = HealthPermissionManager(store)
val permissions = setOf(
    PermissionKey(HealthConstants.HeartRate.HEALTH_DATA_TYPE, PermissionType.READ),
    PermissionKey(HealthConstants.HeartRate.HEALTH_DATA_TYPE, PermissionType.WRITE),
    PermissionKey(HealthConstants.SpO2.HEALTH_DATA_TYPE, PermissionType.WRITE),
    PermissionKey(HealthConstants.BloodPressure.HEALTH_DATA_TYPE, PermissionType.WRITE),
    PermissionKey(HealthConstants.BodyTemperature.HEALTH_DATA_TYPE, PermissionType.WRITE),
    PermissionKey(HealthConstants.Exercise.HEALTH_DATA_TYPE, PermissionType.WRITE),
)
pmsManager.requestPermissions(permissions, activity)
```

### Writing Health Data (suit sensor → Samsung Health)

```kotlin
val resolver = HealthDataResolver(store, null)

// Write heart rate
val hrData = HealthData().apply {
    putString(HealthConstants.HeartRate.DEVICE_UUID, "SmartSuit_v1")
    putInt(HealthConstants.HeartRate.HEART_RATE, bpm)
    putLong(HealthConstants.HeartRate.START_TIME, System.currentTimeMillis())
    putLong(HealthConstants.HeartRate.TIME_OFFSET, TimeZone.getDefault().rawOffset.toLong())
}
val request = resolver.requestForInsert(HealthConstants.HeartRate.HEALTH_DATA_TYPE)
request.request.put(hrData)
request.flush()
```

### Health Data Types available for WRITE

```kotlin
HealthConstants.BloodGlucose.HEALTH_DATA_TYPE
HealthConstants.BloodOxygen.HEALTH_DATA_TYPE     // SpO2
HealthConstants.BloodPressure.HEALTH_DATA_TYPE
HealthConstants.BodyComposition.HEALTH_DATA_TYPE
HealthConstants.BodyTemperature.HEALTH_DATA_TYPE
HealthConstants.Exercise.HEALTH_DATA_TYPE
HealthConstants.ExerciseLocation.HEALTH_DATA_TYPE
HealthConstants.FloorsClimbed.HEALTH_DATA_TYPE
HealthConstants.HeartRate.HEALTH_DATA_TYPE
HealthConstants.Nutrition.HEALTH_DATA_TYPE
HealthConstants.Sleep.HEALTH_DATA_TYPE
HealthConstants.WaterIntake.HEALTH_DATA_TYPE
```

### Health Data Types available for READ only

```kotlin
HealthConstants.ActivitySummary.HEALTH_DATA_TYPE
HealthConstants.EnergyScore.HEALTH_DATA_TYPE
HealthConstants.IrregularHeartRhythmNotification.HEALTH_DATA_TYPE
HealthConstants.SkinTemperature.HEALTH_DATA_TYPE
HealthConstants.SleepApnea.HEALTH_DATA_TYPE
HealthConstants.Steps.HEALTH_DATA_TYPE
HealthConstants.UserProfile.HEALTH_DATA_TYPE
// ... (read-only types come from Galaxy Watch / Samsung Health internal sensors)
```

---

## Samsung Health Sensor SDK — Key API Surface

Only relevant if you add Galaxy Watch as a supplementary sensor alongside the suit. Runs on the watch side (Wear OS app), not the phone.

```kotlin
// app/build.gradle for the Wear OS module:
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8   // Note: 1.8, not 17
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(fileTree(dir: 'libs', include: '*.aar'))
}

// Key classes:
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.data.HealthTrackerType

// Available tracker types:
HealthTrackerType.HEART_RATE_CONTINUOUS
HealthTrackerType.ECG
HealthTrackerType.PPG_GREEN          // Raw green PPG
HealthTrackerType.PPG_IR             // Raw infrared PPG
HealthTrackerType.PPG_RED            // Raw red PPG
HealthTrackerType.ACCELEROMETER
HealthTrackerType.BIA                // Body composition
HealthTrackerType.SKIN_TEMPERATURE
HealthTrackerType.SPO2               // Blood oxygen
HealthTrackerType.EDA                // Electrodermal activity (stress)
HealthTrackerType.SWEAT_LOSS
```

---

## BLE (Nordic BleManager) — Key Pattern

```kotlin
// build.gradle dependency: no.nordicsemi.android:ble:2.8.1

import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data

class SmartSuitBleManager(context: Context) : BleManager(context) {

    // GATT characteristic UUIDs — match exactly what's in suit firmware
    private val HR_SERVICE_UUID     = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val HR_CHAR_UUID        = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val CUSTOM_SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-567812345678")
    private val ECG_CHAR_UUID       = UUID.fromString("your-ecg-char-uuid-here")

    private var hrCharacteristic: BluetoothGattCharacteristic? = null
    private var ecgCharacteristic: BluetoothGattCharacteristic? = null

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val hrService = gatt.getService(HR_SERVICE_UUID)
        hrCharacteristic = hrService?.getCharacteristic(HR_CHAR_UUID)
        return hrCharacteristic != null
    }

    override fun initialize() {
        // Subscribe to HR notifications
        setNotificationCallback(hrCharacteristic).with { _, data ->
            val bpm = data.getIntValue(Data.FORMAT_UINT8, 1) ?: 0
            onHeartRateReceived(bpm)
        }
        enableNotifications(hrCharacteristic).enqueue()

        // Request larger MTU for ECG window (float32[256] = 1024 bytes)
        requestMtu(517).enqueue()
    }

    override fun onDeviceDisconnected() {
        hrCharacteristic = null
        ecgCharacteristic = null
    }
}
```

---

## TensorFlow Lite — Inference Pattern

```kotlin
// build.gradle: org.tensorflow:tensorflow-lite:2.15.0
//               org.tensorflow:tensorflow-lite-support:0.4.4
//               org.tensorflow:tensorflow-lite-gpu:2.15.0

import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ECGAnomalyModel(context: Context) {

    private val interpreter: Interpreter

    init {
        val compatList = CompatibilityList()
        val options = Interpreter.Options().apply {
            if (compatList.isDelegateSupportedOnThisDevice) {
                addDelegate(GpuDelegate(compatList.bestOptionsForThisDevice))
            } else {
                numThreads = 4
            }
        }
        val modelBuffer = loadModelFile(context, "ecg_anomaly.tflite")
        interpreter = Interpreter(modelBuffer, options)
    }

    // Input:  float32[1][256] — 1-second ECG window at 256 Hz
    // Output: float32[1][4]  — [Normal, AFib, Tachycardia, Bradycardia]
    fun predict(ecgWindow: FloatArray): FloatArray {
        val input = Array(1) { ecgWindow }
        val output = Array(1) { FloatArray(4) }
        interpreter.run(input, output)
        return output[0]
    }

    private fun loadModelFile(context: Context, filename: String): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
```

---

## Room Database — Sensor History Schema

```kotlin
// build.gradle: androidx.room:room-runtime:2.6.1 + room-ktx + ksp room-compiler

@Entity(tableName = "sensor_readings")
data class SensorReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val heartRate: Int,
    val spO2: Float,
    val skinTemp: Float,
    val humidity: Float,
    val bpSystolic: Int,
    val bpDiastolic: Int,
    val ecgAnomalyClass: String,    // "Normal", "AFib", etc.
    val formScore: Float,
    val dehydrationRisk: String,    // "Low", "Medium", "High"
    val overexertionStatus: String, // "Safe", "Caution", "Stop"
    val tegPowerMw: Float
)

@Dao
interface SensorReadingDao {
    @Insert suspend fun insert(reading: SensorReading)
    @Query("SELECT * FROM sensor_readings ORDER BY timestamp DESC LIMIT 100")
    fun getRecent(): Flow<List<SensorReading>>
    @Query("SELECT * FROM sensor_readings WHERE timestamp BETWEEN :from AND :to")
    suspend fun getRange(from: Long, to: Long): List<SensorReading>
}

@Database(entities = [SensorReading::class], version = 1)
abstract class SmartSuitDatabase : RoomDatabase() {
    abstract fun sensorReadingDao(): SensorReadingDao
    companion object {
        fun build(context: Context) = Room.databaseBuilder(
            context, SmartSuitDatabase::class.java, "smartsuit.db"
        ).build()
    }
}
```

---

## Permissions Request at Runtime (Compose)

```kotlin
// build.gradle: com.google.accompanist:accompanist-permissions:0.36.0

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BlePermissionGate(content: @Composable () -> Unit) {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        rememberMultiplePermissionsState(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BODY_SENSORS,
            )
        )
    } else {
        rememberMultiplePermissionsState(
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BODY_SENSORS,
            )
        )
    }

    if (permissions.allPermissionsGranted) {
        content()
    } else {
        LaunchedEffect(Unit) { permissions.launchMultiplePermissionRequest() }
    }
}
```

---

## Version Summary (paste this as LLM context header)

```
# Smart Workout Suit — Dependency Versions (as of June 2026)
Samsung Health Data SDK:    v1.1.0  (local AAR, from Samsung Developer Portal)
Samsung Health Sensor SDK:  v1.4.1  (local AAR, Galaxy Watch only)
Samsung Health app minimum: 6.30.2
Android minSdk:             29 (Android 10)
Android targetSdk:          35 (Android 15)
Kotlin:                     2.0.21
Gradle:                     8.5.2
Compose BOM:                2024.09.00
Navigation Compose:         2.8.0
Lifecycle:                  2.8.4
Coroutines:                 1.8.1
Nordic BLE:                 2.8.1
TFLite:                     2.15.0
TFLite Support:             0.4.4
TFLite GPU:                 2.15.0
Room:                       2.6.1
KSP:                        2.0.21-1.0.27
Vico charts:                1.14.0
MPAndroidChart:             v3.1.0  (via JitPack)
Accompanist Permissions:    0.36.0
Gson:                       2.9.0   (required by Samsung Health Data SDK)

DEPRECATED — DO NOT USE:
Samsung Health SDK for Android  (deprecated July 31, 2025)
```

---

## Known Gotchas

**Samsung Health Data SDK is a local AAR, not on Maven.** If an LLM writes `implementation("com.samsung.android.sdk.healthdata:...")` that line doesn't work — the package doesn't exist on any public repo. Always reference the local file.

**`kotlin-parcelize` plugin is required.** The Data SDK's data classes use `@Parcelize`. Without the plugin your build fails with a cryptic error.

**Java 17 for Data SDK, Java 1.8 for Sensor SDK.** If you have both in the same project (phone module + Wear OS module), each module sets its own `compileOptions` independently.

**Both SDKs refuse to work on an emulator.** BLE doesn't work on the standard Android emulator, and Samsung Health won't run on it either. Always test on a real Samsung phone and (for Sensor SDK) a real Galaxy Watch4 or later.

**MTU negotiation is required for ECG streaming.** The ECG window is `float32[256]` = 1024 bytes. Default BLE MTU is 23 bytes. Always call `requestMtu(517)` (max supported) on connect, then size your GATT characteristic notifications to `MTU - 3` bytes.

**Samsung Health app must be installed and version ≥ 6.30.2.** If the user doesn't have Samsung Health or it's outdated, `onConnectionFailed()` fires. Handle this with a "Please update Samsung Health" dialog.