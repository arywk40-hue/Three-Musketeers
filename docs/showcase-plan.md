# One Month Elder-Care Showcase Plan

## Week 1

Goal: Make the elder-safety app feel real and make the wearable testable.

- Scaffold Android app. `Started`
- Build simulator-backed live dashboard. `Started`
- Define BLE data model and packet contract. `Started`
- Validate MAX30102 and one IMU separately.
- Define fall/SOS/caregiver alert states.

Deliverable:
- Phone screen shows believable live health and safety metrics.
- Serial logs exist for HR/SpO2 or IMU motion.

Current app checkpoint:
- `apps/android` opens as the Android project root.
- Dashboard pivots to Vitals, Safety, Caregiver, and Readiness tabs.
- Demo mode streams live simulated vitals, fall risk, SOS, and alert data.
- BLE mode has runtime permission handling, scan/stop/connect controls, and a discovered-device list for `ElderCare_v1`.

## Week 2

Goal: Connect at least one real elder-safety sensor path.

- Implement BLE scan/connect skeleton.
- Stream one sensor value from ESP32-C3 to app.
- Validate IMU fall gesture stream.
- Add caregiver alert state to UI.

Deliverable:
- App can show either simulator metrics or one live BLE metric.

## Week 3

Goal: Make the pitch demo coherent.

- Add fall/SOS demo flow.
- Add inactivity and abnormal-vitals rule-based alerts.
- Add caregiver alert timeline.
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

1. Open ElderCare Guardian app.
2. Show live HR, SpO2, temperature, and motion state.
3. Trigger simulated fall or SOS event.
4. Show caregiver alert and acknowledgement flow.
5. Show BLE readiness and Samsung Health deployment path.
