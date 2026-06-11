# Firmware — ESP32-C3 (NimBLE-Arduino)

ElderCare Guardian BLE GATT server for the prototype wearable.

> MAX30102 (HR + SpO2) and MPU-6050 (IMU) I²C drivers are integrated.
> If a sensor fails to initialise the firmware falls back to synthetic
> data so the BLE pipe and Android dashboard stay usable for testing.
> All custom characteristic UUIDs match
> `apps/android/app/src/main/java/com/eldercareguardian/ble/SmartSuitBleContract.kt`.

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
| VBAT   | Battery    | GPIO 4     | ADC1_CH4 via R1=100kΩ + R2=200kΩ divider |

MAX30102 and MPU-6050 share the same I²C bus (SDA = GPIO6, SCL = GPIO7).
The firmware reads both sensors on every loop tick; if either fails to
initialise, its synthetic fallback path keeps the BLE pipe alive.

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

### Power management

On each loop tick the firmware picks the sleep mode based on whether a BLE
client is connected:

| Connection state | Sleep mechanism | Duration | Power saving |
|---|---|---|---|
| **Client connected** | `delay(900)` — FreeRTOS idle / modem sleep. NimBLE controller stays active, processing connection events in hardware. | 900 ms | ~20 mA → ~15 mA |
| **No client** | `esp_light_sleep_start()` — CPU halted, BLE advertising resumes after wakeup. | 5 000 ms | ~20 mA → ~500 µA |

When no client is connected the MCU spends most of its time in light sleep,
reducing average current draw from ~20 mA to ~500 µA. The 5-second cadence is
adequate for a wearable that is not actively monitored; the device remains
discoverable because `startAdvertising()` is called in `onDisconnect()`.

TX power is set to `ESP_PWR_LVL_P3` (~0 dBm) in `setup()` — wrist-to-phone
range (~2 m) is sufficient, saving ~5 mA compared to `ESP_PWR_LVL_P9`.

---

## Firmware features

- **BLE GATT server** with NimBLE-Arduino, advertising as `ElderCare_v1`.
- **MAX30102** HR + SpO2 with beat detection and Maxim SpO2 algorithm (100-sample buffer).
- **MPU-6050** IMU with real accelerometer/gyro data.
- **SOS button** on GPIO 9 (active-low, INPUT_PULLUP).
- **Battery ADC** on GPIO 4 with 8-sample rolling average and piecewise LiPo curve.
- **BLE security:** bonding + MITM with numeric comparison (`DISPLAY_YESNO`) through `SecurityCallbacks`.
- **Synthetic fallback:** if a sensor fails to init, the characteristic notifies synthetic data so the Android app always sees a live stream.
