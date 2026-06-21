# Samsung Health Partner Program Application Guide

**Priority:** P1 (Required for production Samsung Health integration)  
**Timeline:** 4–8 weeks approval time  
**Status:** ⏳ Not started — apply now to start the clock

---

## Overview

The Samsung Health Partner Program allows third-party apps to write health data (HR, SpO2, temperature) to Samsung Health history. ElderCare Guardian currently uses a **reflection bridge** (`RealSamsungHealthBridge.kt`) that works in **developer mode only**. Partner approval is required for production use.

---

## Current implementation status

| Component | Status | Notes |
|-----------|--------|-------|
| Data SDK integration | ✅ Complete | Reflection bridge in `samsung/RealSamsungHealthBridge.kt` |
| Developer mode testing | ✅ Working | Requires user to enable "Developer Mode" in Samsung Health settings |
| Production approval | ❌ Not started | Requires Partner Program application |
| Data types supported | ✅ Ready | Heart rate, SpO2, skin temperature |

---

## Why apply now?

- **Long approval time:** 4–8 weeks typical, can be longer during holiday periods
- **Pilot-ready app:** The reflection bridge works for internal testing, but production users need the official approval
- **No code changes required:** Once approved, the app continues working without modification — Samsung just whitelists the package name and signature

---

## Application process

### Step 1: Download the SDK (reference only)

The SDK is already integrated via reflection, but for reference:
- **URL:** https://developer.samsung.com/health/data/process.html
- **Version:** v1.1.0 (1.78 MB)
- **Current approach:** We use reflection to avoid committing the AAR, which keeps the codebase cleaner

### Step 2: Request partnership

1. **Go to:** https://developer.samsung.com/SHealth/business-partner/m48wvqi1mt9w2w4c
2. **Sign in** with your Samsung Developer account (create one if needed — free)
3. **Fill out the partnership request form:**

#### Required information

| Field | Value for ElderCare Guardian |
|-------|------------------------------|
| **App Name** | ElderCare Guardian |
| **Package Name** | `com.eldercareguardian` |
| **App Signature (SHA-256)** | See below for extraction command |
| **App Description** | Elderly safety and health monitoring wearable — vitals, fall detection, SOS, and caregiver alerts. Integrates with Samsung Health to write heart rate, SpO2, and temperature data from a Bluetooth wearable device (ESP32-C3 + MAX30102 sensor). |
| **Data Types Used** | Heart rate (read/write), SpO2 (write), Temperature (write) |
| **Use Case** | Continuous health monitoring for elderly users living alone. Data is stored in Samsung Health for long-term trend analysis and caregiver review. |
| **Target Audience** | Elderly users (65+) and their caregivers in India |
| **Distribution** | Google Play Store (internal testing → production) |
| **Website** | https://eldercareguardian.pages.dev (privacy policy + ToS) |

#### Extract app signature (SHA-256)

**For debug keystore (testing only):**
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA256
```

**For release keystore (submit this one):**
```bash
keytool -list -v -keystore /path/to/eldercare-release.jks -alias eldercare -storepass <your-password> | grep SHA256
```

Copy the full SHA-256 fingerprint (e.g., `A1:B2:C3:...`) and paste into the form.

**Alternative (from signed APK/AAB):**
```bash
# Extract from release APK
apksigner verify --print-certs app-release.apk | grep SHA-256

# Extract from AAB
bundletool build-apks --bundle=app-release.aab --output=temp.apks --mode=universal
unzip temp.apks -d temp_apk
apksigner verify --print-certs temp_apk/universal.apk | grep SHA-256
```

### Step 3: Submit and wait

- **Typical timeline:** 4–8 weeks
- **Email notification:** Samsung sends approval/rejection to the email associated with your Samsung Developer account
- **Status check:** Log in to https://developer.samsung.com → My Applications → Partnership Status

### Step 4: After approval

**No code changes required!** Once approved:
1. Samsung whitelists `com.eldercareguardian` + your SHA-256 signature
2. `RealSamsungHealthBridge` transitions from `NeedsPartnerApproval` → `Ready` automatically
3. Users no longer need to enable Developer Mode in Samsung Health
4. Data writes work on all Samsung devices with Samsung Health installed

---

## What data is written?

| Data Type | Samsung Health Type | Source | Frequency |
|-----------|---------------------|--------|-----------|
| Heart Rate | `HeartRate` | MAX30102 via BLE | Every 5 seconds |
| SpO2 | `OxygenSaturation` | MAX30102 via BLE | When valid (IR/Red signal quality > 50,000) |
| Skin Temperature | `BodyTemperature` | TMP117 or simulator | Every 30 seconds |

**Not written (removed in Session 7):**
- Blood pressure (estimated BP removed from Samsung Health writes, still displayed in app with "est mmHg" label)

---

## Developer mode (temporary workaround)

While waiting for approval, testers can enable Developer Mode in Samsung Health:

1. Open **Samsung Health** app
2. Tap **Menu** (three dots, top-right)
3. Tap **Settings**
4. Scroll to **About Samsung Health**
5. Tap **About Samsung Health** 10 times rapidly
6. A toast appears: "Developer mode enabled"

**Important:** Do NOT share this with end users. Developer mode is for testing only and can expose unstable features.

---

## Troubleshooting

### "Partnership request rejected"
Common reasons:
- **Medical claims:** Avoid language like "diagnoses," "treats," "prevents disease"
- **Misleading description:** Samsung requires clear disclosure that the app is wellness/fitness, not medical
- **Trademark issues:** Ensure the app name doesn't infringe on Samsung or other trademarks

**Fix:** Revise the description to emphasize **wellness indicators** and **caregiver convenience**, not medical diagnosis. Re-submit.

### "App not found in Play Store"
Samsung may require a published Play Store listing before approval.

**Fix:** Submit the app to Play Store **Internal Testing** track first (see NEXT_STEPS task #9), then include the Play Store URL in the partnership form.

### "SHA-256 mismatch"
The signature in the form doesn't match the installed APK.

**Fix:** 
1. Extract the SHA-256 from the **release keystore** (not debug)
2. Ensure the submitted APK is signed with the same keystore
3. Verify: `apksigner verify --print-certs app-release.apk | grep SHA-256`

---

## Post-approval checklist

✅ Verify `RealSamsungHealthBridge` state transitions to `Ready`  
✅ Test on a Samsung device **without** Developer Mode enabled  
✅ Confirm HR/SpO2/Temp appear in Samsung Health history  
✅ Update `README.md` to note "Works With Samsung Health" status  
✅ Add "Works With Samsung Health" badge to Play Store listing (optional)  

---

## Security and privacy

- **User consent:** Samsung Health SDK requires runtime permission — users must explicitly grant access
- **Data ownership:** Users own their health data; the app only writes to their local Samsung Health database
- **No cloud sync by default:** Data stays on-device unless the user enables Samsung account sync
- **DPDPA compliance:** ElderCare Guardian's DPDPA consent flow (Session 11) covers Samsung Health data sharing

---

## References

- **Partnership application:** https://developer.samsung.com/SHealth/business-partner/m48wvqi1mt9w2w4c
- **Process guide:** https://developer.samsung.com/health/data/process.html
- **Data SDK documentation:** https://developer.samsung.com/health/data
- **FAQ:** https://developer.samsung.com/health/data/faq.html
- **Dev Support:** https://developer.samsung.com/dev-support

---

## Related files

- `apps/android/app/src/main/java/com/eldercareguardian/samsung/RealSamsungHealthBridge.kt` — Reflection bridge (production-ready)
- `apps/android/app/src/main/java/com/eldercareguardian/samsung/SamsungHealthState.kt` — State machine
- `NEXT_STEPS.md` — Task #8 (this document fulfills that task)
- `DEPLOYMENT_PLAN.md` — Release pipeline (keystore management)

---

## Timeline estimate

| Week | Milestone |
|------|-----------|
| 0 | Submit partnership request |
| 1–2 | Samsung internal review |
| 3–4 | Possible follow-up questions (check email daily) |
| 5–7 | Final approval or rejection |
| 8+ | Re-submission if rejected |

**Start now** to ensure approval arrives before the June 2026 pilot.
