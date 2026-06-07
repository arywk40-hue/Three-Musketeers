# ElderCare Guardian Product Roadmap

This roadmap treats the revised elderly-care wearable as a real product, with a fast showcase build first and a Samsung-ready deployment track after it.

## Targets

| Target | Timeline | Outcome |
| --- | ---: | --- |
| Pitch showcase | 4 weeks | Polished elder-safety app, simulated/live BLE data path, fall/SOS demo |
| Field MVP | 8-12 weeks | Real BLE sensor ingestion, local history, caregiver alerts, Samsung Health development mode |
| Product-like pilot | 4-6 months | Stable wearable hardware, calibrated sensors, validated alerts, Samsung partner path |

## Month 1 Showcase Scope

The showcase build should convince judges and partners that the system improves elderly safety without waiting for every hardware risk to be solved.

Build:
- Android dashboard with live HR, SpO2, temperature, motion state, fall risk, SOS, and caregiver alert status.
- Simulator mode for pitch reliability.
- BLE repository interface ready for firmware data.
- Hardware proof: ESP32-C3/nRF5340 wrist or clip wearable with MAX30102 and IMU.
- Sensor proof: HR/SpO2 and motion/fall simulation validated.
- Demo script with failure-safe fallback to simulated data.

Defer:
- Samsung Health write distribution.
- Fully trained clinical-grade models.
- Smart fabric, suit embedding, and washable textile integration.
- Medical claims.

## Months 2-3 Deployment Scope

Build:
- Real BLE GATT client connected to `ElderCare_v1` firmware.
- Local Room history.
- TFLite inference wrappers with replaceable model files.
- Samsung Health Data SDK integration behind a bridge module.
- Caregiver contact, alert timeline, and emergency acknowledgement flow.
- Developer-mode read/write testing on a real Android phone.
- Consent, privacy, onboarding, and error states.

Required external dependency:
- Samsung Health Data SDK v1.1.0 AAR from Samsung Developer Portal.
- Samsung Health app version 6.30.2 or later.
- Real device testing; Samsung's Data SDK does not support emulators.
- Partner request before write access can be tested for distribution.

## Months 4-6 Product Hardening

Build:
- Custom PCB or stable perf-board wearable revision.
- Enclosure, charging, comfort, and strap/clip durability.
- Power profiling across daily use and overnight monitoring.
- Sensor artifact filtering during walking, sitting, lying down, and falls.
- Data collection protocol with labeled daily activity and fall-simulation sessions.
- Model evaluation reports and thresholds.
- Pilot test with elderly-care scenarios and caregiver response flows.

## Engineering Rule

Every milestone must preserve two paths:
- `demo` path: reliable enough for pitch day.
- `real` path: strict enough for product validation.

The app should always be able to switch between simulator data and BLE data so demos never depend on unstable lab hardware.
