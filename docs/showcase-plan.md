# One Month Elder-Care Showcase Plan

## Week 1

Goal: Make the elder-safety app feel real and make the wearable testable.

- Scaffold Android app. `Done`
- Build simulator-backed live dashboard. `Done`
- Define BLE data model and packet contract. `Done`
- Validate MAX30102 and one IMU separately. `Code complete`
- Define fall/SOS/caregiver alert states. `Done`

Deliverable:
- Phone screen shows believable live health and safety metrics. ✅
- Serial logs exist for HR/SpO2 or IMU motion. ✅

Current app checkpoint:
- `apps/android` opens as the Android project root.
- Dashboard pivots to Vitals, Safety, Caregiver, and Readiness tabs.
- Demo mode streams live simulated vitals, fall risk, SOS, and alert data.
- BLE mode has runtime permission handling, scan/stop/connect controls, and a discovered-device list for `ElderCare_v1`.

## Week 2 — `Complete`

Goal: Connect at least one real elder-safety sensor path.

- BLE scan/connect skeleton. `Done` — `SmartSuitBleDataSource` with permission handling, filter, and connection state management.
- Stream one sensor value from ESP32-C3 to app. `Done` — HR, SpO2, IMU, SOS, battery all streamed.
- Validate IMU fall gesture stream. `Done` — `FallDetectionEngine` + `FallConfirmationBuffer` (2-frame temporal).
- Add caregiver alert state to UI. `Done` — `CaregiverAlertPolicy` → `SensorFrame.caregiverAlert` → Caregiver tab.

Deliverable:
- App can show either simulator metrics or one live BLE metric. ✅

## Week 3 — `Complete`

Goal: Make the pitch demo coherent.

- Fall/SOS demo flow. `Done` — simulator mode with fall/SOS triggers + physical hardware.
- Inactivity and abnormal-vitals rule-based alerts. `Done` — `InactivityMonitor`, `VitalsRiskMonitor`, `OverexertionModel`.
- Caregiver alert timeline. `Done` — `AlertHistoryTracker` → `AlertEventEntity` → Room → timeline UI.
- Package firmware demo with fixed GATT service names. `Done` — `ElderCare_v1` with all 10 characteristics.
- Prepare fallback simulator recording. `Done` — simulator mode always available.

Deliverable:
- End-to-end demo script can be rehearsed. ✅

## Week 4 — `In Progress`

Goal: Polish and rehearse.

- Stabilize app UI. `Ongoing`
- Add onboarding and permission states. `Done` — runtime permission banner + demo mode.
- Record backup demo video. `Pending`
- Prepare Samsung Health deployment story. `Done` — `NeedsPartnerApproval` state + developer.samsung.com link.
- Freeze showcase branch. `Pending`

Deliverable:
- Pitch-ready app build and physical bench demo.

## Pitch Demo Script

1. Open ElderCare Guardian app.
2. Show live HR, SpO2, temperature, and motion state.
3. Trigger simulated fall or SOS event.
4. Show caregiver alert and acknowledgement flow.
5. Show BLE readiness and Samsung Health deployment path.
