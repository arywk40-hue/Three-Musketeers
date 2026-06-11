plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
}

import java.util.Properties

// ── Signing: local keystore.properties file or CI env vars ──
// CI workflow decodes KEYSTORE_BASE64 into a file and passes the path.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasLocalKeystore = keystorePropertiesFile.exists()
val ciStorePath = System.getenv("KEYSTORE_PATH")

data class KsCfg(val storeFile: String, val storePassword: String, val keyAlias: String, val keyPassword: String)

val signingCfg = when {
    hasLocalKeystore -> {
        val p = Properties().apply { load(keystorePropertiesFile.inputStream()) }
        KsCfg(
            p.getProperty("storeFile", ""),
            p.getProperty("storePassword", ""),
            p.getProperty("keyAlias", ""),
            p.getProperty("keyPassword", ""),
        )
    }
    ciStorePath != null && ciStorePath.isNotBlank() -> KsCfg(
        ciStorePath,
        System.getenv("KEYSTORE_PASSWORD") ?: "",
        System.getenv("KEY_ALIAS") ?: "",
        System.getenv("KEY_PASSWORD") ?: "",
    )
    else -> KsCfg("", "", "", "")
}

val hasSigningConfig = signingCfg.storeFile.isNotBlank() &&
    signingCfg.storePassword.isNotBlank() &&
    signingCfg.keyAlias.isNotBlank() &&
    signingCfg.keyPassword.isNotBlank()

// ── Version from CI env or fallback ──
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

    signingConfigs {
        create("release").apply {
            if (hasSigningConfig) {
                storeFile = file(signingCfg.storeFile)
                storePassword = signingCfg.storePassword
                keyAlias = signingCfg.keyAlias
                keyPassword = signingCfg.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
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
