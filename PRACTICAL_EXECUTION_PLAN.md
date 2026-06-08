# ElderCare Guardian — ₹10,000 / 30-Day Practical Execution Plan

**Mission:** Impress judges at IIT Mandi hackathon with a working demo.  
**Budget:** ₹10,000 hard cap  
**Timeline:** 30 days  
**Team:** 3 people  

---

## The Honest Assessment

Your README contains several features that are **impossible to demonstrate reliably in 30 days** on a student budget:

| Feature | Why it's out |
|---|---|
| BP estimation via PPG | Requires calibration dataset, ML training, and clinical validation. No PPG-to-BP model produces trustworthy results without months of data collection. |
| ECG classification (AFib, 1D-CNN) | Requires a trained TFLite model from PTB-XL/MIT-BIH. Training + validating + deploying takes weeks of compute time. You have no model file. |
| Dehydration prediction | SHT40 sensor measures ambient humidity, not sweat rate. The feature is architecturally unsound. |
| Samsung Health certification | Requires partner approval (4–8 week process) and a physical Samsung device for testing. |
| nRF5340 firmware | Zephyr RTOS learning curve + nRF5340 hardware cost (~₹3,000) + limited availability. Stick with ESP32-C3. |
| AD8232 ECG waveform | AD8232 requires proper electrode placement and lead wires. Unreliable in a 30-day wristband prototype. Real ECG from a wrist wearable is extremely challenging. |
| Dehydration / Sweat monitoring (SHT40) | Sensor not available, feature not validated, adds complexity without demo value. |

**Drop all of the above. You will not miss them at a hackathon.**

---

## What to Keep (Your Core Demo Story)

```
Elderly person wears device → falls → alert fires → caregiver sees it → calls them
```

That's it. That's the entire story judges need to hear. Everything else is noise.

**Keep:**
1. ESP32-C3 + MAX30102 (HR + SpO2) — real biometrics
2. MPU-6050 (IMU) — real fall detection
3. SOS button (GPIO 9) — panic button story
4. Android app — caregiver dashboard
5. Simulator mode — demo reliability

**The one-sentence pitch:** "We built a ₹500 wearable that texts your family when you fall."

---

## What Hardware to Buy

### Mandatory (₹3,000–3,500 total)

| Item | Qty | Cost | Buy from |
|---|---|---|---|
| ESP32-C3 dev board (USB-C) | 2 | ₹600 | Robu.in / Robocraze |
| MAX30102 pulse oximeter module | 2 | ₹600 | Robu.in |
| MPU-6050 IMU module | 2 | ₹400 | Robu.in |
| Large tactile push button | 3 | ₹60 | Robu.in / local |
| 1000 mAh LiPo battery | 1 | ₹350 | Robu.in |
| TP4056 charging module | 1 | ₹80 | Robu.in |
| Jumper wires, breadboard | — | ₹200 | Robu.in |
| USB-C cables (data, not charge-only) | 2 | ₹300 | Amazon |
| Velcro wrist strap | 1 | ₹150 | Amazon |
| **Total** | | **~₹2,740** |

### Optional — Only if time permits (₹500–1,000 extra)

| Item | Cost | Notes |
|---|---|---|
| TMP117 temperature sensor | ₹400 | Good demo value — temperature display on screen |
| 3D printed enclosure (outsource) | ₹300–500 | Makes it look professional |
| Small buzzer / vibration motor | ₹100 | Feedback on fall detection — great demo effect |

### Do NOT Buy
- AD8232 ECG module — too complex to demo reliably in 30 days
- SHT40 humidity sensor — dehydration model is unsound
- nRF5340 — stick with ESP32-C3
- TCA9548A multiplexer — you only need one IMU, not three

---

## What Firmware to Build

**Keep it dead simple. Flash the existing `firmware/esp32-c3/main.ino` with minor tweaks.**

### Remove from firmware:
- ECG_RAW characteristic (256 float32 = 1024 bytes per notify — overkill, complex, ECG is synthetic anyway)
- HUMIDITY characteristic (no SHT40 sensor)
- RESP_RATE (synthetic, adds no value)

### Keep in firmware:
- Battery Service (0x180F) — real voltage ADC
- Heart Rate Service (0x180D) — real MAX30102 data
- PLX SpO2 (0x1822) — real MAX30102 data
- Custom Service with:
  - IMU_WRIST (6 floats) — real MPU-6050 data
  - SOS_STATE (uint8) — real GPIO 9 button
  - FALL_RISK (float) — computed on Android from IMU stream
  - DEVICE_STATE (uint8) — Normal/Check/Emergency

### Simplify loop() to:
```cpp
// 1. Read MAX30102 → update HR, SpO2
// 2. Read MPU-6050 → update IMU 6-axis
// 3. Read GPIO 9 → SOS state
// 4. Read ADC battery → battery %
// 5. BLE notify all characteristics
// 6. Watchdog kick
// 7. delay(500) — 2 Hz is fine
```

**Estimated firmware work: 4 hours of cleanup. The firmware already works.**

---

## What Android Screens to Build

### Screen 1 — Live Dashboard (The Money Screen)
This is the screen judges will look at. Make it look stunning.

```
┌─────────────────────────────────────┐
│  ❤️ 74 bpm          🫁 97%          │
│  Heart Rate          SpO2           │
│                                     │
│  🌡️ 36.5°C          🔋 82%          │
│  Temperature         Battery        │
│                                     │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│  🟢 NORMAL — All vitals in range    │
│  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│                                     │
│  [  🔴 TRIGGER SOS DEMO  ]         │
└─────────────────────────────────────┘
```

### Screen 2 — Emergency Alert (The Drama Screen)
This appears when SOS or fall is detected. Judges will remember this.

```
┌─────────────────────────────────────┐
│  🚨 🚨 🚨 EMERGENCY 🚨 🚨 🚨       │
│                                     │
│  FALL DETECTED                      │
│  Caregiver Pranay has been alerted  │
│                                     │
│  ⏱️ 00:23 since event               │
│                                     │
│  [  📞 CALL CAREGIVER  ]           │
│  [  ✅ I'm OK — Cancel  ]          │
└─────────────────────────────────────┘
```

### Screen 3 — BLE Status (The Credibility Screen)
Shows real hardware is connected.

```
┌─────────────────────────────────────┐
│  Device: ElderCare_v1              │
│  Status: 🟢 Connected              │
│  Signal: ████████░░ -58 dBm        │
│  Battery: ████████████ 82%         │
└─────────────────────────────────────┘
```

**Do NOT build:** Historical trends chart, detailed ECG view, Samsung Health sync, dehydration panel. These add no demo value and take development time.

---

## What ML to Postpone

**Postpone everything. Use rules only.**

| ML Feature | Postpone until |
|---|---|
| 1D-CNN ECG anomaly detection | Post-hackathon, needs training data |
| TFLite fall detection | Post-hackathon, needs labelled dataset |
| Dehydration Random Forest | Post-hackathon, sensor is wrong anyway |
| BP estimation regression | Never — clinically invalid |
| Overexertion XGBoost | Post-hackathon |

**What to use instead:** The existing rule-based engines are correct for a demo:
- `FallDetectionEngine` (Phase 5 version) — temporal window approach
- `CaregiverAlertPolicy` (Phase 7 version) — 4-level rules

For the judges, say: "We've designed the ML pipeline and the rule engine is live. We're collecting training data now. The TFLite models will replace the rules in month 2."

---

## What to Simulate

**Simulate anything that makes the demo cleaner.**

| What | Simulate how |
|---|---|
| Fall event | "TRIGGER FALL DEMO" button in app — instantly sets fall risk to High |
| SOS button | On-screen button + GPIO 9 physical button |
| SpO2 dip (demo) | In simulator, briefly drop SpO2 to 88% to show alert trigger |
| Night-time alert | Time jump in simulator — show 3 AM 5-minute inactivity triggers Warning |
| Caregiver SMS | Show the message you would send (hardcoded string) — actual SMS needs SEND_SMS permission approval |

**Never simulate actual sensor readings without labeling them.** Judges ask "is that real data?" — your answer should always be honest. "This is our demo mode — the hardware behind me is streaming real HR."

---

## 30-Day Sprint Plan (Ultra-Simplified)

### Week 1: Make hardware stream real data
- Flash firmware to ESP32-C3
- Wire MAX30102 + MPU-6050 on breadboard
- Verify with nRF Connect app on phone
- Single goal: see real HR bpm from MAX30102 in nRF Connect

### Week 2: Connect Android app to hardware
- Open `apps/android` in Android Studio, build successfully
- Scan for `ElderCare_v1`, connect, see HR number update in real time
- That's it. One real number from hardware = 90% of demo credibility.

### Week 3: Make the emergency flow stunning
- Fall demo button → dramatic Emergency screen
- SOS button (hardware GPIO 9) → same Emergency screen
- "Call Caregiver" button → dials the phone
- Record demo video as backup

### Week 4: Polish and rehearse
- Make the UI look premium (colors, large fonts, animations)
- Rehearse the demo script 10 times
- Freeze the branch. No new features.

---

## The Demo in 90 Seconds

1. "This is ElderCare Guardian. [Show phone with live HR 74 bpm]"
2. "The wearable sends real heart rate and oxygen levels to the caregiver's phone in real time."
3. [Shake the hardware / trigger fall demo] "When it detects a fall..." [screen turns red] "...the caregiver is alerted immediately."
4. "If the elderly person is conscious, they press SOS." [press button] "The caregiver gets a call."
5. "We built this for ₹500 in hardware. It works without a subscription or internet in most cases."
6. "Questions?"

**That's the whole demo. Don't add more. Less is more.**
