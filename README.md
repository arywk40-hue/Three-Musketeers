# Three-Musketeers
# ElderCare Guardian
> Elderly safety and health monitoring wearable — vitals, fall detection, SOS, and caregiver alerts.

**Team:** Pranay · Ariyan · Reman Dey  
**Institution:** IIT Mandi  
**Version:** 1.0.0-beta — Pre-Release  
**Date:** June 2026

---

## What is this?

ElderCare Guardian is a compact wrist/clip wearable for elderly people living alone or needing light daily monitoring. It streams vitals and motion data to a phone, detects possible falls or distress, and can alert caregivers.

The revised prototype intentionally avoids smart fabric and suit embedding. That makes the build simpler, faster, and more impactful for a showcase: one wearable device, one Android app, one caregiver-focused safety story.

The existing BLE, Samsung Health, simulator, GATT parsing, and Android dashboard work still applies. The product focus changes from fitness intelligence to elderly safety, health trends, and caregiver peace of mind.

---

## Features

**Biometrics monitored** *(showcase — real)*
- SpO2 + heart rate (MAX30102) ✅
- IMU 6-axis motion (MPU-6050) ✅
- Battery voltage (ESP32-C3 ADC) ✅

**Biometrics monitored** *(future)*
- ECG waveform (AD8232) — deferred
- Blood pressure — estimated via PPG — **clinically invalid, removed from display**
- Skin temperature (TMP117) — optional
- Sweat / humidity (SHT40) — deferred

**Elder safety**
- Fall detection — IMU-based impact + posture change ✅
- SOS trigger — app and device-side emergency event ✅
- Inactivity monitoring — no movement for unusual duration ✅
- Caregiver alert state — Normal / Check / Warning / Emergency ✅

**Health alerts**
- ECG anomaly detection: Normal / AFib / Tachycardia / Bradycardia ✅
- Dehydration risk: Low / Medium / High ✅
- Fall risk: Low / Medium / High ✅
- Abnormal vitals: Safe / Monitor / Alert ✅

**Platform**
- Samsung Health integration — HR, SpO2, Temp written to Health history (BP removed)
- "Works With Samsung Health" BLE Accessory SDK compliance
- Caregiver dashboard — live status, alert level, BLE readiness, and health trend ✅
- DPDPA consent flow — mandatory first-launch consent under India's Digital Personal Data Protection Act 2023 ✅
- Foreground Service for 24/7 background BLE monitoring ✅
- BLE auto-reconnect with exponential backoff ✅
- Room database with SQLCipher encryption ✅

---

## Revised Prototype

The first prototype is intentionally simple:

- ESP32-C3 BLE wearable (nRF5340 deferred to production).
- MAX30102 for heart rate and SpO2.
- MPU-6050 IMU for fall, inactivity, and posture state.
- Optional TMP117 for temperature.
- Android app with simulator mode, BLE mode, caregiver alert flow, and Samsung Health path.
- Normal rechargeable battery or USB power for showcase reliability.

The earlier TEG, solar, piezo, and smart fabric work is deferred. It remains research inspiration, but it is not required for the elderly-care showcase.

---

## Hardware

### MCU + BLE

| Stage | Chip | Why | Status |
|-------|------|-----|--------|
| Prototype | ESP32-C3 | Cheap, widely available in India, BLE 5.0 | ✅ Firmware complete |
| Final wearable | nRF5340 | BLE 5.3, sleeps at ~1 µA, better for always-on monitoring | ⏳ Deferred |

### Sensor suite

| Parameter | Chip | Interface | Placement |
|-----------|------|-----------|-----------|
| SpO2 + HR | MAX30102 | I²C (0x57) | Wrist/clip skin contact |
| IMU | MPU-6050 / ICM-42688 | I²C | Wrist/clip enclosure |
| Skin temp | TMP117 | I²C (0x48) | Optional skin contact |
| SOS input | Button / capacitive touch | GPIO | Wearable body |
| ECG | AD8232 | Analog ADC | Later clinical extension |

---

## System Architecture

```
MAX30102 + IMU + SOS button + optional TMP117
        │ I²C / GPIO
        ▼
ESP32-C3 / nRF5340 wearable
        │ BLE 5.x GATT
        ▼
Android phone app
 ├── live vitals
 ├── fall/SOS/inactivity alerts
 ├── caregiver status
 └── Samsung Health history path
```

Full architecture → see [`architecture.md`](./architecture.md)  
Dev workflow → see [`workflow.md`](./workflow.md)

Elder care pivot → see [`docs/elder-care-pivot.md`](./docs/elder-care-pivot.md)  
Product roadmap → see [`docs/product-roadmap.md`](./docs/product-roadmap.md)  
One-month showcase plan → see [`docs/showcase-plan.md`](./docs/showcase-plan.md)  
Android app scaffold → see [`apps/android`](./apps/android)

---

## Software

### Android App — module structure

```
eldercare-guardian-app/
├── app/src/main/java/com/eldercareguardian/
│   ├── ble/              # BLE scan, GATT client, frame parser
│   ├── consent/          # DPDPA consent screen and DataStore
│   ├── samsung/          # Samsung Health Data SDK wrapper
│   ├── ml/               # Rule-based engines (ECG, IMU, vitals)
│   ├── database/         # Room + SQLCipher encrypted persistence
│   ├── notifications/    # Caregiver alert dispatcher
│   ├── service/          # Foreground service for background monitoring
│   └── ui/               # Jetpack Compose screens
├── model/                # .tflite model files (deferred)
└── build.gradle.kts
```

### ML Models *(all rule-based for showcase; TFLite models deferred)*

| Model | Status | Algorithm |
|-------|--------|-----------|
| Fall detection | ✅ Rules (FallDetectionEngine + FallConfirmationBuffer) |
| Caregiver alert triage | ✅ Rules (CaregiverAlertPolicy) |
| Inactivity monitoring | ✅ Rules (InactivityMonitor) |
| ECG anomaly | ✅ Rules (EcgAnomalyDetector + HeartRateExtractor) — **fixed RMSSD logic** |
| Dehydration risk | ✅ Rules (DehydrationRiskModel) |
| Vitals risk | ✅ Rules (VitalsRiskMonitor) |
| Overexertion | ✅ Rules (OverexertionModel) |
| BP estimation | ❌ **Removed from display — clinically invalid** |
| TFLite models (1D-CNN, XGBoost, etc.) | ⏳ No model files exist — see `ml/README.md` |

### Samsung Health Integration *(path designed, not yet active)*

Designed for two Samsung Health SDKs, both requiring partner approval from Samsung:

**Accessory SDK** — GATT spec compliance. Standard SIG services (HR 0x180D, PLX 0x1822) are auto-recognised. Custom services need app-side bridging.

**Data SDK** — writes health readings into Samsung Health history. Local AAR not yet in `libs/`. Current state: `SamsungHealthState.NeedsPartnerApproval`.

Reference: https://developer.samsung.com/health

---

## BLE GATT Profile (wearable as peripheral)

```
Generic Access (0x1800)
  Device Name = "ElderCare_v1"

Battery Service (0x180F)
  Battery Level [0–100%] ← LiPo voltage derived via ADC

Heart Rate Service (0x180D)
  HR Measurement [0x2A37] ← MAX30102

PLX / SpO2 Service (0x1822)
  PLX Continuous Measurement [0x2A5F] ← MAX30102 (only when valid)

Health Thermometer (0x1809)
  Temperature Measurement [0x2A1C] ← TMP117 (optional)

Custom ElderCare Service (12345678-1234-5678-1234-567812345678)
  ECG_RAW       float32[256]    synthetic QRS-spike ECG window
  IMU_WRIST     float32[6]      ax,ay,az,gx,gy,gz (m/s², °/s)
  SOS_STATE     uint8           0=off 1=active
  FALL_RISK     float32         0.0–1.0 (firmware: SOS-based, Android: IMU-based)
  HUMIDITY      float32[2]      %RH, temp_C (synthetic)
  RESP_RATE     float32         breaths/min (synthetic)
  DEVICE_STATE  uint8           0=Normal 1=Check 2=Urgent
```

---

## Build Phases

| Phase | What | Duration | Owner |
|-------|------|----------|-------|
| 0 | Samsung SDK registration + Android scaffold | 1 week | Pranay |
| 1 | Wearable bench test — ESP32-C3 + MAX30102 + IMU | 1 week | Reman + Ariyan |
| 2 | Sensor validation — HR/SpO2, IMU fall gesture, SOS button | 1–2 weeks | Ariyan |
| 3 | BLE GATT firmware on ESP32-C3 | 2–3 weeks | Pranay + Ariyan |
| 4 | Android app — BLE client + Samsung Health + dashboard | 2–3 weeks | Pranay |
| 5 | Rule alerts first, ML model training later | 2–4 weeks | Pranay + team |
| 6 | Wearable enclosure — wrist/clip prototype | 1–2 weeks | Ariyan |
| 7 | Integration testing + showcase prep | 1–2 weeks | Full team |

**Completed:** Phases 0-4 core functionality ✅ | **Showcase-ready** as of June 2026

---

## Prototype Cost

| Category | Cost (INR) |
|----------|------------|
| MCU + BLE | 600–1,800 |
| MAX30102 | 250–500 |
| IMU | 150–500 |
| Optional TMP117/temp | 300–600 |
| SOS button + enclosure | 300–800 |
| Battery / power | 300–700 |
| Misc | 500 |
| **Total** | **~2,400–5,400** |

Budget ceiling: ₹12,000 ✓

---

## Team

| Member | Responsibility |
|--------|----------------|
| **Pranay** | BLE firmware, Android app, caregiver dashboard UI |
| **Ariyan** | HR/SpO2 + IMU validation, PCB layout, wearable enclosure |
| **Reman Dey** | Prototype hardware assembly, power/battery benchmarking, enclosure reliability |

---

## References

1. Khan et al., "Flexible thermoelectric generator with radiative cooling for body-heat-driven self-powered BLE sensing system," Nano Energy / ScienceDirect, 2025. DOI: S2214993725004087
2. Yin et al., "Flexible thermoelectric generator and energy management electronics powered by body heat," Microsystems & Nanoengineering / Nature, August 2023. PMC10030726
3. Samsung Health Accessory SDK — https://developer.samsung.com/health/accessory
4. Samsung Health Data SDK — https://developer.samsung.com/health/data
5. PTB-XL ECG dataset — https://physionet.org/content/ptb-xl
6. MIT-BIH Arrhythmia dataset — https://physionet.org/content/mitdb

---

*ElderCare Guardian — IIT Mandi · June 2026*
