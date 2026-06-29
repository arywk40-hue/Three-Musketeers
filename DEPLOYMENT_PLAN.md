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
| **A — Play Store** | Package renamed, signing configured, CI/release pipeline, R8 rules, privacy policy + ToS, app icon, medical claims sweep, data retention, background location permission | Screenshots, Play Store listing, Data Safety section, beta submission (manual ops) |
| **B — Samsung Health** | `SamsungHealthBridge` rewritten against SDK v1.1.0 via reflection, `SamsungHealthState` UI | Download AAR + partner approval (4–8 weeks), physical device testing |
| **C — Hardware** | ESP32-C3 prototype, BLE GATT profile defined, embedded firmware with watchdog | Custom PCB, certification ($8K), enclosure |
| **D — Pilot** | ✅ SisFall validation executed (F1=0.667, spike=7.5, stillness=15.0). ✅ MIT-BIH ECG validation executed (sens=0.101, spec=0.989). ✅ Fall CNN trained + TFLite (185 KB). ✅ Health risk MLP trained + TFLite (14 KB). ✅ Crash fixes (consent-gated services, null-safe frame merging, AFib priority). | Recruit pilot participants |

**Code blockers resolved:** All P0+P1 code tasks done. Remaining items are manual ops (deployment, account creation, Play Store submission).

---

## Track A — Android Play Store Deployment

### Pre-requisites
1. Rename package from `com.smartsuit` → `com.eldercareguardian` ✅ Done
2. Set `applicationId = "com.eldercareguardian"` ✅ Done
3. Create a Google Play Developer account (₹1,750 one-time fee) — **not yet done**
4. Set up release signing keystore (`keytool -genkey ...`) ✅ Done
5. Configure `signingConfig` in `build.gradle.kts` (local keystore.properties + CI env vars) ✅ Done

### Build Checklist
- [x] `id("com.google.gms.google-services")` already in app/build.gradle.kts plugins block
- [x] `google-services.json` already in `apps/android/app/` (project `eldercare-58d1c`)
- [x] `id("kotlin-parcelize")` already absent — never present in this build file
- [x] `apps/privacy-policy/` already in `.github/workflows/pages.yml` paths trigger
- [x] Enable R8 minification in `buildTypes.release`
- [x] Create `proguard-rules.pro` with Room, SQLCipher, Gson rules
- [x] Set `versionCode` auto-increment in CI (`VERSION_CODE`/`VERSION_NAME` env vars)
- [x] CI release job: base64 keystore decode → `bundleRelease` with env-based signing
- [x] Fill `AndroidManifest.xml` with `android:icon` — **done — done**
- [x] Create adaptive app icon — **done — done**
- [x] 512×512 PNG generated at `docs/play-store-screenshots/icon-512.png`
- [x] App description in `docs/play-store-listing.md`, 6 screenshots in `docs/play-store-screenshots/`
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
- [x] Privacy policy hosted via GitHub Pages (`pages.yml` workflow deploys both `apps/tos/` and `apps/privacy-policy/`)
- [ ] Add privacy policy URL to Play Store listing (manual: paste `https://<org>.github.io/privacy-policy/`)
- [x] Data Safety section answers documented in `docs/play-store-listing.md`

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


# ElderCare Guardian — Deployment Audit & Implementation Roadmap

**Date:** June 29, 2026
**Scope:** Cross-checked `DEPLOYMENT_PLAN.md`, `NEXT_STEPS.md`, `LAUNCH_BLOCKERS.md`, and `IMPLEMENTATION_COMPLETE.md` against the actual Android source (Compose UI, BLE stack, ML engines, Samsung bridge, FCM, service layer).

---

## Verdict

The codebase is genuinely far along — Compose UI, BLE GATT client, Room+SQLCipher, the rule-based ML engine stack, FCM, CI, and firmware are all real, working, and reasonably well tested. But "pilot-ready," the label your own docs use, overstates where things actually are, for two reasons:

1. **Your docs disagree with each other.** `DEPLOYMENT_PLAN.md` (Track A) says the Play Store dev account isn't created yet and screenshots aren't captured. `IMPLEMENTATION_COMPLETE.md`, dated the same day, says "✅ All ready — 6 screenshots captured." Same pattern with Samsung Health: `docs/samsung-health-partnership.md`'s own header says "Status: ⏳ Not started," but `NEXT_STEPS.md`'s summary table marks that task "✅ Done." In each case what's actually finished is a *runbook* — code-side prep plus a step-by-step guide — not the manual real-world action itself (creating a Play developer account, filing the Samsung partnership form, running a live two-phone FCM test).

2. **Code review found two issues your blocker tracking (B01–B27) doesn't have**, one of which undermines the app's core safety claim. Details below.

Net: this reads as "code-complete, demo-ready," not "pilot-ready." That's still a strong place to be — the gap to an actual pilot is now mostly manual ops plus two focused code fixes, not new feature work.

---

## New issues (continuing your B-numbering from `LAUNCH_BLOCKERS.md`)

| ID | Severity | Issue |
|---|---|---|
| B28 | P0 | `SamsungHealthBridgeProvider.create()` and `RealSamsungHealthBridge.checkSdkAvailability()` both probe for `com.samsung.android.sdk.healthdata.HealthDataService` — the **deprecated** SDK package your own `dependencies.md` warns against using. The current Data SDK v1.1.0 package is `com.samsung.android.sdk.health.data` (confirmed by `architecture.md`'s own sample code). Because the class name is wrong, the bridge can **never** leave `NeedsSdkAar`/NoOp state — not even with the real AAR installed and partner approval granted. `RealSamsungHealthBridge.kt`'s whole implementation also follows the *old* SDK's pattern (`connect()` + `ConnectionListener` + `requestPermissions()`/`ResultListener`), not the new SDK's pattern (`HealthDataService.getStore(context)` → `Permission.of(...)` → `insertData()`) that's documented elsewhere in your own repo. This needs a rewrite, not a patch — and it means the "Developer Mode reflection bridge works for pilot" fallback mentioned in your risk table is currently also non-functional. |
| B29 | P0 | `ElderCareMonitorService` (the foreground service for 24/7 background BLE monitoring) is fully implemented — notification channels, alert-level escalation, stop action — but is **never started**. `MainActivity.onCreate()` only renders the consent/dashboard Compose tree; `SmartSuitViewModel` imports the service class but never calls `startForegroundService()` / `ContextCompat.startForegroundService()` anywhere in the provided code. Right now background monitoring is inert: once the screen turns off or the app backgrounds, Android is free to kill the process and nothing keeps BLE/fall-detection alive. This is the headline safety claim in your README — fix this before anything else. |
| B30 | P2 | `FallDetectionEngine.kt`'s class docstring still cites the pre-calibration thresholds (spike=19.6 m/s², stillness=4.0 m/s²) while the actual constants are the SisFall-calibrated values (7.5 / 15.0). No functional bug — just a stale comment that'll mislead the next Claude Code session reading the file for context. |
| B31 | P2 | `SmartSuitPermissions.kt` documents `SEND_SMS` as an "enhanced, progressively-disclosed" permission, requested only once SMS alerts are turned on. In practice the "Grant" button in `SmartSuitApp.kt` calls `requiredRuntimePermissions()`, which is core+enhanced combined — so SMS gets requested up front regardless of whether the user ever enables SMS alerts. Minor, but contradicts the stated design. |

---

## P0 — before anything else

- [x] **B21** — `google-services.json` already present at `apps/android/app/google-services.json` with correct project `eldercare-58d1c` and package `com.eldercareguardian`.
- [x] **B29** — `ElderCareMonitorService` now starts from `SmartSuitViewModel.init`, `updateAlertStatus()` is called on every alert transition in the frame-collection loop, and the service stops when `deleteAllData()` triggers.
- [x] **B28** — `RealSamsungHealthBridge.kt` rewritten against the v1.1.0 API surface (`com.samsung.android.sdk.health.data` package). Uses `HealthDataService.getStore(context)` → `Permission.of(DataTypes.X, AccessType.WRITE)` → `HealthDataPoint.builder()` → `healthDataStore.insertData(request)`, all via reflection. `SamsungHealthBridgeProvider.create()` checks the correct package name.

## P1 — verify these are *actually* done, not just documented

| Task | What your docs claim | What to actually verify |
|---|---|---|
| FCM end-to-end test | "✅ Done — see docs/fcm-test-guide.md" | That's a test *procedure*, not a test *result*. Run it on two real phones per the guide and confirm the push actually lands within 60s. |
| Samsung Health partnership | NEXT_STEPS.md: "✅ Done" / samsung-health-partnership.md: "⏳ Not started" | Submit the real partnership form at developer.samsung.com — the 4–8 week clock hasn't started until you do. Fix B28 first, or the bridge won't be able to use the approval once it arrives. |
| Play Store submission | IMPLEMENTATION_COMPLETE.md: "6 screenshots captured, all ready" / DEPLOYMENT_PLAN.md: "screenshots not captured, dev account not created" | Check `docs/play-store-screenshots/` directly. If it's empty, capture the 6 screens on a Pixel 7 Pro API 35 emulator per `docs/play-store-listing.md`, then create the $25 developer account (24–48h approval wait). |
| google-services.json CI secret | "✅ Done" | Probably fine — just re-confirm the `GOOGLE_SERVICES_JSON` GitHub secret matches the file content once B21 is fixed, so it doesn't go stale. |

## P2 — cleanup, not blocking pilot

- [x] B30 — Updated the stale threshold comment in `FallDetectionEngine.kt` to reflect the SisFall-calibrated values (7.5 / 15.0).
- [x] B31 — "Grant" button now requests core permissions only; SMS permission requested progressively when the SMS toggle is enabled in Settings.
- [x] Added a `BootReceiver` (`.receiver.BootReceiver`) for `BOOT_COMPLETED` that restarts `ElderCareMonitorService` after a device reboot, with `RECEIVE_BOOT_COMPLETED` permission and manifest entry.
- [x] `FcmTokenRefreshService.onMessageReceived()` now logs incoming data and notification payloads for pilot debugging. System-tray auto-display of notification payloads is sufficient for pilot.

---

## Ready-to-paste Claude Code prompts

**Batch 1 — P0 code fixes:**
```
Fix three issues in apps/android:

1. ElderCareMonitorService (apps/android/app/src/main/java/com/eldercareguardian/service/ElderCareMonitorService.kt)
   is fully implemented but never started. Wire it to start via
   ContextCompat.startForegroundService() once DPDPA consent is granted in
   MainActivity (or from SmartSuitViewModel's init block), and call
   updateAlertStatus() whenever the alert level changes in the ViewModel's
   frame-collection loop so the persistent notification stays in sync.
   Add a stop call when DataDeleter.deleteAllData() runs.

2. SamsungHealthBridgeProvider.create() (samsung/SamsungHealthBridge.kt) and
   RealSamsungHealthBridge.checkSdkAvailability() (samsung/RealSamsungHealthBridge.kt)
   both check for the deprecated package com.samsung.android.sdk.healthdata.HealthDataService.
   The current Samsung Health Data SDK v1.1.0 package is
   com.samsung.android.sdk.health.data — see architecture.md's sample code for the
   real API surface (HealthDataService.getStore(context), Permission.of(DataTypes.X,
   AccessType.Y), healthDataStore.insertData(request)). Rewrite RealSamsungHealthBridge.kt
   against that surface via reflection (keep it AAR-optional, same pattern as today),
   fixing the package name everywhere it appears.

3. google-services.json is missing from apps/android/app/. Walk me through getting
   a fresh one from the Firebase console for project eldercare-58d1c and confirm
   ./gradlew assembleDebug succeeds after it's added.

Run ./gradlew assembleDebug and the existing test suite after each fix to confirm
nothing regresses.
```

**Batch 2 — P2 cleanup:**
```
Two small fixes in apps/android:

1. FallDetectionEngine.kt's class docstring cites stale pre-calibration thresholds
   (spike=19.6, stillness=4.0). Update it to match the actual SisFall-calibrated
   constants (FALL_SPIKE_THRESHOLD=7.5f, FALL_STILLNESS_THRESHOLD=15.0f) and reference
   docs/fall-detection-calibration.md.

2. Add a BroadcastReceiver for android.intent.action.BOOT_COMPLETED that restarts
   ElderCareMonitorService after device reboot, with the matching manifest entry
   and RECEIVE_BOOT_COMPLETED permission.
```