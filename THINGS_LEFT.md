# Things Left to Implement

> **Generated:** June 2026 — after Sessions 1–20 complete.

---

## All Sessions 11–20 Implemented ✅

| Session | Scope | Status |
|---------|-------|--------|
| **11** | Fix CI `if:` condition (already clean); DataSourceChip in dashboard | ✅ Complete |
| **12** | Medical claims language sweep: `displayLabel` on `EcgAnomalyStatus`, non-diagnostic UI strings | ✅ Complete |
| **13** | `DataRetentionPreferences` (7/14/30/60/90 days), settings picker, ViewModel wired | ✅ Complete |
| **14** | Terms of Service page (`apps/tos/index.html`), GitHub Pages deploy workflow | ✅ Complete |
| **15** | `Spo2Quality` enum, 3-reading debounce in `CaregiverAlertPolicy`, quality chip on VitalsScreen | ✅ Complete |
| **16** | `InactivityMonitor` accumulator in `SensorFrameMerger`, `isFallActive` wired | ✅ Complete |
| **17** | `FallDetectionEngine` converted from `object` to `class`, all call sites + tests updated | ✅ Complete |
| **18** | `ACCESS_BACKGROUND_LOCATION` in manifest + permissions + rationale in ReadinessScreen | ✅ Complete |
| **19** | Adaptive app icon (teal + heart-pulse), mipmap-anydpi-v26, manifest `android:icon` | ✅ Complete |
| **20** | SisFall validation script ready; block on dataset download | ⏳ Blocked (no dataset) |

---

## P1 — Pre-Commercial (0 items remaining)
All B07, B10, B11 items resolved across Sessions 12–14.

---

## P2 — Pre-Pilot (1 item remaining)

| Blocker | Description | Effort |
|---------|-------------|--------|
| **B14 — Single point of failure** | Redundancy: secondary SMS alert, device-removal detection, multi-device pairing | 1–2 weeks |

> **B16** resolved in Session 15.

---

## P3 — Commercial (3 items)

| Blocker | Description | Effort |
|---------|-------------|--------|
| **B17 — Differentiation story** | Sharpen pitch vs Apple Watch / Galaxy Watch | Ongoing (marketing) |
| **B18 — Competitor moats** | Identify niche: ₹500 clip-on, landline integration, B2B care homes | Ongoing (strategy) |
| **B19 — PCB manufacturing** | Custom PCB with SMD sensors, enclosure, regulatory testing | ₹20–50L, 6–12 mo |

---

## SisFall Dataset Validation (Session 20)

| Task | Description |
|------|-------------|
| Download SisFall dataset | Request research access from authors |
| Run validation script | `python3 docs/sisfall-validation/validate_fall_engine.py --data-dir <path>` |
| Update thresholds | Paste optimal values into `FallDetectionEngine.kt` |
| Re-run tests | `./gradlew testDebugUnitTest` to confirm |

---

## Samsung Health Path (no session planned — partner-dependent)

| Task | Description |
|------|-------------|
| Download Data SDK AAR | From Samsung Developer Portal → `app/libs/` |
| Apply to Partner App Program | Register `com.eldercareguardian` for write access (4–8 weeks) |
| Test on physical Samsung device | Full `RealSamsungHealthBridge` integration |
| Accessory SDK certification | Submit GATT profile for "Works With Samsung Health" |

---

## CI / Release

| Task | Session | Status |
|------|---------|--------|
| Fix CI `if:` condition | 11 | ✅ Clean (no secrets in `if:`) |
| App icon + feature graphic | 19 | ✅ Adaptive icon created |
| Play Store listing | 19 | ⏳ Screenshots, description, Data Safety section |
| Close beta → production rollout | After 19 | ⏳ Staged 10% rollout |

---

## Future ML (TFLite Models)

| Model | Status | Next Step |
|-------|--------|-----------|
| Fall detection (TFLite) | Scaffold exists (`TfLiteFallbackLoader`), no model file | Train 1D-CNN or XGBoost, convert to `.tflite`, drop in `assets/` |
| ECG anomaly (TFLite) | No scaffold, rule engine active | Train on PTB-XL/MIT-BIH, convert to `.tflite` |
| Dehydration (TFLite) | No scaffold, rule engine active | Collect labelled data, train Random Forest |
| Overexertion (TFLite) | No scaffold, rule engine active | Collect labelled data, train XGBoost |

---

## Cleanup / Tech Debt (Done ✅)

| Task | Status |
|------|--------|
| Remove `hardware/embedded/` legacy firmware | ✅ Deleted |
| Move `ml/README.md` → `docs/ml-roadmap.md` | ✅ Done |
| Delete `PRODUCT_GAPS.md` if exists | ✅ Not found |
