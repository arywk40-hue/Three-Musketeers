# ElderCare Guardian — Deployment Plan

**Date:** June 2026  
**Team:** Pranay · Ariyan · Reman Dey  
**Institution:** IIT Mandi

---

## Overview

A realistic deployment roadmap from current prototype → public product. This plan separates four tracks:

- **Track A:** Android Play Store deployment  
- **Track B:** Samsung Health certification  
- **Track C:** Hardware manufacturing & BLE certification  
- **Track D:** Pilot testing with real elderly users  

Each track has independent timelines and can progress in parallel.

### Current Status

| Track | Progress | Remaining |
|-------|----------|-----------|
| **A — Play Store** | Package renamed, signing configured, CI/release pipeline, R8 rules, privacy policy + ToS, app icon, medical claims sweep, data retention, background location permission | Screenshots, Play Store listing, Data Safety section, beta submission |
| **B — Samsung Health** | `SamsungHealthBridge` abstraction with `NoOp`/`Real` impl, `SamsungHealthState` UI | Download AAR + partner approval (4–8 weeks), physical device testing |
| **C — Hardware** | ESP32-C3 prototype, BLE GATT profile defined, embedded firmware with watchdog | Custom PCB, certification ($8K), enclosure |
| **D — Pilot** | Fall detection validation harness (SisFall), calibration report | Dataset download + run validation, recruit participants |

**Blockers resolved:** All P0 (0 remaining), all P1 (0 remaining — B07/B10/B11 fixed), P2 (1 remaining — B14 single-point-of-failure deferred).

---

## Track A — Android Play Store Deployment

### Pre-requisites
1. Rename package from `com.smartsuit` → `com.eldercareguardian` ✅ Done
2. Set `applicationId = "com.eldercareguardian"` ✅ Done
3. Create a Google Play Developer account (₹1,750 one-time fee) — **not yet done**
4. Set up release signing keystore (`keytool -genkey ...`) ✅ Done
5. Configure `signingConfig` in `build.gradle.kts` (local keystore.properties + CI env vars) ✅ Done

### Build Checklist
- [ ] Add `id("com.google.gms.google-services")` to app/build.gradle.kts plugins block
- [ ] Download `google-services.json` from Firebase console → place in `apps/android/app/`
- [ ] Remove unused `id("kotlin-parcelize")` from app/build.gradle.kts
- [ ] Add `apps/privacy-policy/` to `.github/workflows/pages.yml` paths so it deploys to GitHub Pages
- [x] Enable R8 minification in `buildTypes.release`
- [x] Create `proguard-rules.pro` with Room, SQLCipher, Gson rules
- [x] Set `versionCode` auto-increment in CI (`VERSION_CODE`/`VERSION_NAME` env vars)
- [x] CI release job: base64 keystore decode → `bundleRelease` with env-based signing
- [x] Fill `AndroidManifest.xml` with `android:icon` — **done — done**
- [x] Create adaptive app icon — **done — done**
- [ ] Generate 512×512 PNG for Play Store (use Android Studio Image Asset Studio)
- [ ] Write app description, screenshots for Play Store listing — docs/play-store-listing.md created, screenshots not captured
- [x] Set `targetSdkVersion = 35` (required for new apps on Play)
- [ ] Handle `SEND_SMS` permission — submit Play Store Declaration Form with:
  Use case: "Emergency caregiver SMS alert when patient falls or triggers SOS.
  No marketing or commercial SMS. Permission is only requested when the user
  explicitly enables SMS alerts in Settings."

### Play Store Policy Compliance
- [x] Remove any diagnostic claims from app — **done**
- [x] Use wellness language throughout UI — **done — done**
- [x] Privacy policy page created at `apps/privacy-policy/index.html` — done
- [x] Terms of Service page created at `apps/tos/index.html` — done
- [x] GitHub Pages deploy workflow at `.github/workflows/pages.yml` — done
- [ ] Host privacy policy on GitHub Pages (`gh-pages` branch) — workflow auto-deploys apps/tos/, but apps/privacy-policy not yet in pages.yml path
- [ ] Add privacy policy URL to Play Store listing
- [ ] Add a Data Safety section (health data stored locally, not shared)

### Timeline
| Milestone | Duration | Status |
|---|---|---|
| Package rename + signing setup | 1 day | ✅ Done |
| R8 rules + minification testing | 2 days | ✅ Done |
| CI release pipeline | 2 days | ✅ Done |
| Privacy policy page | 1 day | ✅ Done |
| Terms of Service page | 1 day | ✅ Done |
| App icon + adaptive icon resources | 1 day | ✅ Done |
| Medical claims language sweep | 2 days | ✅ Done |
| Configurable data retention | 2 days | ✅ Done |
| Play Store listing creation (screenshots, description, Data Safety) | 2–3 days | ⏳ Pending |
| Internal testing track (closed beta) | 1 week | ⏳ Pending |
| Production submission + review | 1–2 weeks | ⏳ Pending |

---

## Track B — Samsung Health Certification

### Samsung Health Accessory SDK Path

1. Create Samsung Developer account at developer.samsung.com
2. Download Samsung Health Accessory SDK
3. Verify GATT profile against Samsung's spec:
   - Heart Rate Service (0x180D) → auto-recognised
   - PLX Service (0x1822) → auto-recognised
   - Health Thermometer (0x1809) → auto-recognised
4. Apply for "Works With Samsung Health" certification
5. Submit device spec + APK for Samsung review

### Samsung Health Data SDK Path (requires Partner App Program)

1. Apply to the Partner App Program at developer.samsung.com/health/data/process.html
2. Approval timeline: 4–8 weeks
3. After approval: download the Data SDK AAR
4. Replace `NoOpSamsungHealthBridge` with the real implementation
5. Test on a physical Samsung device with Samsung Health v6.30.2+

### Critical Constraint
The Samsung Health Data SDK **does not work on emulators**. All testing requires a physical Samsung phone. This is a known limitation and there is no workaround.

---

## Track C — Hardware Manufacturing & BLE Certification

### BLE Certification (Bluetooth SIG)

1. Register at bluetooth.com/develop-with-bluetooth/qualification-listing/
2. If using ESP32-C3: Espressif has pre-qualified BLE stacks. You may use the Declaration ID from Espressif.
3. If developing a custom PCB with nRF5340: Nordic has pre-qualified the nRF5340 SoC. The host stack qualification covers your product.
4. Product listing fee: $8,000 USD (significant cost — defer to post-revenue stage)

### For the Showcase
- Use ESP32-C3 dev board — no certification needed for demo/prototype
- For production PCB: use nRF5340 (Nordic's QDID can be leveraged)

### Hardware Manufacturing Roadmap

| Stage | Hardware | Timeline | Cost |
|---|---|---|---|
| Prototype | ESP32-C3 dev board + breakout sensors on perfboard | Done / 1 week | ₹3,000–5,000 |
| V1 PCB | Custom ESP32-C3 or nRF5340 PCB with SMD sensors | 4–6 weeks | ₹10,000–30,000 (JLCPCB) |
| Enclosure | 3D printed wristband enclosure | 2–3 weeks | ₹2,000–5,000 |
| V2 production | PCB + enclosure + sourced components | 3–4 months | ₹500–1,500 per unit at 100 units |

### Regulatory (India — CDSCO)

Under the Medical Devices Rules 2017 (India), the device may qualify as:
- **Class A** (low risk) if marketed as a wellness/safety device with no diagnostic claims
- **Class B** (moderate risk) if marketed as a monitoring device making health claims

**Recommendation:** Market as a "personal safety wearable" with "wellness insights" to stay in Class A. Avoid language that implies diagnosis or treatment decisions.

---

## Track D — Pilot Testing Roadmap

### Phase 1 — Lab Validation (Weeks 1–4)
- Recruit 3–5 team members / volunteers for wearability testing
- Validate fall detection false-positive rate (target: < 1 false alarm per hour of normal wear)
- Validate battery life (target: 8+ hours continuous BLE streaming)
- Validate BLE connection stability over 2 hours
- Document sensor accuracy vs a calibrated reference device

### Phase 2 — Controlled Pilot (Months 2–3)
- Recruit 5–10 elderly participants with caregiver consent
- Deploy for 2 weeks at home (or assisted-living facility)
- Collect: alert logs, false positive/negative reports, caregiver response times
- Collect: usability feedback (wearable comfort, app clarity)

### Phase 3 — Field Pilot (Months 4–6)
- Deploy to 20–50 users with a structured data collection protocol
- Partner with a home healthcare provider or geriatric clinic in Himachal Pradesh
- Establish SLA: alert to caregiver acknowledgement < 5 minutes for Emergency

### Success Criteria for Public Launch
- [ ] False positive fall alert rate: < 1 per 8-hour wear day
- [ ] False negative fall rate: < 10% on a labelled test set
- [ ] Battery life: > 12 hours on a 1000 mAh cell
- [ ] BLE connection stability: > 99% uptime over 2-hour test window
- [ ] Caregiver alert delivery: 100% within 60 seconds of trigger
- [ ] User wearability: > 80% of pilot users wear it > 8 hours/day
