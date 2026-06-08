# Build Log

## 2026-06-08

Architecture alignment pass.

Completed:
- architecture.md Layer 3 GATT profile corrected to elder-care contract (IMU_WRIST, SOS_STATE, FALL_RISK, DEVICE_STATE — old IMU_ELBOW_L/R, IMU_LUMBAR, POWER_MW removed).
- architecture.md Layer 4 SafetyEngine module map updated to reflect all 9 rule-based engines.
- architecture.md Layer 5 on-device inference pipeline updated (RepCounterModel, FormScorerModel removed; full elder-care engine pipeline shown).
- architecture.md Layer 4 PLX BLE pipe documented.
- SmartSuitBleTelemetry gains spo2Percent field.
- SmartSuitBleParser gains parsePlxContinuousMeasurement() for firmware's simplified PLX encoding.
- SmartSuitBleDataSource subscribes to PLX_CONTINUOUS_MEASUREMENT and parses SpO2 into telemetry.
- SensorFrameMerger uses BLE SpO2 when available, flows through VitalsRiskMonitor and OverexertionModel.
- SmartSuitViewModel init block adds Samsung Health 5-second write cadence wired to samsungBridge.writeVitals().
- Unit tests added: DehydrationRiskModelTest, VitalsRiskMonitorTest, BloodPressureEstimatorTest, InactivityMonitorTest.
- hardware/README.md clarifies that hardware/embedded/ uses Bluetooth Classic Serial (legacy) and firmware/esp32-c3/ uses NimBLE BLE GATT (current).

---

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
