# Three-Musketeers
# 🩱 Smart Workout Suit
> Self-powered biometric sensing suit — no battery, no charging, full health monitoring.

**Team:** Pranay · Ariyan · Reman Dey  
**Institution:** IIT Mandi  
**Version:** 1.0 — Prototype Stage  
**Date:** June 2026

---

## What is this?

A compression workout suit (shirt + shorts) that monitors your body in real time and streams data to your phone — powered entirely by your own body heat, sunlight, and footsteps.

No charging. No battery pack. No cables.

The suit harvests energy from skin heat via a **Thermoelectric Generator (TEG)**, supplemented by flexible solar panels on the shoulders and a piezoelectric membrane in the shoe sole. A BLE 5.x radio transmits live sensor data to an Android app that integrates with **Samsung Health** and runs **on-device ML models** to detect health conditions, count reps, grade form, flag dehydration, and warn about overexertion.

---

## Features

**Biometrics monitored**
- ECG waveform (AD8232) — real-time, 256 Hz
- SpO2 + heart rate (MAX30102)
- Blood pressure — estimated via PPG
- Skin temperature (TMP117)
- Sweat / humidity (SHT40)
- Respiratory rate — derived from ECG + IMU

**Workout intelligence**
- Rep counter (bicep curl, squat, etc.) — LSTM on IMU data
- Form scorer — posture quality [0–10] per rep
- Core stability monitoring — lumbar IMU
- Arm motion analysis — bilateral elbow IMUs

**ML health alerts**
- ECG anomaly detection: Normal / AFib / Tachycardia / Bradycardia
- Dehydration risk: Low / Medium / High
- Overexertion / fatigue: Safe / Caution / Stop

**Platform**
- Samsung Health integration — HR, SpO2, BP, Temp written to Health history
- "Works With Samsung Health" BLE Accessory SDK compliance
- Live power dashboard — TEG mW, solar mW, supercap charge level

---

## Research Basis

This prototype is directly backed by two peer-reviewed papers:

| Paper | Key result | Relevance |
|-------|------------|-----------|
| Khan et al., ScienceDirect 2025 | Flexible TEG (2.26 mm) + radiative cooling powers BLE sensor module from skin heat alone, 3.51 µW/cm² | Proves TEG can replace electricity-generating fabric |
| Yin et al., Nature/PMC 2023 | TEG + energy management IC powers BLE chipset at ΔT as low as 4 K; 1.6 s sense-transmit cycle, battery-free | Proves burst-sleep architecture works in warm climates like India |

---

## Hardware

### Power subsystem

| Component | Part | Role | Cost (INR) |
|-----------|------|------|------------|
| TEG module × 2 | TEC1-12706 | Primary — skin heat → electricity | 600 |
| Flexible solar panel × 2 | DFRobot FIT0333 | Supplementary — shoulder mount | 2,400 |
| Piezo membrane | Generic shoe insert | Supplementary — footstep energy | 400 |
| Boost converter IC | TPS61220 / MAX17222 | Step up 30–100 mV → 3.3 V | 300 |
| Supercapacitor | 470 µF | Energy buffer | 100 |
| Backup LiPo | 100 mAh flat cell | Cold-start failsafe | 250 |

### MCU + BLE

| Stage | Chip | Why |
|-------|------|-----|
| Prototype | ESP32-C3 | Cheap, widely available in India, BLE 5.0 |
| Final suit | nRF5340 | BLE 5.3, sleeps at ~1 µA, matches Nature 2023 architecture |

### Sensor suite

| Parameter | Chip | Interface | Placement |
|-----------|------|-----------|-----------|
| ECG | AD8232 | Analog ADC | Chest (dry-contact electrodes) |
| SpO2 + HR | MAX30102 | I²C (0x57) | Wrist cuff |
| IMU × 3 | MPU-6050 / ICM-42688 | I²C via TCA9548A mux | Left elbow, right elbow, lumbar |
| Skin temp | TMP117 | I²C (0x48) | Waistband |
| Humidity / sweat | SHT40 | I²C (0x44) | Shirt inner lining |

> **Note:** Three MPU-6050s share I²C address 0x68. A TCA9548A multiplexer gives each its own channel. Required — do not skip.

---

## System Architecture

```
[Body heat + footsteps + sunlight]
          │ energy harvest
          ▼
 TEG + Piezo + Solar ──► Boost converter ──► Supercap 470µF
                                                    │ 3.3V burst (hysteretic)
                                                    ▼
 AD8232 ──────────────────────────────────► nRF5340 / ESP32-C3
 MAX30102  ── I²C ─────────────────────────►   (GATT server)
 MPU-6050×3 ─ I²C (TCA9548A) ─────────────►   BLE 5.x
 TMP117  ─── I²C ─────────────────────────►       │
 SHT40  ──── I²C ─────────────────────────►       │ ~1.6s cycle
                                                    ▼
                                           Android phone app
                                        ┌───────────────────┐
                                        │ BLE GATT client   │
                                        │ Samsung Health SDK│──► Samsung Health
                                        │ TFLite ML engine  │──► Alerts + UI
                                        │ Jetpack Compose   │
                                        └───────────────────┘
```

Full architecture → see [`architecture.md`](./architecture.md)  
Dev workflow → see [`workflow.md`](./workflow.md)

Product roadmap → see [`docs/product-roadmap.md`](./docs/product-roadmap.md)  
One-month showcase plan → see [`docs/showcase-plan.md`](./docs/showcase-plan.md)  
Android app scaffold → see [`apps/android`](./apps/android)

---

## Software

### Android App — module structure

```
smart-suit-app/
├── app/src/main/java/com/smartsuit/
│   ├── ble/              # BLE scan, GATT client, frame parser
│   ├── samsung/          # Samsung Health Data SDK wrapper
│   ├── ml/               # TFLite model runners (ECG, IMU, tabular)
│   └── ui/               # Jetpack Compose screens
├── model/                # .tflite model files
└── build.gradle
```

### ML Models

| Model | Input | Output | Algorithm |
|-------|-------|--------|-----------|
| ECG anomaly | 256-sample ECG window | Normal / AFib / Tachy / Brady | 1D-CNN |
| Rep counter | 2s IMU window (400×6) | Rep count | LSTM |
| Form scorer | 2s IMU window (400×6) | Score [0–10] | LSTM → regression |
| Dehydration risk | Sweat rate, skin temp, HR | Low / Medium / High | Random Forest |
| Overexertion | HR reserve, SpO2, RR, IMU intensity | Safe / Caution / Stop | XGBoost |
| BP estimation | PPG waveform features | Systolic / Diastolic mmHg | CNN + regression |

All models run **on-device** via TensorFlow Lite. No server required.

### Samsung Health Integration

Uses two Samsung Health SDKs:

**Accessory SDK** — registers the suit as a BLE health device in Samsung Health. Your GATT services must match Samsung's spec exactly. Standard SIG services (HR, SpO2, BP, Temp) are auto-recognised.

**Data SDK** — writes health readings into the user's Samsung Health history from the Android app. Requires Partner App Program approval from Samsung.

Reference: https://developer.samsung.com/health

---

## BLE GATT Profile (suit as peripheral)

```
Generic Access (0x1800)
  Device Name = "SmartSuit_v1"

Battery Service (0x180F)
  Battery Level [0–100%] ← supercap voltage derived

Heart Rate Service (0x180D)
  HR Measurement [0x2A37] ← MAX30102

PLX / SpO2 Service (0x1822)
  PLX Continuous Measurement [0x2A5F] ← MAX30102

Blood Pressure Service (0x1810)
  BP Measurement [0x2A35] ← PPG estimated

Health Thermometer (0x1809)
  Temperature Measurement [0x2A1C] ← TMP117

Custom SmartSuit Service
  ECG_RAW      float32[256]    raw ECG window
  IMU_ELBOW_L  float32[6]      ax,ay,az,gx,gy,gz
  IMU_ELBOW_R  float32[6]
  IMU_LUMBAR   float32[6]
  HUMIDITY     float32[2]      %RH, temp_C
  RESP_RATE    float32         breaths/min
  POWER_MW     float32         live TEG output
```

---

## Build Phases

| Phase | What | Duration | Owner |
|-------|------|----------|-------|
| 0 | Samsung SDK registration + Android scaffold | 1 week | Pranay |
| 1 | Power bench test — TEG → cap → 3.3 V without external supply | 1–2 weeks | Reman |
| 2 | Sensor validation — each chip tested independently on Arduino | 1–2 weeks | Ariyan |
| 3 | BLE GATT firmware on nRF5340/ESP32-C3 | 2–3 weeks | Pranay + Ariyan |
| 4 | Android app — BLE client + Samsung Health + dashboard | 2–3 weeks | Pranay |
| 5 | ML model training + TFLite conversion + integration | 3–4 weeks | Pranay + team |
| 6 | Suit embedding — flex PCB, sewing, waterproof connectors | 2 weeks | Ariyan |
| 7 | Integration testing + showcase prep | 1–2 weeks | Full team |

---

## Prototype Cost

| Category | Cost (INR) |
|----------|------------|
| Power subsystem | 3,950 |
| MCU (nRF5340 dev board) | 1,800 |
| Sensors | 2,500 |
| Interconnect + PCB + mux | 800 |
| Suit base (shirt + shorts) | 1,500 |
| Piezo shoe insert | 400 |
| Misc (solder, flex, tape) | 450 |
| **Total** | **~11,400** |

Budget ceiling: ₹12,000 ✓

---

## Team

| Member | Responsibility |
|--------|----------------|
| **Pranay** | BLE firmware (nRF5340 / ESP32-C3), Android app, phone dashboard UI |
| **Ariyan** | Sensor validation (ECG, IMU, SpO2), PCB layout, suit embedding |
| **Reman Dey** | TEG skin contact optimisation, boost converter circuit, power benchmarking |

---

## References

1. Khan et al., "Flexible thermoelectric generator with radiative cooling for body-heat-driven self-powered BLE sensing system," Nano Energy / ScienceDirect, 2025. DOI: S2214993725004087
2. Yin et al., "Flexible thermoelectric generator and energy management electronics powered by body heat," Microsystems & Nanoengineering / Nature, August 2023. PMC10030726
3. Samsung Health Accessory SDK — https://developer.samsung.com/health/accessory
4. Samsung Health Data SDK — https://developer.samsung.com/health/data
5. PTB-XL ECG dataset — https://physionet.org/content/ptb-xl
6. MIT-BIH Arrhythmia dataset — https://physionet.org/content/mitdb

---

*Smart Workout Suit — IIT Mandi · June 2026*
