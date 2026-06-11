# SisFall Validation

This directory contains the validation runner for `FallDetectionEngine` against the [SisFall](https://www.researchgate.net/publication/276271186_SisFall_Dataset) public fall dataset.

## Dataset Download

1. Request access to the SisFall dataset from the authors (free for research).
2. Extract to a local directory. Expected layout:

```
SisFall/
├── A/        # Activities of Daily Living (ADL) — non-fall
│   ├── SA01A01.txt
│   ├── SA01A02.txt
│   └── ...
└── D/        # Falls
    ├── SA01D01.txt
    ├── SA01D02.txt
    └── ...
```

File format: SisFall default sensor setup (ADXL345 at waist). Columns:
`Sample, ax(g), ay(g), az(g), gx(°/s), gy(°/s), gz(°/s), ...`

The parser uses the waist ADXL345 (columns 1-3) — closest to our wrist-worn sensor placement. It converts from g to m/s² automatically.

## Usage

```bash
python3 docs/sisfall-validation/validate_fall_engine.py \
    --data-dir /path/to/SisFall \
    --out docs/fall-detection-calibration.md
```

### Threshold Sweep

Default sweep:

| Parameter | Values |
|---|---|
| Spike threshold | 14.7, 17.2, 19.6, 22.0, 24.5 m/s² (1.5g–2.5g) |
| Stillness threshold | 3.0, 4.0, 5.0 m/s² |
| Spike min samples | 2 |
| Stillness min samples | 3 |
| Spike-to-stillness window | 5 samples |

Override:

```bash
python3 docs/sisfall-validation/validate_fall_engine.py \
    --data-dir /path/to/SisFall \
    --thresholds 14.7,17.2,19.6 \
    --stillness 3.0,4.0 \
    --out docs/fall-detection-calibration.md
```

## Output

The script generates a markdown calibration report at `--out` (default: stdout). The report:

1. Threshold sweep matrix with sensitivity, specificity, precision, F1
2. Recommended configuration (highest F1 score)
3. Kotlin code snippet to paste into `FallDetectionEngine.kt`

## How It Works

`validate_fall_engine.py` reimplements `FallDetectionEngine.assess()` in pure Python:

- Three-phase temporal window (spike → stillness → confirmation)
- Configurable thresholds
- Decimation from 200 Hz (SisFall sampling rate) to ~1 Hz (BLE notify rate)
- Runs every fall and ADL sequence through the engine
- Reports TP / FP / TN / FN

## Update FallDetectionEngine.kt

After finding optimal thresholds, update `apps/android/app/src/main/java/com/eldercareguardian/ml/FallDetectionEngine.kt`:

```kotlin
private const val FALL_SPIKE_THRESHOLD = 19.6f       // ← match calibration report
private const val FALL_STILLNESS_THRESHOLD = 4.0f     // ← match calibration report
```

Then run `./gradlew testDebugUnitTest` to confirm existing tests still pass with the new thresholds.
