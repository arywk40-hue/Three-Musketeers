# Smart Workout Suit Product Roadmap

This roadmap treats the suit as a real wearable product, with a fast showcase build first and a Samsung-ready deployment track after it.

## Targets

| Target | Timeline | Outcome |
| --- | ---: | --- |
| Pitch showcase | 4 weeks | Polished app demo, simulated/live BLE data path, power and sensor proof points |
| Field MVP | 8-12 weeks | Real BLE sensor ingestion, local history, first ML alerts, Samsung Health integration in development mode |
| Product-like pilot | 4-6 months | Stable wearable hardware, washable modular design, calibrated sensors, validated ML, Samsung partner path |

## Month 1 Showcase Scope

The showcase build should convince judges and partners that the system works end-to-end without waiting for every hardware risk to be solved.

Build:
- Android dashboard with live ECG, HR, SpO2, skin temperature, humidity, posture, rep count, fatigue, and power telemetry.
- Simulator mode for pitch reliability.
- BLE repository interface ready for firmware data.
- Hardware bench proof: TEG/solar/piezo to regulated rail and supercap.
- Sensor bench proof: AD8232, MAX30102, one IMU, TMP117/SHT40 individually validated.
- Demo script with failure-safe fallback to simulated data.

Defer:
- Samsung Health write distribution.
- Fully trained clinical-grade models.
- Washable final textile integration.
- Medical claims.

## Months 2-3 Deployment Scope

Build:
- Real BLE GATT client connected to SmartSuit_v1 firmware.
- Local Room history.
- TFLite inference wrappers with replaceable model files.
- Samsung Health Data SDK integration behind a bridge module.
- Developer-mode read/write testing on a real Android phone.
- Consent, privacy, onboarding, and error states.

Required external dependency:
- Samsung Health Data SDK v1.1.0 AAR from Samsung Developer Portal.
- Samsung Health app version 6.30.2 or later.
- Real device testing; Samsung's Data SDK does not support emulators.
- Partner request before write access can be tested for distribution.

## Months 4-6 Product Hardening

Build:
- Custom PCB or stable perf-board revision.
- Waterproof connectors and removable washable modules.
- Power profiling across indoor, outdoor, sweat, and motion conditions.
- Sensor artifact filtering during workouts.
- Data collection protocol with labeled exercise sessions.
- Model evaluation reports and thresholds.
- Pilot test with repeated workouts and recovery flows.

## Engineering Rule

Every milestone must preserve two paths:
- `demo` path: reliable enough for pitch day.
- `real` path: strict enough for product validation.

The app should always be able to switch between simulator data and BLE data so demos never depend on unstable lab hardware.
