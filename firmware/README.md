# Firmware Scope

Firmware must turn wearable sensor streams into real-time health and safety intelligence.

## Runtime Responsibilities
- Continuous sensor acquisition and normalization.
- Feature extraction for health, gait, posture, mobility, and environment.
- Real-time inference hooks for ML alert classes.
- BLE transmission to mobile app with low-latency risk updates.

## Safety Logic
- Trigger preventive vibration alerts on elevated fall-risk states.
- Trigger emergency state on fall/unconsciousness/critical vital anomalies.
- Package emergency payload with timestamped telemetry snapshots.

## Target Alert Classes
- ECG anomaly: Normal / AFib / Tachycardia / Bradycardia
- Fall risk: Low / Medium / High
- Dehydration risk: Low / Medium / High
- Mobility decline: Stable / Declining / Critical
- Fatigue level: Safe / Caution / High Risk
- Cognitive decline: Normal / Monitor / Concern
- Sleep quality: Good / Fair / Poor
