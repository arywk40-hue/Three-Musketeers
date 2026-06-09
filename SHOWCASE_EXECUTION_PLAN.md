# ElderCare Guardian — IIT Mandi Showcase Execution Plan

**Event:** IIT Mandi Hackathon / Demo Day  
**Deadline:** 30 days from today (June 2026)  
**Team:** Pranay · Ariyan · Reman Dey  
**Budget:** ₹10,000

---

## Mission

Demonstrate a working, visually compelling elderly safety wearable to judges in 3–5 minutes. The demo should reliably show: live vitals, fall detection, SOS alert, and caregiver notification — with or without hardware.

---

## The #1 Rule for Showcases

**The demo must work every time, with or without hardware.** Always have simulator mode as a fallback. Hardware is a bonus.

---

## Must Have / Should Have / Nice To Have

### Must Have ✅
- [x] Android app running on a real phone with simulator data
- [x] Live HR + SpO2 display updating in real time
- [x] Fall event demo that triggers emergency alert on screen
- [x] SOS button demo (tap on screen or physical hardware)
- [x] Caregiver alert screen showing Emergency state
- [x] Working BLE scan on the phone (even if hardware unavailable)
- [x] ESP32-C3 hardware advertising as `ElderCare_v1`
- [x] 1 real sensor streaming (HR from MAX30102 OR IMU)

### Should Have 🎯
- [x] Both MAX30102 + MPU-6050 streaming over BLE
- [x] Fall detection triggering on hardware IMU (drop the device) — `FallDetectionEngine` + `FallConfirmationBuffer`
- [x] SOS button on hardware wired to GPIO 9
- [ ] Physical wristband enclosure (3D printed or cardboard prototype)
- [x] Alert history timeline in app (showing previous events)
- [x] Battery percentage display from real ADC
- [x] Samsung Health deployment story explained in slides — `NeedsPartnerApproval` state + UI panel

### Nice To Have 💡
- [ ] Temperature sensor (TMP117) streaming
- [x] ECG waveform (synthetic is fine) — `ECG_RAW` characteristic with QRS-spike synthetic
- [ ] Night-time mode demonstrated
- [ ] Alert SMS demo (to a test phone number)
- [ ] Slide deck with system architecture diagram

---

## Week-by-Week Roadmap

### Week 1 (Days 1–7) — Hardware Assembly + Firmware
**Owner: Ariyan + Reman**

**Goals:**
- ESP32-C3 dev board + MAX30102 breadboard circuit working
- MPU-6050 on the same I²C bus
- SOS button on GPIO 9
- Firmware flashed, Serial Monitor shows sensor readings
- BLE advertising confirmed with nRF Connect app on phone

**Day-by-day:**

| Day | Task |
|---|---|
| 1 | Order/verify components: ESP32-C3, MAX30102, MPU-6050, push button, LiPo, USB cable |
| 2 | Wire breadboard: I²C SDA=GPIO6, SCL=GPIO7, MAX30102 at 0x57, MPU-6050 at 0x68 |
| 3 | Flash `firmware/esp32-c3/main.ino`. Verify Serial Monitor shows sensor readings |
| 4 | Test BLE with nRF Connect app — confirm HR + IMU notifications arrive |
| 5 | Wire SOS button GPIO 9. Verify SOS_STATE characteristic toggles |
| 6 | Battery divider circuit on GPIO 4. Calibrate rough voltage reading |
| 7 | **Checkpoint:** Hardware demo works standalone. Record Serial Monitor video as backup |

**Deliverable:** Hardware advertises `ElderCare_v1`, sends real HR + IMU + SOS over BLE.

---

### Week 2 (Days 8–14) — Android App Polish
**Owner: Pranay**

**Goals:**
- Android app builds and runs on a real phone (not emulator)
- BLE connect to `ElderCare_v1` works end-to-end
- All 4 tabs (Vitals, Safety, Caregiver, Readiness) look polished
- Fall demo flow works: trigger → alert → screen changes to Emergency
- SOS demo works: button → Emergency alert

**Day-by-day:**

| Day | Task |
|---|---|
| 8 | Open `apps/android` in Android Studio. Build + run on physical device |
| 9 | Fix any build errors. Verify simulator dashboard runs smoothly |
| 10 | Test BLE scan → connect → data display with hardware from Week 1 |
| 11 | Polish Vitals tab: large readable numbers, ECG waveform animation |
| 12 | Polish Safety tab: fall risk meter, SOS button prominently displayed |
| 13 | Polish Caregiver tab: large alert level badge, timestamp, "Call Caregiver" button |
| 14 | **Checkpoint:** Full demo script runs on real phone + hardware in under 5 min |

**Deliverable:** Demo-ready Android APK on Pranay's phone.

---

### Week 3 (Days 15–21) — Enclosure + Integration
**Owner: All three**

**Goals:**
- Physical enclosure for wearable (even cardboard + tape is fine for demos)
- End-to-end demo rehearsed 5+ times
- Backup plan tested (simulator mode works if hardware dies)
- Slide deck complete

**Day-by-day:**

| Day | Task |
|---|---|
| 15 | Design simple 3D-printed or cardboard wristband housing for ESP32-C3 + sensors |
| 16 | Mount components, route wires, test that fall drop still registers through enclosure |
| 17 | Run full demo rehearsal: all 3 team members present their section |
| 18 | Record a 3-minute demo video (backup for if hardware fails during presentation) |
| 19 | Create slide deck: problem → solution → hardware → software → ML → demo |
| 20 | Practice Q&A: common judge questions and your responses |
| 21 | **Checkpoint:** Full rehearsal done. Video recorded. Slides ready |

**Deliverable:** Polished demo package — hardware + APK + slides + backup video.

---

### Week 4 (Days 22–30) — Buffer, Polish, and Freeze
**Owner: All three**

**Goals:**
- No new features. Fix only bugs that affect the demo.
- Freeze the demo branch.
- Rehearse daily.

**Day-by-day:**

| Day | Task |
|---|---|
| 22–24 | Bug fix sprint: only fix issues that break the demo flow |
| 25 | Final polish: make sure app looks great on the specific phone used for demo |
| 26 | Freeze `demo` branch. No more commits. APK built from frozen branch. |
| 27 | Travel/logistics prep. Charge all devices. Backup APK on USB drive. |
| 28 | Final full rehearsal at venue if possible |
| 29–30 | Rest + demo day |

**Deliverable:** Frozen, reliable demo package. Backup APK on USB.

---

## Demo Script (5-minute version)

### Opening (30 s)
"ElderCare Guardian is a wearable safety system for elderly people living independently or with a caregiver. It monitors vitals, detects falls, and sends real-time alerts to family members."

### Live Demo (3 min)
1. Show phone running ElderCare Guardian app. Point to HR (75 bpm), SpO2 (97%).
2. Connect to `ElderCare_v1` hardware — BLE status changes to Connected.
3. Show real HR value updating from MAX30102.
4. **Fall demo:** Drop the device (or trigger in simulator) — Safety screen shows HIGH RISK.
5. Show alert escalation to Emergency on the Caregiver tab.
6. Show "Call Caregiver" button and explain SMS alert path.
7. **SOS demo:** Press button on hardware — immediate Emergency alert.
8. Show alert history timeline with timestamps.

### Technical Slide (1 min)
- Architecture diagram: Sensors → ESP32-C3 → BLE → Android → Caregiver
- Mention: TFLite path ready for ML models, Samsung Health integration designed

### Close (30 s)
"We're a team of 3 from IIT Mandi. We built this in 30 days with ₹10,000. The system is designed around one principle: an elderly person should never be found alone, hours after a fall. This device prevents that."

---

## Risk Mitigation

| Risk | Mitigation |
|---|---|
| Hardware doesn't work at demo | Simulator mode — app still shows all data realistically |
| BLE won't pair | Use nRF Connect to show BLE advertising is working, app in sim mode |
| Phone dies | Bring power bank. Keep APK on a second phone |
| App crashes | Back up APK. Record demo video as final fallback |
| Sensor gives wrong readings | Synthetic fallback is always active. Demo the concept, not the calibration |

---

## Component Procurement (₹10,000 budget)

| Item | Source | Cost (₹) |
|---|---|---|
| ESP32-C3 dev board (2×) | Robu.in / Robocraze | 800 |
| MAX30102 module (2×) | Robu.in | 600 |
| MPU-6050 module (2×) | Robu.in | 400 |
| 1000 mAh LiPo battery | Amazon | 400 |
| TP4056 charging module | Robu.in | 150 |
| Resistors, capacitors, wires | Robu.in | 300 |
| Push button (large, tactile) | Local electronics | 100 |
| Breadboard | Already have / ₹100 | 100 |
| USB cables | Already have | 0 |
| 3D print enclosure (outsource) | College lab or Anycubic | 500 |
| Velcro straps for wristband | Amazon | 200 |
| **Total hardware** | | **~3,550** |
| **Reserve for Android phone** | If team doesn't own a BLE-capable Android | 0–5,000 |
| **Buffer** | Misc, shipping, reprints | 500 |

**Total: ~₹4,050–9,050. Well within ₹10,000.**

---

## Success Criteria for Showcase

| Criteria | Target |
|---|---|
| Demo runs without crashing | 10/10 rehearsals |
| Fall detection demo triggers correctly | Hardware OR simulator |
| SOS demo triggers correctly | Hardware OR simulator |
| Judges understand the value in 30 seconds | Yes |
| Questions about "does it really work?" are answered with real data | Serial Monitor logs |
