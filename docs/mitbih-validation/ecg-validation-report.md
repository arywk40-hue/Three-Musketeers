# ECG Anomaly Detector Validation Report

**Generated:** 2026-06-29 19:59
**Datasets:** MIT-BIH Arrhythmia Database (mitdb) + AF Database (afdb)
**Algorithm:** EcgAnomalyDetector — RMSSD + RR irregularity (Python reimplementation)
**Window:** 720 samples @ 360 Hz = 2.0 s, 50% overlap
**Pilot safety requirement:** AFib sensitivity ≥ 0.80

---

## Results

| Dataset | Sensitivity | Specificity | Precision | F1 | TP | FP | TN | FN | Windows |
|---|---|---|---|---|---|---|---|---|---|
| MIT-BIH mitdb | 0.003 | 0.990 | 0.031 | 0.005 | 24 | 751 | 77699 | 8118 | 86592 |
| AF Database afdb | 0.107 | 0.988 | 0.853 | 0.190 | 14601 | 2522 | 216288 | 122122 | 355533 |
| Combined | 0.101 | 0.989 | 0.817 | 0.180 | 14625 | 3273 | 293987 | 130240 | 442125 |

---

## Pilot Safety Verdict

❌ **AFib sensitivity: 0.101** — FAIL — sensitivity below 0.80 threshold

### Remediation

AFib sensitivity is below the 0.80 pilot safety threshold. The rule-based RMSSD +
irregularity approach is fundamentally limited on 2 s windows — many AFib windows
lack the 4+ RR intervals needed for statistical reliability at low HR.

**Current thresholds:** AFIB_RMSSD_THRESHOLD_MS=40,
AFIB_IRREGULARITY_THRESHOLD=0.12, MIN_RR_INTERVALS=4

Options to improve:

1. **Reduce MIN_RR_INTERVALS to 2** — accepts more windows at the cost of specificity.
2. **Increase WINDOW_SAMPLES to 1440** (4 s) — more RR intervals per window.
3. **Train TFLite CNN on MIT-BIH + AFDB** (P2 — `ml/train_cnn.py` pattern) for robust detection.

For the June 2026 pilot, AFib detection defaults to rate-based classification
(Tachycardia / Bradycardia) which is unaffected by this limitation.

---

## EcgAnomalyDetector.kt Constants

```kotlin
private const val AFIB_RMSSD_THRESHOLD_MS = 40
private const val AFIB_IRREGULARITY_THRESHOLD = 0.12f
private const val MIN_RR_INTERVALS = 4
```

---

*ElderCare Guardian — IIT Mandi · Clinical disclaimer: rule-based screening aid only. Not for diagnostic use.*