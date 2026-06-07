# Build Log

## 2026-06-06

Started the product build from the planning repository.

Completed:
- Android app scaffold under `apps/android`.
- Compose app shell with Vitals, Safety, Caregiver, and Readiness tabs.
- Simulator data stream for live elder-care pitch demo reliability.
- BLE UUID contract for `ElderCare_v1`.
- Samsung Health bridge placeholder that avoids deprecated SDK usage.
- Product roadmap and one-month showcase plan.

Next:
- Open `apps/android` in Android Studio and confirm the simulator dashboard builds.
- Add runtime permission gate for BLE and body sensors.
- Implement BLE scanner/connect screen.
- Create ESP32-C3 firmware demo that advertises `ElderCare_v1`.
- Stream one real metric into the app before expanding to all sensors.

Review fixes:
- Simulator ECG window now emits 256 samples to match BLE and ML contracts.
- Samsung Health bridge state is observable through `StateFlow`.
- App state now starts behind `SmartSuitViewModel`.
- Supercap progress bar fills available card width.
- Samsung Health workflow/dependency docs now use the local AAR Data SDK path instead of the deprecated Maven/old Android SDK examples.

Product pivot:
- Revised direction from fitness smart suit to elderly safety and health wearable.
- Prototype scope changes to wrist/clip device with vitals, fall/SOS, inactivity, and caregiver alerts.
- Smart fabric, suit embedding, TEG/solar/piezo, rep counting, and form scoring are no longer required for the first showcase.
- Existing BLE, Samsung Health, simulator, GATT parsing, and Android foundations remain useful.

Forward build:
- Added runtime permission model for BLE scan/connect, body sensors, and activity recognition.
- Added permission request banner while keeping Demo mode usable.
- Added BLE scanner data source that looks for `ElderCare_v1`.
- Added discovered-device list and scan/stop/connect controls in the Ready tab.
- BLE can discover/connect at the GATT skeleton level; sensor frames remain simulator-backed until firmware streams the custom GATT characteristics.

Architecture build:
- Updated `architecture.md` Samsung section to the current local-AAR Data SDK path.
- Expanded BLE UUID contract to include Battery, PLX, Blood Pressure, Health Thermometer, and custom SmartSuit services.
- Added binary GATT parsers for HR, battery, float32 arrays, ECG, IMU, humidity, respiratory rate, and power.
- BLE data source now requests MTU 517, discovers services, enables CCCD notifications, and publishes parsed telemetry.
- Ready tab now shows parsed GATT telemetry beside scan/connect status.
