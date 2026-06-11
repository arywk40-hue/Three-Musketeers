# Fall Detection Calibration

## Overview

`FallDetectionEngine` uses a three-phase temporal-window algorithm to detect falls from wrist-mounted IMU acceleration data. The thresholds were initially chosen from published literature (Bourke & Lyons 2008). This document tracks calibration results from the SisFall dataset.

> **Status:** Calibration harness built. SisFall dataset must be downloaded separately (research access required) and run through `docs/sisfall-validation/validate_fall_engine.py` to produce the threshold validation matrix below.

## Current Thresholds (Literature-Based)

| Constant | Value | Source |
|---|---|---|
| `FALL_SPIKE_THRESHOLD` | 19.6 m/s² (2g) | Bourke & Lyons 2008 — wrist-mounted impact threshold |
| `FALL_STILLNESS_THRESHOLD` | 4.0 m/s² | Post-fall body movement allowance (breathing, tremor) |
| `SPIKE_MIN_SAMPLES` | 2 | Minimum consecutive samples to confirm impact |
| `STILLNESS_MIN_SAMPLES` | 3 | Minimum consecutive still samples to confirm fall |
| `SPIKE_TO_STILLNESS_WINDOW_SAMPLES` | 5 | Samples after spike to observe stillness (~5 s at 1 Hz) |

## SisFall Dataset

The [SisFall](https://www.researchgate.net/publication/276271186_SisFall_Dataset) dataset contains:

- **38 subjects** (19 young adults, 19 elderly)
- **19 ADL activities** (A01–A19): walking, sitting, standing, stairs, etc.
- **15 fall types** (D01–D15): forward, backward, lateral, slip, trip, syncope, etc.
- **Sampling:** 200 Hz from ADXL345 triple-axis accelerometer (±16g)
- **Sensor placement:** Waist-mounted (closest available surrogate for our wrist-worn sensor)

## Validation Runner

```bash
python3 docs/sisfall-validation/validate_fall_engine.py \
    --data-dir /path/to/SisFall \
    --out docs/fall-detection-calibration.md
```

See [docs/sisfall-validation/README.md](sisfall-validation/README.md) for detailed usage.

## Results Matrix

*Results will appear here after running the SisFall validation.*

| Spike (m/s²) | Stillness (m/s²) | Sensitivity | Specificity | Precision | F1 Score | TP | FP | TN | FN |
|---|---|---|---|---|---|---|---|---|---|
| 19.6 | 4.0 | — | — | — | — | — | — | — | — |
| 14.7 | 4.0 | — | — | — | — | — | — | — | — |
| 17.2 | 3.0 | — | — | — | — | — | — | — | — |
| ... | ... | ... | ... | ... | ... | ... | ... | ... | ... |

## Recommended Configuration

*Will be filled after SisFall validation run.*

```kotlin
private const val FALL_SPIKE_THRESHOLD = 19.6f       // ← current (literature)
private const val FALL_STILLNESS_THRESHOLD = 4.0f     // ← current (literature)
private const val SPIKE_MIN_SAMPLES = 2
private const val STILLNESS_MIN_SAMPLES = 3
private const val SPIKE_TO_STILLNESS_WINDOW_SAMPLES = 5
```

### Elderly-Specific Considerations (B15)

The current 2g (19.6 m/s²) threshold may need downward adjustment for the elderly population:

- **Falls in elderly** tend to be slower, with lower impact force due to reduced walking speed and lower centre of mass.
- **Previous studies** (Kangas et al. 2012) suggest 1.5g–1.8g (14.7–17.6 m/s²) for elderly fall detection from waist/wrist sensors.
- **Tremor and involuntary movements** in elderly with Parkinson's or essential tremor may produce false spikes.
- The **stillness threshold** (4.0 m/s²) accounts for breathing and small movements — may need narrowing to 3.0–3.5 m/s² for elderly who lie more still after a fall.

The SisFall dataset's elderly subject subset (19 subjects aged 60+) can be used to validate these specific concerns. See `docs/sisfall-validation/README.md` for subject-level filtering instructions.

## TFLite Roadmap

When `.tflite` model files become available, replace `FallDetectionEngine` with a TFLite-based fall detector:

1. Add `org.tensorflow:tensorflow-lite:2.15.0` to `build.gradle.kts`
2. Place `fall_detection.tflite` in `app/src/main/assets/`
3. Wire `RealTfLiteFallbackLoader` in place of `FallDetectionEngine`
4. Fall back to rule engine when `classify()` returns null

The `TfLiteFallbackLoaderProvider` already handles graceful degradation — no code changes needed when the dependency is absent.

## Unit Tests

Current tests (`FallDetectionEngineTest.kt`, `FallDetectionEnginePhase5Test.kt`, `FallConfirmationBufferTest.kt`) verify:
- Normal movement → Low risk
- Spike alone → Medium risk (not High)
- Spike + stillness → High risk
- Sustained stillness without spike → Low (inactivity, not fall)
- Reset clears all state

Run with:
```bash
./gradlew testDebugUnitTest
```

Run single test class:
```bash
./gradlew testDebugUnitTest --tests '*FallDetectionEngine*'
```
