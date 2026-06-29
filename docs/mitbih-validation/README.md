# MIT-BIH ECG Validation

Validates `EcgAnomalyDetector` (AFib detection) against:

- **MIT-BIH Arrhythmia Database** (mitdb) — mixed rhythms, includes AFib records
- **AF Database** (afdb) — predominantly AFib recordings

**Pilot safety requirement:** AFib sensitivity ≥ 0.80

## Setup

```bash
pip install wfdb numpy
python3 -c "import wfdb; wfdb.dl_database('mitdb', dl_dir='ml/data/mitdb')"
python3 -c "import wfdb; wfdb.dl_database('afdb',  dl_dir='ml/data/afdb')"
```

## Run

```bash
python3 docs/mitbih-validation/validate_ecg_engine.py \
    --mitdb ml/data/mitdb \
    --afdb  ml/data/afdb \
    --out   docs/mitbih-validation/ecg-validation-report.md
```

Quick test (first 5 records per DB):

```bash
python3 docs/mitbih-validation/validate_ecg_engine.py --limit 5
```

The script exits with code 1 if sensitivity < 0.80.

## Output

Report written to `docs/mitbih-validation/ecg-validation-report.md` with:
- Sensitivity / specificity / precision / F1 per dataset and combined
- Pilot safety verdict
- Remediation steps if sensitivity < 0.80
- Kotlin constant block to paste into `EcgAnomalyDetector.kt` if thresholds change

## Algorithm

The script reimplements `HeartRateExtractor` + `EcgAnomalyDetector` in pure Python
(no Android dependency). A 720-sample sliding window (50% overlap) is classified as
AFib if `RMSSD > 40 ms AND RR irregularity > 0.12`.

MIT-BIH records at 360 Hz: window = 2.0 s (matches Kotlin's 600-sample buffer at 256 Hz).
The AF Database is evaluated with the same window size.

**Current sensitivity: 0.101** — rule-based RMSSD approach needs more RR intervals than
a 2s window provides at normal HR. A TFLite CNN model is planned for P2 to reach the
pilot safety target of ≥ 0.80 sensitivity.
