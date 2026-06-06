# Smart Workout Suit — Development Workflow
**Team:** Pranay · Ariyan · Reman Dey  
**Version:** 1.0 | **Date:** June 2026  
**Stack:** ESP32-C3 / nRF5340 → BLE 5.x → Samsung Health SDK → Android App → ML Models

---

## Overview

The project has two parallel tracks that converge at Phase 4:

- **Hardware track** (Reman + Ariyan): Power system → Sensors → MCU → BLE GATT firmware
- **Software track** (Pranay + Ariyan): Samsung SDK registration → Android app → ML models

They merge when the suit's BLE GATT profile is registered as a "Works With Samsung Health" accessory and the Android app starts consuming live sensor data.

---

## Phase 0 — Samsung SDK Registration & Dev Setup
**Duration:** 1 week (can run in parallel with Phase 1)  
**Owner:** Pranay

### 0.1 Samsung Developer Account
Go to [developer.samsung.com](https://developer.samsung.com) → create/log in with Samsung account. This is required before you can download any Health SDK.

### 0.2 Download the SDKs you need
From [developer.samsung.com/health](https://developer.samsung.com/health) you need three:

| SDK | What it does for us | Where to get it |
|-----|---------------------|-----------------|
| **Samsung Health Accessory SDK** | Registers the suit as a BLE health device; defines GATT spec for Samsung Health to recognise it | developer.samsung.com/health/accessory |
| **Samsung Health Data SDK** | Reads/writes health data to the Samsung Health platform from your Android app | developer.samsung.com/health/data |
| **Samsung Health Sensor SDK** *(optional)* | If user also wears a Galaxy Watch, fuse watch sensor data with suit data | developer.samsung.com/health/sensor |

### 0.3 Read the Accessory SDK spec
The Accessory SDK is a **GATT compatibility spec**, not just a library — it tells you exactly how to structure your BLE services so Samsung Health recognises the device. Download "Health Accessory Specs" from the Accessory page and read the device specs for:
- Blood pressure service (SIG: 0x1810)
- Heart rate service (SIG: 0x180D)
- Body temperature service (SIG: 0x1809)
- SpO2 / PLX service (SIG: 0x1822)

Any custom services (IMU, humidity, respiratory) go into vendor-specific GATT services with custom UUIDs.

### 0.4 Android project scaffold
```
smart-suit-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/smartsuit/
│   │   │   ├── ble/          # BLE scan + GATT client
│   │   │   ├── samsung/      # Samsung Health Data SDK wrapper
│   │   │   ├── ml/           # TFLite model runners
│   │   │   └── ui/           # Dashboard screens
│   │   └── res/
├── model/                    # .tflite model files
└── build.gradle
```

### 0.5 Partner App Program
The Samsung Health Data SDK requires applying to the **Partner App Program** before you can write data to Samsung Health. Apply early — approval takes time. Get the form at developer.samsung.com/health/data → "Download SDK". For the prototype/demo, the Sensor SDK (no approval needed) is enough to display data locally without writing to Samsung Health.

---

## Phase 1 — Power Bench Test
**Duration:** 1–2 weeks  
**Owner:** Reman Dey

Goal: prove the TEG + supercapacitor can hold 3.3 V without any external supply while worn.

### Steps
1. Wire TEC1-12706 TEG → TPS61220 boost converter → 470 µF supercapacitor
2. Place TEG on skin (forearm, no suit yet). Measure Vout with multimeter.
3. Add DFRobot FIT0333 solar panel in parallel → verify combined output in sunlight
4. Add piezo membrane in parallel → test during walking on treadmill
5. Wire hysteretic switch: MCU only powers on when Vcap > 3.3 V, hibernates below 3.0 V

### Success criterion
Stable 3.3 V at Vout sustained for 30 minutes of light activity, indoors, no external supply.

### Deliverable
Voltage vs time plot (just screenshot from oscilloscope or Arduino serial plotter) — needed for the showcase power demo.

---

## Phase 2 — Sensor Validation
**Duration:** 1–2 weeks  
**Owner:** Ariyan

Validate each sensor independently before integrating into the MCU.

### Sensor checklist

| Sensor | Chip | Interface | Test tool | What to verify |
|--------|------|-----------|-----------|----------------|
| ECG | AD8232 | Analog → ADC | Arduino Uno + Serial Plotter | Clean PQRST waveform on chest |
| SpO2 + HR | MAX30102 | I²C | Arduino + library | SpO2 > 95%, HR matches pulse |
| IMU × 3 | MPU-6050 | I²C | Arduino | Accel + gyro stable at rest; rep detection on curl |
| Skin temp | TMP117 | I²C | Arduino | ±0.1°C accuracy vs thermometer |
| Humidity / sweat | SHT40 | I²C | Arduino | Reads relative humidity change on skin |

### I²C address conflict resolution
MPU-6050 default I²C address is 0x68 (AD0 low) or 0x69 (AD0 high). With 3 IMUs you need an I²C multiplexer (TCA9548A, ~₹150 on Robu) or use SPI for two of them.

### Deliverable
Arduino sketches + serial log for each sensor — hand off to Pranay for firmware integration reference.

---

## Phase 3 — BLE GATT Firmware
**Duration:** 2–3 weeks  
**Owner:** Pranay + Ariyan

Replace the Arduino test setup with the real MCU and write BLE GATT firmware that Samsung Health can parse.

### 3.1 MCU choice
Start with **ESP32-C3** (cheaper, widely available, BLE 5.0). Use the ESP-IDF BLE stack (not Arduino BLE library — it's too limited for proper GATT server setup).

Switch to **nRF5340** for the final prototype because it sleeps at ~1 µA vs ~5 µA for ESP32-C3. The 2023 Nature paper uses nRF's architecture directly.

### 3.2 GATT Server profile on the suit (peripheral role)

```
Generic Access Service (0x1800)
  Device Name: "SmartSuit_v1"

Battery Service (0x180F)
  Battery Level: [0-100%] — derived from supercap voltage

Heart Rate Service (0x180D)                 → Samsung Health reads this natively
  Heart Rate Measurement [0x2A37]

PLX / SpO2 Service (0x1822)                 → Samsung Health reads this natively
  PLX Continuous Measurement [0x2A5F]

Blood Pressure Service (0x1810)             → Samsung Health reads this natively
  Blood Pressure Measurement [0x2A35]       (PPG-derived, flagged as estimated)

Health Thermometer Service (0x1809)
  Temperature Measurement [0x2A1C]

Custom SmartSuit Service (UUID: your-uuid)  → read by your Android app
  IMU_Elbow_L  [0x2A?? custom]
  IMU_Elbow_R  [0x2A?? custom]
  IMU_Lumbar   [0x2A?? custom]
  Humidity     [0x2A?? custom]
  Resp_Rate    [0x2A?? custom]
  Power_mW     [0x2A?? custom]              (live TEG output for demo)
```

### 3.3 Burst-and-sleep loop (from Nature 2023 paper)
```
loop:
  wait until Vcap > 3.3 V          (hysteretic comparator interrupt)
  wake MCU
  read all sensors over I²C / SPI
  assemble GATT notification packets
  BLE advertise + notify connected phone  (~1.6 s window)
  hibernate MCU + BLE radio
  repeat
```

### 3.4 Verify Samsung Health Accessory SDK compliance
With Accessory SDK docs open, confirm your GATT service UUIDs and characteristic formats match exactly what the spec requires. Samsung Health will only auto-recognise standard SIG services — custom services need your Android app to bridge the data.

---

## Phase 4 — Android App
**Duration:** 2–3 weeks  
**Owner:** Pranay

### 4.1 BLE client (scan → connect → subscribe)
Use Android's `BluetoothLeScanner` to find devices advertising "SmartSuit_v1". On connect, subscribe to all GATT notifications. This is your raw sensor data stream.

### 4.2 Samsung Health Data SDK integration
```kotlin
// app/build.gradle.kts
// Samsung Health Data SDK is downloaded from Samsung Developer Portal.
// It is a local AAR, not a Maven dependency.
implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("samsung-health-data-api*.aar"))))
implementation("com.google.code.gson:gson:2.9.0")

// SamsungHealthBridge implementation shape
val healthDataStore = HealthDataService.getStore(applicationContext)
val dataPoint = HealthDataPoint.builder()
    .setStartTime(startTime)
    .setEndTime(endTime)
    .setDeviceId(registeredSmartSuitDeviceId)
    .addFieldData(DataType.HeartRateType.HEART_RATE, latestHR)
    .build()

val request = DataTypes.HEART_RATE.insertDataRequestBuilder
    .addData(dataPoint)
    .build()

healthDataStore.insertData(request)
```

Data types you write to Samsung Health:
- `DataTypes.HEART_RATE`
- `DataTypes.BLOOD_OXYGEN`
- `DataTypes.BLOOD_PRESSURE`
- `DataTypes.BODY_TEMPERATURE`

### 4.3 Live dashboard screens

| Screen | Data shown | Source |
|--------|------------|--------|
| **Home** | ECG waveform (scrolling), HR, SpO2, RR | BLE GATT |
| **Workout** | Rep counter, posture status (Good/Warning/Bad), cadence | ML model output |
| **Health** | BP estimate, hydration score, fatigue index | ML model output |
| **Power** | TEG mW live, solar mW, supercap %, est. runtime | GATT custom service |
| **History** | Samsung Health data — past workouts | Samsung Health Data SDK |

### 4.4 ML inference on Android
Run TFLite models on-device. Each model gets a sliding window of sensor data as input. See Phase 5 for model details.

```kotlin
val interpreter = Interpreter(loadModelFile("ecg_anomaly.tflite"))
val input = Array(1) { FloatArray(256) }  // 256-sample ECG window
val output = Array(1) { FloatArray(4) }   // 4 class probs: Normal, AFib, Tachycardia, Bradycardia
interpreter.run(input, output)
```

---

## Phase 5 — ML Models
**Duration:** 3–4 weeks (can overlap Phase 4)  
**Owner:** Pranay (model integration) + team for data collection

### 5.1 Models to build

| Model | Input | Output | Algorithm | Dataset source |
|-------|-------|--------|-----------|----------------|
| ECG anomaly | 256-sample ECG window (256 Hz) | Normal / AFib / Tachy / Brady | 1D-CNN | PTB-XL, MIT-BIH |
| VO2 max estimation | HR + age + IMU cadence | VO2max (mL/kg/min) | Ridge regression | Published formula + fine-tune |
| Rep counter + form | 3-axis IMU (200 Hz), 2 s window | Rep count + form score [0–10] | LSTM | Self-collected during workout |
| Dehydration risk | SHT40 sweat rate, skin temp, HR | Low / Medium / High risk | Random Forest | Self-collected |
| Overexertion / fatigue | HR, SpO2, RR, IMU intensity | Safe / Caution / Stop | XGBoost / SVM | Self-collected |
| BP estimation | PPG waveform features (MAX30102) | Systolic / Diastolic mmHg | CNN feature extractor | MIMIC-III / UCI BP |

### 5.2 Data collection plan
For custom models (rep counter, dehydration, fatigue) you need your own data:

**Collect during Phase 3/4 testing:**
- Wear suit during workouts, log raw sensor CSV with timestamps
- Label manually: rep boundaries (video sync), fatigue onset (RPE scale), form errors (visual check)
- Target: 2–3 hours of labelled workout data minimum

### 5.3 Training pipeline
```
raw CSV data → preprocessing (filter, normalise, window) 
→ feature extraction (for tabular models) or raw input (for CNN/LSTM)
→ train in Python (TF/Keras or scikit-learn)
→ convert to TFLite: model.tflite + metadata
→ copy to Android app /assets/
→ run via TFLite Interpreter on device
```

### 5.4 Evaluation targets

| Model | Target metric | Acceptable threshold |
|-------|---------------|---------------------|
| ECG anomaly | F1-score (macro) | > 0.85 on held-out test |
| Rep counter | MAE (reps per set) | ≤ 1 rep |
| Dehydration risk | Recall on High risk | > 0.90 |
| Overexertion | Recall on Stop class | > 0.95 |
| BP estimation | MAE | < 10 mmHg systolic |

---

## Phase 6 — Suit Embedding
**Duration:** 2 weeks  
**Owner:** Ariyan

### Steps
1. Sew flat flex PCB traces into compression shirt along ECG lead paths
2. Mount TEG on upper back with silicone contact pad (maximise skin surface area — Reman's task)
3. Sew conductive thread from shoulders to MCU board for solar panel connections
4. Mount ECG dry-contact electrodes on chest inner lining with snap connectors
5. Mount 3 IMUs at elbows (left/right) and lumbar, secured with elastic patches
6. Attach MAX30102 SpO2 module to wrist cuff with velcro
7. Insert piezo membrane into shoe sole between heel and arch
8. Encase MCU + power management board in a thin TPU pouch, clip to waistband

### Washability note
TEG patch, solar panels, and piezo insert must be **removable** before washing. Use waterproof snap connectors (JST or similar) at all module connection points.

---

## Phase 7 — Integration Testing & Showcase Prep
**Duration:** 1–2 weeks  
**Owner:** Full team

### Integration test checklist
- [ ] Suit worn for 30 min continuous workout — no power loss
- [ ] Samsung Health shows HR, SpO2, BP in its built-in history view
- [ ] ECG waveform renders without artefact on app dashboard
- [ ] Rep counter ≤ 1 rep error on 3 sets × 10 bicep curls
- [ ] Posture model triggers warning on deliberate bad form
- [ ] Dehydration alert fires after treadmill run
- [ ] Power screen shows live TEG mW
- [ ] Cover solar panels → system continues on TEG alone (showcase demo moment)

### Demo script (from PDF — expanded)
1. Wear suit. Show Samsung Health recognised the device (Accessory SDK integration)
2. Live ECG waveform on phone screen
3. SpO2 + HR updating
4. 10 bicep curls → rep counter on screen, form score shown
5. Cover solar panels → system keeps running on body heat only
6. Power panel shows: TEG generating X mW from skin ΔT
7. After 15-min run → dehydration risk changes to "Medium"
8. Tilt forward deliberately → posture warning fires

### Pitch line
"The suit is powered entirely by body heat via thermoelectric generation — proven in Nature (2023) to run BLE sensing at ΔT as small as 4°C — and monitored end-to-end through Samsung Health. ML models running on-device classify your ECG, count your reps, grade your form, and flag fatigue and dehydration in real time. No battery. No charging. One suit."

---

## Timeline Summary

| Phase | Weeks | Owner |
|-------|-------|-------|
| 0 — Samsung SDK setup | 1 | Pranay |
| 1 — Power bench test | 1–2 | Reman |
| 2 — Sensor validation | 1–2 | Ariyan |
| 3 — BLE GATT firmware | 2–3 | Pranay + Ariyan |
| 4 — Android app | 2–3 | Pranay |
| 5 — ML models | 3–4 (overlaps 4) | Pranay + team |
| 6 — Suit embedding | 2 | Ariyan |
| 7 — Testing + showcase | 1–2 | Full team |
| **Total** | **~10–12 weeks** | |

---

## Key Links

- Samsung Health overview: https://developer.samsung.com/health
- Accessory SDK docs: https://developer.samsung.com/health/accessory/overview.html
- Accessory device specs (GATT compliance): https://developer.samsung.com/health/accessory/device-specs.html
- Data SDK Partner App Program: https://developer.samsung.com/health/data/process.html
- Health Code Lab: https://developer.samsung.com/codelab#Health
- nRF5340 DK: https://www.nordicsemi.com/Products/nRF5340
- PTB-XL ECG dataset: https://physionet.org/content/ptb-xl/
- MIT-BIH Arrhythmia dataset: https://physionet.org/content/mitdb/
