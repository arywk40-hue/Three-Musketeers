# Safety Engine — Code Map

This page maps every rule/ML model in `architecture.md` §Layer 5 to the
Kotlin files that implement it for ElderCare Guardian. Everything is
rule-based today. When real `.tflite` model files land in
`app/src/main/assets/`, the same interfaces can be swapped for TFLite
backends without touching the rest of the app.

| Architecture model            | Kotlin file                                   | Status         | Notes                                                                                          |
|-------------------------------|-----------------------------------------------|----------------|------------------------------------------------------------------------------------------------|
| Model 1 — ECG Anomaly         | `ml/EcgAnomalyDetector.kt`                    | rule-based     | R-peak detect → RR intervals → RMSSD + rate. Output: `Normal / AFib / Tachycardia / Bradycardia` |
| Model 1 helper — RR intervals | `ml/HeartRateExtractor.kt`                    | rule-based     | 256 Hz default, refractory 200 ms, threshold 0.55                                                |
| Model 2 — Fall & Inactivity   | `ml/FallDetectionEngine.kt`                   | rule + TFLite  | SisFall-calibrated: spike > 7.5 m/s² or stillness < 15.0 m/s² → High/Medium. FallDetectionEngine + `fall_detection.tflite` (185 KB, 1D-CNN) both available. Rule engine prioritized; CNN used as second opinion. See `docs/fall-detection-calibration.md`. |
| Model 2 — Inactivity          | `ml/InactivityMonitor.kt`                     | rule-based     | Counts seconds since `|‖a‖ − 9.81| > 0.6 m/s²`                                                |
| Model 3 — Dehydration         | `ml/DehydrationRiskModel.kt`                  | rule-based     | Sweat rate + skin temp + HR → Low / Medium / High                                               |
| Model 4 — Overexertion        | `ml/OverexertionModel.kt`                     | rule-based     | HR-reserve % + SpO2 drop + RR + IMU intensity → Safe / Caution / Stop                            |
| Model 5 — BP Estimation       | `ml/BloodPressureEstimator.kt`                | rule-based     | HR + skin-temp linear model, output flagged `isEstimated = true`                                |
| Caregiver triage              | `ml/CaregiverAlertPolicy.kt`                  | rule-based     | SOS, extreme HR, low SpO2, high fall risk → Urgent; medium fall, inactivity, soft HR → Check    |
| Sensor fusion                 | `data/SensorFrameMerger.kt`                   | glue           | Combines BLE telemetry + simulator frame, runs engines, derives caregiver alert                 |
| SafetyEngine entry points     | `ble/SmartSuitSimulator.kt` (demo)            | glue           | Emits raw vitals; engines derive the computed fields in the same frame                          |

## Data flow

```
SmartSuitSimulator.frames ──┐
                            ├──► SensorFrameMerger.merge ──► SensorFrame
SmartSuitBleDataSource.telemetry ──┘                                  │
                                                                       ▼
                                                          SmartSuitViewModel.frames
                                                                       │
                          ┌────────────────────────────────────────────┴───────┐
                          ▼                                                    ▼
                Compose UI (Vitals/Safety/Caregiver/Readiness tabs)   SamsungHealthBridge.writeVitals
```

## TFLite models deployed

Two TFLite models are now live in `app/src/main/assets/`:

| Model | File | Size | Description |
|-------|------|------|-------------|
| Fall detection | `fall_detection.tflite` | 185 KB | 1D-CNN (Conv1D 32→64→128, Dense 64→2), accepts raw IMU window |
| Health risk | `health_risk.tflite` | 14 KB | MLP with shared trunk + 3 output heads (vitals, dehydration, overexertion) |

Both were trained on real datasets (SisFall, synthetic health records), validated offline, and exported via ONNX → TFLite.

## Adding more TFLite models

1. Add `org.tensorflow:tensorflow-lite:2.15.0` to `app/build.gradle.kts`
   (currently absent — `TfLiteFallbackLoader` uses reflection so the app compiles without it).
2. Drop the `.tflite` file into `app/src/main/assets/`.
3. Wire `TfLiteFallbackLoaderProvider.create(context)` at the call site:
   ```kotlin
   val loader = TfLiteFallbackLoaderProvider.create(context)
   loader.loadModel("ecg_anomaly.tflite")
   val result = loader.classify(floatArrayOf(...))
   if (result == null) {
       // fall back to EcgAnomalyDetector
   }
   ```
4. When `TFLite` dep is added later, `RealTfLiteFallbackLoader` starts working
   automatically — no code changes needed.
