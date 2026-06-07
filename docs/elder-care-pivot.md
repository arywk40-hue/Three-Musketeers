# Elder Care Pivot

## Revised Product

The project pivots from a fitness-focused smart workout suit to an elderly safety and health monitoring wearable.

Working product name: **ElderCare Guardian**.

The prototype becomes a compact wrist/clip wearable that monitors core vitals, detects possible falls or distress, and notifies caregivers through the Android app. Samsung Health remains useful for long-term health history, but the pitch focuses on safety, independence, and caregiver peace of mind.

## Why This Is Stronger

- **Higher impact:** Elderly care is a real social problem for families, hospitals, and assisted-living centers.
- **Simpler prototype:** No need to implement conductive fabric, suit stitching, washable electronics, solar shoulders, or piezo shoe inserts for the showcase.
- **Clearer story:** The product helps an elderly person live independently while family members get timely alerts.
- **Better judging angle:** Safety, healthcare access, and aging population impact are easier to defend than fitness analytics.
- **Reuse of current work:** BLE, Samsung Health, vitals, ECG/PPG, IMU, permissions, simulator mode, and the Android dashboard all still apply.

## Prototype Scope

Build:
- Wrist/clip wearable using ESP32-C3 or nRF5340.
- MAX30102 for heart rate and SpO2.
- IMU for fall detection, inactivity, and abnormal movement.
- Optional TMP117 or skin temperature sensor.
- Phone app with live vitals, safety status, caregiver alert state, and device readiness.
- Simulator mode for reliable pitch demos.
- BLE GATT path for one real sensor stream.

Defer:
- Full smart fabric suit.
- TEG/solar/piezo energy harvesting.
- Rep counting and workout form scoring.
- Medical diagnosis claims.
- Production-grade fall detection validation.

## First Demo Story

1. Elderly user wears the band or clip.
2. App shows live heart rate, SpO2, temperature, and motion state.
3. Device detects a simulated fall or SOS event.
4. App shows caregiver alert with location-ready status.
5. Samsung Health path is explained as long-term vitals history.

## Product Pillars

- **Safety:** fall detection, SOS, inactivity, abnormal vitals.
- **Health:** HR, SpO2, temperature, respiratory trend, ECG path later.
- **Caregiver:** alerts, check-ins, status timeline.
- **Simplicity:** wearable band/clip, no complex fabric for prototype.
