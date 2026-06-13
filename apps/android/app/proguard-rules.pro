# ElderCare Guardian — ProGuard Rules
# ====================================

# ── Room ──
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# ── SQLCipher ──
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# ── Gson ──
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }

# ── DataStore ──
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# ── Kotlin Coroutines ──
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── Compose ──
-dontwarn androidx.compose.**

# ── ElderCare data classes (used in serialization) ──
-keep class com.eldercareguardian.data.** { *; }
-keep class com.eldercareguardian.ble.SmartSuitBleTelemetry { *; }
-keep class com.eldercareguardian.database.AlertEventEntity { *; }

# ── Security ──
-keep class androidx.security.crypto.** { *; }

# ── Samsung Health SDK ──
-keep class com.samsung.android.sdk.** { *; }
-dontwarn com.samsung.android.sdk.**

# ── Nordic Semiconductor BLE libraries ──
-keep class no.nordicsemi.** { *; }
-dontwarn no.nordicsemi.**

# ── Bluetooth GATT callbacks ──
-keepclassmembers class * extends android.bluetooth.BluetoothGattCallback { *; }

# ── TensorFlow Lite ──
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
