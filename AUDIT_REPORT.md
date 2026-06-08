# ElderCare Guardian — Complete Repository Audit Report

**Auditor:** CTO / Principal Engineer perspective  
**Date:** June 2026  
**Repo:** Three-Musketeers / ElderCare Guardian  
**Team:** Pranay · Ariyan · Reman Dey  

---

## Executive Summary

ElderCare Guardian is a genuinely well-conceived prototype. The architecture is layered correctly, the BLE GATT contract is defined, the Android scaffold is functional, and the firmware can advertise and stream sensor data. However, this is **not yet production-ready** and has critical gaps that would cause it to fail in a real elderly-care deployment. The most severe issues are: a hardcoded BLE passkey, no auto-reconnect on drop, single-sample fall detection with no temporal context, a BP estimator that is clinically unsound, no background BLE service for persistent monitoring, and no real caregiver alert path beyond a local notification.

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
- `rootProject.name = "SmartSuit"` — the project is still named SmartSuit in `settings.gradle.kts`. Package name is `com.smartsuit`. Both should be renamed to `eldercare` or `eldercareguardian`.
- No `ProGuard`/`R8` rules file. If minification is enabled, reflection-based code (Room, SQLCipher, Gson) will break.
- `BloodPressureEstimator` uses a naive linear regression on HR + skin temperature — clinically invalid and must be prominently labeled or removed from production.
- `build.gradle.kts` has no `signingConfig` for release builds.
- No `versionCode` update strategy — stays at 1.
- Missing `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_HEALTH` permissions for background monitoring.
- Missing `ACCESS_BACKGROUND_LOCATION` (needed on API 30 for BLE scan in background on some devices).

### Code Patterns
- `CaregiverAlertPolicy` only has 2 levels (Normal / Check / Urgent). The prompt specifies 4 levels. Level 3 "Warning" is missing.
- `InactivityMonitor.assess()` accumulates `inactivitySeconds` across the app session but never resets on movement — once you hit the 20-minute threshold, it stays in Check forever until app restart.
- `FallDetectionEngine.assess()` operates on a **single IMU sample** (one frame of 6 floats). Real fall detection needs a temporal window of 50–200 samples. The current approach generates false positives from any vibration or tap.

---

## 3. BLE Architecture

### Strengths
- NimBLE-Arduino is the correct library choice for ESP32-C3.
- Serial notification queue (subscribe one characteristic at a time via `onDescriptorWrite`) is the correct approach — subscribing all at once is a known Android BLE bug.
- MTU negotiation to 517 bytes is correct for the 1024-byte ECG payload.
- Bond receiver with reconnect-after-bond is implemented correctly.
- Scan filter by device name avoids scanning the entire BLE neighbourhood.

### Weaknesses
- **No auto-reconnect on unexpected disconnect.** If the wearable goes out of range and comes back, the Android app does nothing. The user must manually reconnect.
- **Fixed passkey 123456** in firmware `SecurityCallbacks::onPassKeyRequest()`. This is documented and known but still ships. In production this is a critical vulnerability — any nearby device can pair.
- **KEYBOARD_ONLY IO capability** on the firmware side requires the Android phone to enter a passkey. But the Android app has no passkey entry UI. Pairing will silently fail or fall back to Just Works depending on device.
- The `BLUETOOTH_ADVERTISE` permission is declared in the manifest but the Android app never advertises — this is unnecessary noise.
- BLE scan mode is `SCAN_MODE_LOW_LATENCY` (high power). For a health monitoring app that scans repeatedly, this drains the phone battery fast. Should switch to `SCAN_MODE_BALANCED` after initial discovery.
- No scan timeout — the scan runs forever until explicitly stopped.
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
- **No watchdog timer.** If the firmware hangs (I²C bus lock-up is common with MPU-6050 on 3.3V), the device silently stops transmitting. A WDT that restarts the MCU after 10 seconds of no I²C activity is essential.
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
- **`BloodPressureEstimator` is clinically invalid.** A linear regression of HR and skin temperature cannot estimate blood pressure to any useful accuracy. The comment `isEstimated = true` is insufficient — this should be removed from production or labeled as "experimental trend indicator only."
- **`DehydrationRiskModel`** uses `sweatRatePercentPerMin` from the SHT40 sensor. SHT40 measures ambient relative humidity, not skin sweat rate. Computing delta humidity is a rough proxy, not a validated dehydration metric.
- **No trained TFLite models exist.** The `ml/` folder contains only a README. All inference is rule-based. The architecture documents claim TFLite models but none are present.
- **AFib detection threshold** (`AFIB_RMSSD_THRESHOLD_MS = 25`): RMSSD < 25 ms typically indicates low heart rate variability, not AFib. AFib is characterized by RMSSD > 50 ms with high irregularity. The logic is inverted.
- **`OverexertionModel`** is designed for fitness users (HR reserve, exertion zones) — not elderly people who should not be pushed to "Caution/Stop" thresholds based on normal movement.

---

## 6. Security

### Current State
- Room database encrypted with SQLCipher + EncryptedSharedPreferences: ✅ **Done**
- `allowBackup="false"` + data extraction rules: ✅ **Done**
- BLE bond receiver + reconnect after bond: ✅ **Done**
- BLE passkey entry (firmware side): ⚠️ **Hardcoded 123456 — not production-safe**
- Android passkey entry UI: ❌ **Missing — pairing will fail with KEYBOARD_ONLY**
- BLE encryption in flight: ⚠️ **Just Works fallback is likely active due to missing Android UI**
- Network transport: ✅ **No cloud — all local (acceptable for prototype)**
- No server-side component: ✅ **Correct for prototype**

### Critical Gaps
- **No INTERNET permission** — caregiver SMS alerts require either a backend or `SEND_SMS` permission. Neither is implemented.
- **No FCM/push path** — remote caregivers cannot be notified unless they have the app open on the same device.
- **Tap-to-dial only** — the "phone call escalation" feature in the alert pipeline is a user-facing dial intent. The app cannot auto-dial in an emergency.
- **No audit log of who acknowledged what** — for healthcare compliance this is required.

---

## 7. Reliability

### Current Issues
- No auto-reconnect after BLE drop — **show-stopper for 24/7 monitoring**.
- No watchdog in firmware — device can silently hang.
- Single point of failure: if the Android app process is killed, monitoring stops.
- `SensorFrameMerger` has a shared mutable `FallConfirmationBuffer` — race condition in multi-threaded context.
- BLE scan runs without timeout — `LOW_LATENCY` scan will drain phone battery.
- `delay(1000)` in firmware loop is not jitter-free — could drift.

### Reliability Rating: 4/10 for production, 6/10 for showcase demo

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
| **Security** | 5/10 | SQLCipher done, BLE passkey hardcoded, no SMS/push path |
| **Reliability** | 4/10 | No auto-reconnect, no watchdog, no background service |
| **Maintainability** | 6/10 | Good architecture, but 50KB monolithic Compose file, two conflicting firmware versions |
| **Deployment Readiness** | 2/10 | No Samsung AAR, no Play Store signing, no background monitoring, no FCM |

---

## Priority Fix List (Ordered)

1. **[P0] Add BLE auto-reconnect** in `SmartSuitBleDataSource` — reconnect with exponential backoff on disconnect.
2. **[P0] Add Foreground Service** for background BLE monitoring — this is the #1 safety requirement.
3. **[P0] Fix fall detection** to use a sliding temporal window, not a single frame.
4. **[P0] Fix AFib RMSSD logic** — threshold direction is inverted.
5. **[P1] Remove or prominently disclaimer BloodPressureEstimator** from clinical display.
6. **[P1] Fix `InactivityMonitor`** to reset counter on detected movement.
7. **[P1] Add Warning (Level 3) alert state** to `CaregiverAlertPolicy`.
8. **[P1] Add passkey entry UI** on Android or switch firmware to Numeric Comparison.
9. **[P1] Add scan timeout** and switch to `SCAN_MODE_BALANCED` after first discovery.
10. **[P2] Split `SmartSuitApp.kt`** into per-screen files.
11. **[P2] Rename project** from SmartSuit to ElderCare.
12. **[P2] Add watchdog timer** to firmware.
13. **[P2] Add deep sleep** to firmware between notify cycles.
14. **[P3] Samsung Health real AAR** integration.
15. **[P3] FCM/SMS caregiver alert path**.
