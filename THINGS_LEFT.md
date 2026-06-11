# Things Left to Implement

> **Generated:** June 2026 — after all 10 implementation sessions completed.

---

## P0 — All Resolved ✅

All 6 P0 blockers fixed or mitigated across Sessions 1–10. Nothing blocks responsible pilot deployment.

---

## P1 — Pre-Commercial (3 items)

| Blocker | Description | Effort |
|---------|-------------|--------|
| **B07 — Medical claims language** | Replace diagnostic language ("AFib", "Bradycardia") with wellness indicators ("irregular rhythm detected — consult a doctor") throughout UI | 2–3 days |
| **B10 — Data retention policy UI** | Allow user to configure retention period (currently hardcoded 7-day Room purge). Export/delete (Session 6) works, but configurable retention is missing | 2–3 days |
| **B11 — Terms of Service page** | Write ToS, host alongside privacy policy on GitHub Pages, link from app and Play Store listing | 1–2 days |

---

## P2 — Pre-Pilot (2 items)

| Blocker | Description | Effort |
|---------|-------------|--------|
| **B14 — Single point of failure** | One phone, one BLE connection, one wearable. Add redundancy: secondary SMS alert path verified, device-removal detection, multi-device pairing | 1–2 weeks |
| **B16 — SpO2 quality indicator** | Display signal quality indicator alongside SpO2. Require 3 consecutive low readings before alerting | 3–5 days |

---

## P3 — Commercial (3 items)

| Blocker | Description | Effort |
|---------|-------------|--------|
| **B17 — Differentiation story** | Sharpen pitch vs Apple Watch / Galaxy Watch. Focus on caregiver dashboard + non-Samsung alert path | Ongoing (marketing) |
| **B18 — Competitor moats** | Identify niche: ₹500 clip-on price point, landline/feature phone integration, B2B care home sales | Ongoing (strategy) |
| **B19 — PCB manufacturing** | Move from ESP32-C3 dev board to custom PCB with SMD sensors. Requires PCB design, PCBA vendor, enclosure, regulatory testing | ₹20–50L, 6–12 mo |

---

## SisFall Dataset Validation

| Task | Description | Effort |
|------|-------------|--------|
| Download SisFall dataset | Request research access from authors | 1 day (wait) |
| Run validation script | `python3 docs/sisfall-validation/validate_fall_engine.py --data-dir <path>` | 1 hour |
| Update thresholds | Paste optimal values into `FallDetectionEngine.kt` | 10 min |
| Re-run tests | `./gradlew testDebugUnitTest` to confirm | 2 min |

---

## Samsung Health Path

| Task | Description | Effort |
|------|-------------|--------|
| Download Data SDK AAR | From Samsung Developer Portal → `app/libs/` | 30 min |
| Apply to Partner App Program | Register `com.eldercareguardian` for write access | 4–8 weeks wait |
| Test on physical Samsung device | Replace `RealSamsungHealthBridge` full integration | 1–2 days |
| Accessory SDK certification | Submit GATT profile for "Works With Samsung Health" | 4–8 weeks |

---

## CI / Release

| Task | Description | Effort |
|------|-------------|--------|
| Enable CI release job | Uncomment release job in `.github/workflows/android-ci.yml`, set secrets | 1 day |
| Set VERSION_CODE auto-increment | Use `github.run_number` (already configured) | Done |
| Create Play Store listing | App icon, screenshots, description, Data Safety section | 2 days |
| Close beta → production rollout | Staged 10% rollout | 48h monitoring |

---

## Future ML (TFLite Models)

| Model | Status | Next Step |
|-------|--------|-----------|
| Fall detection (TFLite) | Scaffold exists (`TfLiteFallbackLoader`), no model file | Train 1D-CNN or XGBoost, convert to `.tflite`, drop in `assets/` |
| ECG anomaly (TFLite) | No scaffold, rule engine active | Train on PTB-XL/MIT-BIH, convert to `.tflite` |
| Dehydration (TFLite) | No scaffold, rule engine active | Collect labelled data, train Random Forest |
| Overexertion (TFLite) | No scaffold, rule engine active | Collect labelled data, train XGBoost |

---

## Cleanup / Tech Debt

| Task | Description | Effort |
|------|-------------|--------|
| Remove `hardware/embedded/` legacy firmware | Deprecated Bluetooth Classic Serial sketch | 30 min |
| Move `ml/README.md` → `docs/ml-roadmap.md` | Consolidate ML docs | 30 min |
| Delete `PRODUCT_GAPS.md` if exists | Superseded by LAUNCH_BLOCKERS.md | 10 min |
