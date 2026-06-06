# Build Log

## 2026-06-06

Started the product build from the planning repository.

Completed:
- Android app scaffold under `apps/android`.
- Compose app shell with Vitals, Workout, Power, and Readiness tabs.
- Simulator data stream for live pitch demo reliability.
- BLE UUID contract for `SmartSuit_v1`.
- Samsung Health bridge placeholder that avoids deprecated SDK usage.
- Product roadmap and one-month showcase plan.

Next:
- Open `apps/android` in Android Studio and confirm the simulator dashboard builds.
- Add runtime permission gate for BLE and body sensors.
- Implement BLE scanner/connect screen.
- Create ESP32-C3 firmware demo that advertises `SmartSuit_v1`.
- Stream one real metric into the app before expanding to all sensors.

Review fixes:
- Simulator ECG window now emits 256 samples to match BLE and ML contracts.
- Samsung Health bridge state is observable through `StateFlow`.
- App state now starts behind `SmartSuitViewModel`.
- Supercap progress bar fills available card width.
- Samsung Health workflow/dependency docs now use the local AAR Data SDK path instead of the deprecated Maven/old Android SDK examples.

Forward build:
- Added runtime permission model for BLE scan/connect, body sensors, and activity recognition.
- Added permission request banner while keeping Demo mode usable.
- Added BLE scanner data source that looks for `SmartSuit_v1`.
- Added discovered-device list and scan/stop/connect controls in the Ready tab.
- BLE can discover/connect at the GATT skeleton level; sensor frames remain simulator-backed until firmware streams the custom GATT characteristics.
