# One Month Showcase Plan

## Week 1

Goal: Make the app feel real and make the hardware testable.

- Scaffold Android app. `Started`
- Build simulator-backed live dashboard. `Started`
- Define BLE data model and packet contract. `Started`
- Validate AD8232 ECG and MAX30102 separately.
- Start TEG to boost converter bench setup.

Deliverable:
- Phone screen shows believable live health and power metrics.
- Serial logs exist for at least ECG or SpO2.

Current app checkpoint:
- `apps/android` opens as the Android project root.
- Dashboard has Vitals, Workout, Power, and Readiness tabs.
- Demo mode streams live simulated ECG, vitals, workout, and power data.
- BLE mode exists as a product surface; scanner implementation comes next.

## Week 2

Goal: Connect at least one real sensor path.

- Implement BLE scan/connect skeleton.
- Stream one sensor value from ESP32-C3 to app.
- Add ECG chart placeholder or waveform rendering path.
- Validate one IMU motion stream.
- Measure supercap charge/discharge under TEG/solar.

Deliverable:
- App can show either simulator metrics or one live BLE metric.

## Week 3

Goal: Make the pitch demo coherent.

- Add rep counter demo flow.
- Add posture/fatigue/dehydration rule-based alerts.
- Add power screen with TEG/solar/supercap fields.
- Package firmware demo with fixed GATT service names.
- Prepare fallback simulator recording for pitch reliability.

Deliverable:
- End-to-end demo script can be rehearsed.

## Week 4

Goal: Polish and rehearse.

- Stabilize app UI.
- Add onboarding and permission states.
- Record backup demo video.
- Prepare Samsung Health deployment story.
- Freeze showcase branch.

Deliverable:
- Pitch-ready app build and physical bench demo.

## Pitch Demo Script

1. Open Smart Suit app.
2. Show live vitals and ECG waveform.
3. Do 10 curls and show rep count/form score.
4. Cover solar panels and show power still flowing from TEG.
5. Trigger posture/fatigue alert.
6. Explain Samsung Health deployment path.
