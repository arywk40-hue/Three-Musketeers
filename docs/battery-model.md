# Battery Model

## Overview

The ElderCare Guardian wearable is powered by a single-cell LiPo battery
(3.0 V empty → 4.2 V full, 3.7 V nominal). The firmware measures the cell
voltage on every loop tick via an ESP32-C3 ADC pin and broadcasts a
percentage (0–100) to the Android app over the standard Bluetooth SIG
**Battery Service** (`0x180F` / `0x2A19`).

The Android app maps the value to a `SensorFrame.batteryPercent` field and
uses it both for the UI gauge and for the `CaregiverAlertPolicy` low-
battery rule (`batteryPercent < 15` → `Check`).

## Hardware

| Component        | Value           | Notes                                    |
|------------------|-----------------|------------------------------------------|
| Cell             | 1S LiPo         | 3.0 V empty, 4.2 V full, 3.7 V nominal   |
| Top resistor R1  | 100 kΩ          | VBAT → ADC tap                           |
| Bottom resistor R2 | 200 kΩ        | ADC tap → GND                            |
| ADC pin          | GPIO 4 (ADC1_CH4) | Free pin on the dev module; check PCB |
| Quiescent draw   | 0.667 × Vbat / 300 kΩ | ~6.7 µA at 3.0 V → ~9.3 µA at 4.2 V |

Divider ratio: `R2 / (R1 + R2) = 200 / 300 = 0.667`

This puts the ADC input in `[2.00 V, 2.80 V]` for the full LiPo range,
safely inside the ESP32-C3's 3.3 V ADC reference.

## ADC math

```
vAdc   = (raw / 4095) × 3.3 V
vBat   = vAdc / 0.667
percent = piecewise_lookup(vBat)    ← 16-entry table, see below
```

The firmware averages **8 consecutive ADC samples** per loop tick to
smooth switching noise. With a 1 Hz loop, this is sufficient resolution
and keeps the I²C bus and BLE notify path responsive.

## LiPo discharge curve (piecewise-linear table)

The firmware uses a 16-entry piecewise-linear lookup table in `vbatToPercent()`,
approximating a real LiPo discharge curve from bench measurements:

| Voltage | Reported % | Segment |
|---------|-----------|---------|
| 4.20 V  | 100       | Full    |
| 4.10 V  | 95        |         |
| 4.00 V  | 88        |         |
| 3.90 V  | 78        |         |
| 3.80 V  | 65        |         |
| 3.75 V  | 55        | Plateau |
| 3.70 V  | 45        | (long)  |
| 3.65 V  | 33        |         |
| 3.60 V  | 22        |         |
| 3.55 V  | 15        | Knee    |
| 3.50 V  | 10        | (fast)  |
| 3.45 V  | 7         |         |
| 3.40 V  | 5         |         |
| 3.35 V  | 3         |         |
| 3.30 V  | 2         |         |
| 3.20 V  | 1         | Near empty |
| ≤ 3.0 V | 0         | Empty   |

This replaces the earlier single-segment linear map, which over-reported
percent in the 3.6–3.8 V plateau by up to +34 %.

## Threshold

`BATT_LOW_PCT = 15` is the low-battery threshold. It is duplicated in
two places:

1. **Firmware** (`firmware/esp32-c3/main.ino:100`) — used for the
   `DeviceAlert` state on the `DEVICE_STATE` characteristic. (Reserved
   for a future task that drives `DEVICE_STATE` from battery level.)
2. **Android** (`apps/android/.../ml/CaregiverAlertPolicy.kt`) — used in
   the `isCheck` rule. When `batteryPercent != null && batteryPercent <
   15`, the policy returns `Check` and the alert appears in the
   caregiver timeline.

If you change the threshold, change both locations. A future refactor
should expose the threshold as a single shared constant.

## Calibration procedure (Ariyan, on-bench)

The piecewise table above is a reference approximation. To calibrate against
your specific cell:

1. Fully charge the LiPo to 4.20 V (verified with a calibrated
   bench DMM at the cell terminals, not at the PCB — there is a small
   IR drop across the protection circuit).
2. Flash the firmware and open the Serial Monitor at 115200 baud.
3. Add temporary debug prints to `readBatteryPercent()`:
   ```
   Serial.printf("ADC raw=%.0f vAdc=%.3f vBat=%.3f pct=%d\n",
                 raw, vAdc, vBat, percent);
   ```
4. Record the printed `vBat` and `pct` at 5-minute intervals as the
   cell drains from 4.20 V down to 3.00 V under a constant dummy load
   (~50 mA, matching the average draw the wearable pulls in normal
   operation).
5. Build a `(vBat → truePct)` lookup table from the recorded points.
6. Update the `CAL_TABLE` array in `main.ino` `vbatToPercent()` with
   your measured breakpoints. The piecewise interpolator will adapt.
7. Capture the open-circuit voltage (no load) at 3.7 V — this is the
   value the firmware should report when the wearable is at rest and
   the load is disconnected.
8. Update the low-battery threshold if Ariyan's real discharge data
   shows a steeper knee at higher voltages than 3.5 V (some LiPo
   chemistries age this way).

## Future work

- **Calibrated cell model** — once the on-bench data is in, replace
  the linear map with the piecewise table. The Android `Caregiver
  AlertPolicy` is already value-driven, so no policy changes are
  needed.
- **Supercap backup monitoring** — the wearable's supercap is a
  short-duration backup; a separate ADC channel can read its voltage
  and populate `SensorFrame.supercapPercent` (currently a synthetic
  value driven by the simulator).
- ~~**Sleep-mode battery management** — when no BLE client is
  subscribed, the firmware can drop to deep-sleep between loop ticks
  to extend runtime. Wake on the SOS button GPIO interrupt.~~
  ✓ Done — Session 4 implements `esp_light_sleep_start()` (light sleep, not
  deep sleep) with a 5 s timer wake-up when no client is connected,
  and `delay(900)` with implicit modem sleep when a client is connected.
- **OTA update of the discharge table** — once the cell ages, the
  curve drifts. A future revision can store the lookup table in NVS
  and let the app push an updated table over a custom characteristic.
