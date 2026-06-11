# Firmware

Firmware exposes the ElderCare Guardian BLE profile from the MCU.

Prototype target:
- ESP32-C3 with Arduino framework + NimBLE-Arduino (ESP-IDF via Arduino core).

Product target:
- nRF5340 with Zephyr RTOS.

Current status (June 2026):
- ✅ Advertises as `ElderCare_v1`
- ✅ Standard SIG services: Battery (0x180F), Heart Rate (0x180D), PLX/SpO2 (0x1822), Health Thermometer (0x1809)
- ✅ Custom ElderCare Service (12345678-1234-5678-1234-567812345678) with 8 characteristics
- ✅ MAX30102 HR + SpO2 with beat detection and Maxim algorithm
- ✅ MPU-6050 IMU (6-axis accel + gyro)
- ✅ SOS button on GPIO 9 (active-low)
- ✅ Battery ADC on GPIO 4 with piecewise LiPo discharge curve
- ✅ BLE security: bonding + MITM with numeric comparison (DISPLAY_YESNO)
- ✅ Synthetic fallback for all sensors — BLE pipe stays alive without hardware
- ✅ Software watchdog (esp_task_wdt) for I²C bus lockup protection
- ✅ MTU negotiation to 517 bytes (reserved — ECG payload deferred)
