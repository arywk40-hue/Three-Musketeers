# ElderCare Guardian — Dependencies Reference
**Last verified against Samsung docs:** June 2026  
**Purpose:** Full dependency list for the ElderCare Guardian Android app — paste this into LLM context when building

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
| Emulator support | Android app simulator mode works on an emulator; BLE and Samsung Health require real hardware |

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
apps/android/
├── app/
│   ├── libs/
│   │   └── samsung-health-data-api-*.aar   ← Samsung Health Data SDK (optional, reflection bridge works without it)
│   ├── src/main/
│   │   ├── java/com/eldercareguardian/
│   │   │   ├── ble/
│   │   │   ├── consent/
│   │   │   ├── samsung/
│   │   │   ├── ml/
│   │   │   ├── database/
│   │   │   ├── notifications/
│   │   │   ├── service/
│   │   │   ├── data/
│   │   │   ├── settings/
│   │   │   └── ui/
│   │   ├── assets/                          # Future .tflite model files go here
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

## app/build.gradle.kts (current — Session 10)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // id("kotlin-parcelize")  ← REMOVED: unused in this project
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")   // ← ADD: required for Firebase FCM
}

import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasLocalKeystore = keystorePropertiesFile.exists()
val ciStorePath = System.getenv("KEYSTORE_PATH")

data class KsCfg(val storeFile: String, val storePassword: String, val keyAlias: String, val keyPassword: String)

val signingCfg = when {
    hasLocalKeystore -> {
        val p = Properties().apply { load(keystorePropertiesFile.inputStream()) }
        KsCfg(p.getProperty("storeFile",""), p.getProperty("storePassword",""), p.getProperty("keyAlias",""), p.getProperty("keyPassword",""))
    }
    ciStorePath != null && ciStorePath.isNotBlank() -> KsCfg(ciStorePath, System.getenv("KEYSTORE_PASSWORD") ?: "", System.getenv("KEY_ALIAS") ?: "", System.getenv("KEY_PASSWORD") ?: "")
    else -> KsCfg("", "", "", "")
}

val hasSigningConfig = signingCfg.storeFile.isNotBlank() && signingCfg.storePassword.isNotBlank() && signingCfg.keyAlias.isNotBlank() && signingCfg.keyPassword.isNotBlank()

val appVersionCode: Int = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val appVersionName: String = System.getenv("VERSION_NAME") ?: "1.0.0-beta"

android {
    namespace = "com.eldercareguardian"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eldercareguardian"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs { create("release").apply { if (hasSigningConfig) { storeFile = file(signingCfg.storeFile); storePassword = signingCfg.storePassword; keyAlias = signingCfg.keyAlias; keyPassword = signingCfg.keyPassword } } }

    buildTypes {
        release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"); if (hasSigningConfig) signingConfig = signingConfigs.getByName("release") }
    }

    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("net.zetetic:sqlcipher-android:4.5.6")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
}
```

> **Note:** TFLite runtime is ACTIVE in build.gradle.kts (tensorflow-lite:2.15.0).
> The `TfLiteFallbackLoader` uses reflection so no `.tflite` file is needed to compile,
> but the dependency IS declared. Remove it only if binary size is a concern.

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
// Imports come from the local Samsung Health Data SDK AAR.
// Current SDK package root:
// com.samsung.android.sdk.health.data

val healthDataStore = HealthDataService.getStore(applicationContext)
```

### Requesting Permissions

```kotlin
val permissions = setOf(
    Permission.of(DataTypes.HEART_RATE, AccessType.READ),
    Permission.of(DataTypes.HEART_RATE, AccessType.WRITE),
    Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.WRITE),
    Permission.of(DataTypes.BLOOD_PRESSURE, AccessType.WRITE),
    Permission.of(DataTypes.BODY_TEMPERATURE, AccessType.WRITE),
    Permission.of(DataTypes.EXERCISE, AccessType.WRITE),
)

val granted = healthDataStore.getGrantedPermissions(permissions)
val missing = permissions - granted
if (missing.isNotEmpty()) {
    healthDataStore.requestPermissions(missing, activity)
}
```

### Writing Health Data (suit sensor → Samsung Health)

```kotlin
val dataPoint = HealthDataPoint.builder()
    .setStartTime(startTime)
    .setEndTime(endTime)
    .setDeviceId(registeredSmartSuitDeviceId)
    .addFieldData(DataType.HeartRateType.HEART_RATE, bpm)
    .build()

val request = DataTypes.HEART_RATE.insertDataRequestBuilder
    .addData(dataPoint)
    .build()

healthDataStore.insertData(request)
```

### Health Data Types available for WRITE

```kotlin
DataTypes.BLOOD_GLUCOSE
DataTypes.BLOOD_OXYGEN       // SpO2
DataTypes.BLOOD_PRESSURE
DataTypes.BODY_COMPOSITION
DataTypes.BODY_TEMPERATURE
DataTypes.EXERCISE
DataTypes.EXERCISE_LOCATION
DataTypes.FLOORS_CLIMBED
DataTypes.HEART_RATE
DataTypes.NUTRITION
DataTypes.SLEEP
DataTypes.WATER_INTAKE
```

### Health Data Types available for READ only

```kotlin
DataTypes.ACTIVITY_SUMMARY
DataTypes.ENERGY_SCORE
DataTypes.IRREGULAR_HEART_RHYTHM_NOTIFICATION
DataTypes.SKIN_TEMPERATURE
DataTypes.SLEEP_APNEA
DataTypes.STEPS
DataTypes.USER_PROFILE
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
# ElderCare Guardian — Dependency Versions (as of June 2026)
Samsung Health Data SDK:    v1.1.0  (local AAR, optional — reflection bridge works without it)
Samsung Health app minimum: 6.30.2
Android minSdk:             29 (Android 10)
Android targetSdk:          35 (Android 15)
Kotlin:                     2.0.21
Gradle:                     8.10.2
Compose BOM:                2024.09.00
Lifecycle:                  2.8.4
Coroutines:                 1.8.1
Room:                       2.6.1
KSP:                        2.0.21-1.0.27
Gson:                       2.9.0
SQLCipher:                  4.5.6
Firebase BOM:               33.1.0

ACTIVE (declared in build.gradle.kts):
TFLite:                     2.15.0
TFLite Support:             0.4.4
TFLite Metadata:            0.4.4
TFLite GPU:                 2.15.0

NOT USED (removed from build.gradle.kts):
Navigation Compose, Vico charts, MPAndroidChart, Accompanist Permissions,
AppCompat, Material (compat), Kotlinx Coroutines Core (android variant used)

DEPRECATED — DO NOT USE:
Samsung Health SDK for Android  (deprecated July 31, 2025)
```

---

## Known Gotchas

**Samsung Health Data SDK is a local AAR, not on Maven.** If an LLM writes `implementation("com.samsung.android.sdk.healthdata:...")` that line doesn't work — the package doesn't exist on any public repo. Always reference the local file.

**Do NOT add `kotlin-parcelize` unless you have `@Parcelize` classes in your own code.**
The Samsung Health Data SDK AAR ships pre-compiled — its internal Parcelable classes don't require the plugin on your side.

**Java 17 for Data SDK, Java 1.8 for Sensor SDK.** If you have both in the same project (phone module + Wear OS module), each module sets its own `compileOptions` independently.

**Both SDKs refuse to work on an emulator.** BLE doesn't work on the standard Android emulator, and Samsung Health won't run on it either. Always test on a real Samsung phone and (for Sensor SDK) a real Galaxy Watch4 or later.

**MTU negotiation is required for ECG streaming.** The ECG window is `float32[256]` = 1024 bytes. Default BLE MTU is 23 bytes. Always call `requestMtu(517)` (max supported) on connect, then size your GATT characteristic notifications to `MTU - 3` bytes.

**Samsung Health app must be installed and version ≥ 6.30.2.** If the user doesn't have Samsung Health or it's outdated, `onConnectionFailed()` fires. Handle this with a "Please update Samsung Health" dialog.
