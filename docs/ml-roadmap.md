# ML

ML started with rule-based elder-safety alerts and now includes validated on-device TFLite models.

## Current (June 2026) — Trained and Deployed

| Model | Architecture | Dataset | Epochs | Params | TFLite | Validation |
|-------|-------------|---------|--------|--------|--------|------------|
| Fall detection | 1D-CNN (Conv1D 32→64→128, Dense 64→2) | SisFall (120K windows) | 50 | 43,906 | `fall_detection.tflite` (185 KB) | Test acc=0.826, F1=0.706 |
| Health risk (vitals) | MLP shared trunk + 3 heads | 50K synthetic | 60 | ~3,500 | `health_risk.tflite` (14 KB) | 93% accuracy |
| Health risk (dehydration) | same shared trunk | 50K synthetic | 60 | shared | same TFLite | 97% accuracy |
| Health risk (overexertion) | same shared trunk | 50K synthetic | 60 | shared | same TFLite | 88% accuracy |

## Rule-based (still active)

- Fall detection — FallDetectionEngine (SisFall-calibrated spike=7.5, stillness=15.0)
- ECG anomaly — EcgAnomalyDetector (RMSSD + RR intervals, AFib sens=0.101)
- Inactivity monitoring — InactivityMonitor (motion gate)
- Caregiver alert triage — CaregiverAlertPolicy (4-level triage)
- BP estimation — BloodPressureEstimator (linear HR+temp model, flagged as estimated)

## Future (P2, post-pilot)

- ECG anomaly: 1D-CNN TFLite model needed for AFib sensitivity >80%
- Caregiver alert triage: classifier after pilot data collection
- Dehydration: lab-validated model after controlled study
- Overexertion: MIMIC-III validated model
- BP estimation: calibrated PPG/ECG feature model
