# ECG Anomaly Detector Validation Report

**Generated:** 2026-06-29 09:12
**Datasets:** MIT-BIH Arrhythmia Database (mitdb) + AF Database (afdb)
**Algorithm:** EcgAnomalyDetector — RMSSD + RR irregularity (Python reimplementation)
**Window:** 256 samples @ 360 Hz ≈ 0.71 s, 50% overlap
**Pilot safety requirement:** AFib sensitivity ≥ 0.80

---

## Results

| Dataset | Sensitivity | Specificity | Precision | F1 | TP | FP | TN | FN | Windows |
|---|---|---|---|---|---|---|---|---|---|
| MIT-BIH mitdb | 0.000 | 1.000 | 0.000 | 0.000 | 0 | 0 | 21660 | 0 | 21660 |
| AF Database afdb | 0.000 | 1.000 | 0.000 | 0.000 | 0 | 0 | 141768 | 11658 | 153426 |
| Combined | 0.000 | 1.000 | 0.000 | 0.000 | 0 | 0 | 163428 | 11658 | 175086 |

---

## Pilot Safety Verdict

❌ **AFib sensitivity: 0.000** — FAIL — sensitivity below 0.80 threshold

### Remediation

AFib sensitivity is below the 0.80 pilot safety threshold. Options:

1. **Lower AFIB_RMSSD_THRESHOLD_MS** (currently 50 ms) — more windows will be flagged as AFib.
2. **Lower AFIB_IRREGULARITY_THRESHOLD** (currently 0.20) — less strict irregularity requirement.
3. **Increase WINDOW_SAMPLES** — more RR intervals per window improves RMSSD stability.
4. **Train TFLite CNN** (see `ml/train_cnn.py`) and replace rule-based engine.

Re-run this script after each change to verify the new sensitivity.

---

## EcgAnomalyDetector.kt Constants

```kotlin
private const val AFIB_RMSSD_THRESHOLD_MS = 50
private const val AFIB_IRREGULARITY_THRESHOLD = 0.2f
private const val MIN_RR_INTERVALS = 4
```

---

*ElderCare Guardian — IIT Mandi · Clinical disclaimer: rule-based screening aid only. Not for diagnostic use.*