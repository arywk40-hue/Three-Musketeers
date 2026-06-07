# Firmware — ESP32-C3 (NimBLE-Arduino)

ElderCare Guardian BLE GATT server for the prototype wearable.

> I2C sensor reading (MAX30102 + MPU-6050) is a Phase 2 task.
> This sketch ships synthetic data so the Android app's BLE pipeline can
> be exercised end-to-end. All custom characteristic UUIDs match
> `apps/android/app/src/main/java/com/smartsuit/ble/SmartSuitBleContract.kt`.

---

## Wiring

| Signal | Net        | Pin / bus  | Notes                               |
|--------|------------|------------|-------------------------------------|
| MCU    | ESP32-C3   | —          | Dev Module, 3.3 V                    |
| SDA    | I²C data   | GPIO 6     | 3.3 V, 4.7 kΩ pull-up to VCC        |
| SCL    | I²C clock  | GPIO 7     | 3.3 V, 4.7 kΩ pull-up to VCC        |
| VCC    | Sensor VDD | 3.3 V      | Both MAX30102 and MPU-6050           |
| GND    | Common GND | GND        | Star-ground at MCU                   |
| INT    | MAX30102   | (unused)   | Polling mode for prototype           |
| AD0    | MPU-6050   | GND        | I²C address 0x68                     |
| SOS    | Button     | GPIO 9     | Button to GND, `INPUT_PULLUP`        |

MAX30102 and MPU-6050 share the same I²C bus (SDA = GPIO6, SCL = GPIO7).
Phase 2 firmware will add the I²C drivers and replace the synthetic block in
`loop()` with real sensor reads.

SOS button is active-low. Pressing it pulls GPIO9 to GND; the firmware
mirrors this state onto the `SOS_STATE` characteristic (0 = off, 1 = active)
and drives `DEVICE_STATE` to `2` (Urgent).

---

## Flash steps

1. Install **Arduino IDE 2.x** (or update if you already have it).
2. Add ESP32 board support:
   - File → Preferences → Additional board URLs:
     `https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json`
   - Tools → Board → Board Manager → search "esp32" → install **esp32 by Espressif Systems**, package version **2.x**.
3. Install the **NimBLE-Arduino** library:
   - Tools → Manage Libraries → search "NimBLE-Arduino" → install the latest **1.x** release.
4. Select board: **Tools → Board → ESP32 Arduino → ESP32C3 Dev Module**.
5. Open `main.ino` and flash (Upload button, or Ctrl/Cmd+U).
6. Open **Serial Monitor** at 115200 baud to confirm:
   - "ElderCare Guardian — ESP32-C3 BLE server starting..."
   - "BLE advertising started as 'ElderCare_v1'."

Once flashed, the device advertises as **ElderCare_v1**. The Android app's
**Readiness** tab will show the device in the BLE scanner; tapping **Connect**
subscribes to every notify characteristic and the app dashboard starts
showing live HR, ECG window, IMU magnitude, SOS state, humidity, and
respiratory rate.

---

## Phase 2 — real sensor integration

Replace the synthetic block at the top of `loop()` with:

```cpp
// MAX30102 — poll HR + SpO2 over I²C (0x57)
readMax30102(hrBpm, spo2Pct);

// MPU-6050 — read accel + gyro (0x68)
readMpu6050(ax, ay, az, gx, gy, gz);
```

The notification payloads and timings stay identical, so the Android
parser and dashboard continue to work without changes.
