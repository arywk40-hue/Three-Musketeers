# ElderCare Guardian — Complete Repository Audit Report

**Auditor:** CTO / Principal Engineer perspective  
**Date:** June 2026 (updated after Phase 8 fixes)  
**Repo:** Three-Musketeers / ElderCare Guardian  
**Team:** Pranay · Ariyan · Reman Dey  

---

## Executive Summary

ElderCare Guardian is a genuinely well-conceived prototype. The architecture is layered correctly, the BLE GATT contract is defined, the Android scaffold is functional, and the firmware can advertise and stream sensor data. 

**Significant progress since last audit (June 2026 Phase 8):**
- ✅ Foreground Service (`ElderCareMonitorService`) implemented for 24/7 background monitoring
- ✅ BLE auto-reconnect with exponential backoff implemented
- ✅ 4-level caregiver alert system (Normal/Check/Warning/Emergency) implemented
- ✅ DPDPA consent screen mandatory at first launch
- ✅ SQLCipher encryption for Room database
- ✅ Release signing configured with keystore
- ✅ ProGuard/R8 rules added
- ✅ Package renamed to `com.eldercareguardian`
- ✅ AFib detection RMSSD logic fixed (was inverted)
- ✅ Firmware watchdog timer added (`esp_task_wdt`)
- ✅ Gradle wrapper updated to 8.10.2 (matches CI)
- ✅ All required BLE permissions added to manifest
- ✅ `android.enableJetifier=true` added to gradle.properties
- ✅ CI workflow cleaned up (removed redundant wrapper generation)
- ✅ Version bumped to 1.0.0-beta
- ✅ Wake lock permission added for BLE reliability
- ✅ BloodPressureEstimator removed from clinical display (clinically invalid)

**Remaining critical gaps for production:**
- Hardcoded BLE passkey (123456) — mitigated by DISPLAY_YESNO numeric comparison
- No remote caregiver notification (FCM/SMS backend needed)
- Fall detection not validated against real dataset
- Samsung Health Data SDK AAR not integrated (partner approval pending)
- No privacy policy / ToS hosted for Play Store

---

## 1. Architecture Review

### Strengths
- Clean five-layer architecture: Power → Sensors → Firmware → Android → Samsung Health.
- Simulator-first approach is correct for showcase reliability.
- `SensorFrameMerger` correctly gives BLE data priority over simulator when connected.
- Room database for alert persistence is a good architectural choice.
- `StateFlow`-based reactive UI pipeline (simulator → merger → ViewModel → Compose) is solid.

### Weaknesses
- **Two incompatible firmware implementations exist in the same repo:**
  - `hardware/embedded/` uses **Bluetooth Classic Serial** (BluetoothSerial + ArduinoJson) — completely different protocol.
  - `firmware/esp32-c3/main.ino` uses **NimBLE BLE GATT** — the correct target implementation.
  - This will confuse any new contributor. The embedded/ folder needs a large deprecation notice or removal.
- `SmartSuitBleDataSource.frames` always returns `emptyFlow()`. BLE telemetry arrives through the `_telemetry` StateFlow, but nothing joins that telemetry into a coherent frame stream — the ViewModel must manually combine simulator + telemetry every tick. This works today but will break if you ever want to use BLE as the sole data source.
- No background `Service` (Foreground Service) — the app dies when backgrounded. For elderly safety, monitoring **must** continue in the background.
- `SensorFrameMerger` is an `object` singleton with a mutable `FallConfirmationBuffer` field. This is a race condition if the merger is ever called concurrently (e.g., from two coroutine contexts).
- `SmartSuitViewModel` hardcodes `NoOpSamsungHealthBridge()`. There is no build-flavor or injection mechanism to swap in a real implementation without modifying source.

### Architectural Gaps
- No offline mode: if BLE drops, the app silently falls back to simulator data with no user indication.
- No foreground service for background monitoring.
- No data retention policy beyond the 7-day Room purge.

---

## 2. Android Code Quality

### Strengths
- Jetpack Compose with `StateFlow` is the correct modern stack.
- `@SuppressLint("MissingPermission")` is used correctly with try/catch SecurityException.
- `CaregiverPreferences` uses DataStore (not SharedPreferences) — correct.
- `DatabaseEncryption.kt` is implemented with SQLCipher + EncryptedSharedPreferences.
- `allowBackup="false"` in manifest is correct for health data.
- `AlertHistoryTracker` correctly debounces alert transitions.

### Weaknesses
- `SmartSuitApp.kt` is 50 KB — a single monolithic Compose file containing all screens. This will become unmaintainable quickly. It needs to be split into individual screen files.
- ~~`rootProject.name = "SmartSuit"` — the project is still named SmartSuit. Package name is `com.smartsuit`.~~ **✅ FIXED** — Renamed to `ElderCareGuardian` / `com.eldercareguardian`.
- ~~No `ProGuard`/`R8` rules file.~~ **✅ FIXED** — `proguard-rules.pro` added with keep rules for Room, SQLCipher, Gson, DataStore, Samsung SDK, Nordic BLE.
- `BloodPressureEstimator` — **✅ REMOVED from clinical display** (clinically invalid; marked as estimated only).
- ~~`build.gradle.kts` has no `signingConfig` for release builds.~~ **✅ FIXED** — Release signing with keystore.properties, R8 minification, resource shrinking.
- `versionCode` = 1, `versionName` = "1.0.0-beta" — **✅ UPDATED**
- Missing `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_HEALTH` permissions for background monitoring. **✅ FIXED** — Both added to manifest.
- Missing `ACCESS_BACKGROUND_LOCATION` (needed on API 30 for BLE scan in background on some devices).
- Gradle wrapper was 9.5.1 — **✅ FIXED** to 8.10.2 (matches CI)
- `android.enableJetifier=true` was missing — **✅ ADDED**

### Code Patterns
- ~~`CaregiverAlertPolicy` only has 2 levels (Normal / Check / Urgent). The prompt specifies 4 levels. Level 3 "Warning" is missing.~~ **✅ FIXED** — 4-level system: `Normal` / `Check` / `Warning` / `Emergency`.
- `InactivityMonitor.assess()` accumulates `inactivitySeconds` across the app session but never resets on movement — once you hit the 20-minute threshold, it stays in Check forever until app restart.
- `FallDetectionEngine.assess()` operates on a **single IMU sample** (one frame of 6 floats). Real fall detection needs a temporal window of 50–200 samples. The current approach generates false positives from any vibration or tap.
- **AFib RMSSD logic was inverted** — **✅ FIXED** (AFib is high RMSSD + irregularity, not low RMSSD)

---

## 3. BLE Architecture

### Strengths
- NimBLE-Arduino is the correct library choice for ESP32-C3.
- Serial notification queue (subscribe one characteristic at a time via `onDescriptorWrite`) is the correct approach — subscribing all at once is a known Android BLE bug.
- MTU negotiation to 517 bytes is correct for the 1024-byte ECG payload.
- Bond receiver with reconnect-after-bond is implemented correctly.
- Scan filter by device name avoids scanning the entire BLE neighbourhood.

### Weaknesses
- **Auto-reconnect implemented** — `SmartSuitBleDataSource` now has exponential backoff reconnect (max 10 attempts, 2s → 60s). **✅ FIXED**
- **Fixed passkey 123456** in firmware `SecurityCallbacks::onPassKeyRequest()`. This is documented and known but still ships. In production this is a critical vulnerability — any nearby device can pair.
- Firmware now uses `DISPLAY_YESNO` numeric comparison, so Android can rely on the system pairing dialog. Production still needs per-device pairing material instead of the fixed debug value.
- The `BLUETOOTH_ADVERTISE` permission is declared in the manifest but the Android app never advertises — this is unnecessary noise.
- BLE scan mode is `SCAN_MODE_LOW_LATENCY` (high power). For a health monitoring app that scans repeatedly, this drains the phone battery fast. Should switch to `SCAN_MODE_BALANCED` after initial discovery.
- Scan timeout added — 30s auto-stop if no device found. **✅ FIXED**
- ECG characteristic is 256 float32 = 1024 bytes. At the default ATT_MTU of 23, this would require 45 ATT packets. MTU 517 reduces this to 2. The firmware correctly relies on the Android side requesting a large MTU, but there is no fallback if MTU negotiation fails (older phones).
- Custom service UUID `12345678-1234-5678-1234-567812345678` is a placeholder. Production needs a registered UUID.
- UUID construction in firmware uses `BLE_UUID128_INIT` with bytes in the wrong display order relative to the architecture doc (little-endian vs big-endian confusion). The Android `SmartSuitBleContract` must agree exactly — verify before hardware testing.

---

## 4. Embedded Firmware Architecture

### Strengths
- Synthetic fallback path is excellent — BLE pipe stays alive even without real sensors.
- Piecewise-linear LiPo discharge curve in `vbatToPercent()` is more accurate than a single linear map.
- FIFO drain cap (`MAX_FIFO_DRAIN = 25`) prevents blocking the 1-second loop.
- SpO2 only transmitted when `g_spo2Valid` is set — no fake SpO2 values on startup.
- Security callbacks class structure is correct (though passkey is hardcoded).

### Weaknesses
- **`delay(1000)` at the end of `loop()`** is a hard blocking call. All BLE events (connect/disconnect, characteristic writes) are handled in NimBLE's task, but any timing-sensitive sensor operation will be blocked. Should use `vTaskDelay` with a task-based approach for production.
- **No deep sleep mode.** The firmware never sleeps — ESP32-C3 draws ~80 mA continuously. On a 1000 mAh cell, runtime is ~12 hours. For 24/7 elderly monitoring, deep sleep between sensor reads is mandatory.
- **Fall risk in firmware is trivially wrong:** `fallRisk = 0.95f if SOS active, else 0.0f`. The firmware sends fall risk only based on the SOS button, not IMU data. The real fall detection happens entirely in the Android app. This is architecturally fine for the showcase but must be documented.
- **Watchdog timer added** — `esp_task_wdt_init(15s)` with `esp_task_wdt_reset()` in loop. **✅ FIXED**
- **No OTA firmware update path.** Production wearables require OTA. Arduino OTA or NimBLE-based DFU is needed.
- **I²C is not reset on sensor init failure.** If `g_mpu.initialize()` leaves the bus in a bad state, subsequent MAX30102 reads will corrupt.
- `hardware/embedded/` old code uses `Wire.begin(21, 22)` — different pins from `firmware/esp32-c3/main.ino`'s GPIO 6/7. If someone flashes the wrong file, sensors will not init.

---

## 5. ML Pipeline

### Strengths
- Rule-based engines with a clear path to swap in TFLite models is architecturally sound.
- `HeartRateExtractor` has baseline wander removal, adaptive threshold, and refractory period — genuinely good signal processing.
- `EcgAnomalyDetector` uses RMSSD + RR irregularity for AFib detection — clinically reasonable for a prototype.
- All engines are `object` singletons with single public functions — easy to replace with TFLite.

### Weaknesses / Medical Risks
- **Fall detection (`FallDetectionEngine`) uses a single IMU frame, not a window.** One frame = 6 floats at one moment in time. Real falls require: (1) high-G spike phase, (2) free-fall phase, (3) post-impact stillness phase. A single magnitude check misses phases 1 and 3 and generates false positives from bumps. The threshold of 24.5 m/s² (~2.5g) will trigger on an energetic arm swing.
- **`BloodPressureEstimator` removed from clinical display** — clinically invalid linear regression of HR + skin temperature. **✅ REMOVED**
- **`DehydrationRiskModel`** uses `sweatRatePercentPerMin` from the SHT40 sensor. SHT40 measures ambient relative humidity, not skin sweat rate. Computing delta humidity is a rough proxy, not a validated dehydration metric.
- **No trained TFLite models exist.** The `ml/` folder contains only a README. All inference is rule-based. The architecture documents claim TFLite models but none are present.
- **AFib detection logic was inverted** — **✅ FIXED** (AFib is high RMSSD + irregularity, not low RMSSD)
- **`OverexertionModel`** is designed for fitness users (HR reserve, exertion zones) — not elderly people who should not be pushed to "Caution/Stop" thresholds based on normal movement.

---

## 6. Security

### Current State
- Room database encrypted with SQLCipher + EncryptedSharedPreferences: ✅ **Done**
- `allowBackup="false"` + data extraction rules: ✅ **Done**
- BLE bond receiver + reconnect after bond: ✅ **Done**
- BLE passkey entry (firmware side): ⚠️ **Hardcoded 123456 — mitigated by DISPLAY_YESNO numeric comparison**
- Android passkey entry UI: ✅ **Custom UI not required for current `DISPLAY_YESNO` numeric comparison; system dialog handles pairing**
- BLE encryption in flight: ✅ **LE Secure Connections enabled with bonding + MITM**
- Network transport: ✅ **No cloud — all local (acceptable for prototype)**
- No server-side component: ✅ **Correct for prototype**
- `SEND_SMS` permission added for SMS alert path (Play Store justification needed)

### Critical Gaps
- **No INTERNET permission** — caregiver SMS alerts require either a backend or `SEND_SMS` permission. `SEND_SMS` added.
- **No FCM/push path** — remote caregivers cannot be notified unless they have the app open on the same device.
- **Tap-to-dial only** — the "phone call escalation" feature in the alert pipeline is a user-facing dial intent. The app cannot auto-dial in an emergency.
- **No audit log of who acknowledged what** — for healthcare compliance this is required.

---

## 7. Reliability

### Current Issues
- Auto-reconnect implemented — exponential backoff, max 10 attempts. **✅ FIXED**
- Watchdog timer added to firmware — `esp_task_wdt_init(15s)`. **✅ FIXED**
- Foreground Service implemented — `ElderCareMonitorService` with `connectedDevice|health` type. **✅ FIXED**
- `SensorFrameMerger` has a shared mutable `FallConfirmationBuffer` — race condition in multi-threaded context.
- BLE scan timeout added — 30s auto-stop. **✅ FIXED**
- `delay(1000)` in firmware loop is not jitter-free — could drift.

### Reliability Rating: 6/10 for production, 8/10 for showcase demo

---

## 8. Scalability

- Single-patient, single-caregiver model is hardcoded.
- No multi-patient support (e.g., for a care home with 10 residents).
- DataStore preferences are global — no per-patient profiles.
- Alert history is unbounded in memory (capped at 60 trend points, 7 days in Room).
- Samsung Health integration uses a `NoOp` bridge — no real path to multi-device Samsung data.

---

## 9. Battery Considerations

### Firmware (Wearable)
- No deep sleep — continuous draw ~80 mA → ~12 hour runtime on 1000 mAh.
- BLE advertising at max power (`ESP_PWR_LVL_P9`) wastes energy — reduce to P3 for the showcase.
- Battery ADC sampled 8 times every loop tick (1 Hz) — acceptable but adds to duty cycle.
- `vbatToPercent()` piecewise table is reasonable but needs bench calibration against real cell.

### Android (Phone)
- `SCAN_MODE_LOW_LATENCY` during scan burns phone battery fast.
- 5-second Samsung Health write cadence is fine but the `while(true)` loop should yield properly if bridge is unavailable.
- No `JobScheduler`/`WorkManager` for background — everything is ViewModel scope.

---

## 10. Samsung Health Readiness

- Samsung Health Data SDK AAR: **not present in `libs/`** — bridge is NoOp placeholder.
- Samsung Health Accessory SDK: **not integrated** — device is not registered as "Works With Samsung Health."
- Partner App Program approval: **not applied for** — write access to Samsung Health requires approval.
- GATT profile uses standard SIG UUIDs (HR 0x180D, PLX 0x1822) — Samsung Health will auto-read these once Accessory SDK is integrated.
- Data SDK consent flow: documented in architecture but not implemented in app.
- `SamsungHealthState.NeedsPartnerApproval` state exists in code but real consent dialog is not shown.

---

## Scores

| Dimension | Score | Notes |
|---|---|---|
| **Security** | 6/10 | SQLCipher done, BLE passkey mitigated by DISPLAY_YESNO, SEND_SMS added |
| **Reliability** | 6/10 | Auto-reconnect, watchdog, foreground service implemented |
| **Maintainability** | 6/10 | Good architecture, but 50KB monolithic Compose file, two conflicting firmware versions |
| **Deployment Readiness** | 7/10 | Package renamed, signing configured, DPDPA consent added, ProGuard rules set, version bumped, CI fixed. Samsung AAR + FCM still missing. |

---

## Priority Fix List (Ordered)

1. **[P0] Add BLE auto-reconnect** — `reconnectGatt()` after bond; no exponential-backoff for general drops. **✅ Done** — exponential backoff (max 10 attempts, 2s → 60s).
2. **[P0] Add Foreground Service** for background BLE monitoring. **✅ Done** — `ElderCareMonitorService` with `connectedDevice|health` type.
3. **[P0] Fix fall detection** to use a sliding temporal window. **FallConfirmationBuffer added, single-frame `FallDetectionEngine` unchanged.**
4. **[P0] Fix AFib RMSSD logic** — threshold direction was inverted. **✅ Fixed.**
5. **[P1] Remove BloodPressureEstimator** from clinical display. **✅ Done** — clinically invalid, removed.
6. **[P1] Fix `InactivityMonitor`** to reset counter on detected movement. **Not done.**
7. **[P1] Add Warning (Level 3) alert state** to `CaregiverAlertPolicy`. **✅ Done — 4-level system implemented.**
8. **[P1] Add passkey entry UI** on Android or switch firmware to Numeric Comparison. **✅ Numeric comparison + bond receiver + system dialog handles pairing.**
9. **[P1] Add scan timeout** and switch to `SCAN_MODE_BALANCED` after first discovery. **✅ Scan timeout (30s) added; SCAN_MODE_BALANCED after first discovery not yet done.**
10. **[P2] Split `SmartSuitApp.kt`** into per-screen files. **Not done.**
11. **[P2] Rename project** from SmartSuit to ElderCare. **✅ Done — `com.eldercareguardian`, `ElderCareGuardian`.**
12. **[P2] Add watchdog timer** to firmware. **✅ Done — `esp_task_wdt_init(15s)` added.**
13. **[P2] Add deep sleep** to firmware between notify cycles. **Not done.**
14. **[P3] Samsung Health real AAR** integration. **`NoOpSamsungHealthBridge` + `NeedsPartnerApproval` state; real AAR not present.**
15. **[P3] FCM/SMS caregiver alert path**. **Not done.**
16. **[P3] Privacy Policy / ToS** hosted for Play Store listing. **Not done.**

---

### Items Addressed Since Audit

| Item | What Was Done |
|------|---------------|
| Room encryption (SQLCipher) | ✅ `DatabaseEncryption.kt`, `SupportFactory`, `eldercare_encrypted.db`, security-model updated |
| BLE bonding + pairing | ✅ `SecurityCallbacks` on firmware, `BondStateReceiver` + `createBond()` + `reconnectGatt()` on Android, `BleConnectionState.Bonding/Bonded` |
| Sensor input validation | ✅ `SensorFrameValidation.kt` with per-field bounds (HR, SpO2, RR, humidity, temp, ECG, IMU accel/gyro, fall risk, device state) |
| LiPo calibration | ✅ Multi-segment piecewise `vbatToPercent()` (16-entry `CAL_TABLE`) replacing single linear map |
| Samsung Partner UX | ✅ `SamsungHealthState.NeedsPartnerApproval` state + UI text directing to developer.samsung.com |
| Battery-model doc | ✅ Reflects piecewise table |
| Firmware README | ✅ Updated to reflect integrated I²C sensors (MAX30102 + MPU-6050), not "Phase 2" |
| LAUNCH_BLOCKERS statuses | ✅ Status column updated for completed/partially-completed items |
| build.log | ✅ Four entries for Room encryption, BLE pairing security, sensor validation, LiPo calibration |
| Package rename | ✅ `com.smartsuit` → `com.eldercareguardian`, `rootProject.name` → `ElderCareGuardian`, directory tree migrated |
| Signing config | ✅ `eldercare-release.jks` keystore, `keystore.properties`, `signingConfigs.release` in `build.gradle.kts` |
| ProGuard rules | ✅ `proguard-rules.pro` with keep rules for Room, SQLCipher, Gson, DataStore, Compose, security-crypto |
| DPDPA consent screen | ✅ `DpdpaConsentScreen.kt` + `ConsentPreferences.kt` — mandatory first-launch gate before health data collection |
| 4-level alert system | ✅ `CaregiverAlertStatus.Emergency` + `Warning` added across `StatusPill`, `AlertTimeline`, `SmartSuitApp`, `SmartSuitViewModel` |
| BLE auto-reconnect | ✅ Exponential backoff (max 10 attempts, 2s→60s), scan timeout (30s), bond receiver reconnect |
| Foreground Service | ✅ `ElderCareMonitorService` with `connectedDevice|health` foreground type |
| AFib RMSSD logic fix | ✅ Corrected inverted threshold (AFib = high RMSSD + irregularity) |
| BloodPressureEstimator removal | ✅ Removed from clinical display (clinically invalid) |
| Watchdog timer | ✅ `esp_task_wdt_init(15s)` in firmware |
| Gradle wrapper fix | ✅ Updated to 8.10.2 (matches CI) |
| Jetifier enabled | ✅ `android.enableJetifier=true` added to gradle.properties |
| CI workflow cleanup | ✅ Removed redundant gradle wrapper generation step |
| Version bump | ✅ 1.0.0-beta with versionCode=1 |
| Wake lock permission | ✅ `WAKE_LOCK` added for BLE reliability |
| ProGuard Samsung/Nordic rules | ✅ Keep rules for Samsung SDK, Nordic BLE, BluetoothGattCallback |
