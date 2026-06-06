# Firmware

Firmware will expose the SmartSuit BLE profile from the MCU.

Prototype target:
- ESP32-C3 with ESP-IDF.

Product target:
- nRF5340 with Zephyr RTOS.

Immediate firmware milestones:
- Advertise as `SmartSuit_v1`.
- Expose standard Heart Rate service for early app testing.
- Add custom SmartSuit service UUID `12345678-1234-5678-1234-567812345678`.
- Stream one real sensor first, then expand to ECG, IMU, humidity, respiratory rate, and power telemetry.
