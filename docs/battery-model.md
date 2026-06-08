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
percent = (vBat - 3.0) / (4.2 - 3.0) × 100, clamped to [0, 100]
```

The firmware averages **8 consecutive ADC samples** per loop tick to
smooth switching noise. With a 1 Hz loop, this is sufficient resolution
and keeps the I²C bus and BLE notify path responsive.

## LiPo discharge curve (approximate)

The current firmware uses a single-segment linear map between
`vBat = 3.0 V` (0%) and `vBat = 4.2 V` (100%). This is good enough for
the prototype display but doesn't match real LiPo behaviour:

| Voltage | True % (typical) | Linear-map % | Error |
|---------|------------------|--------------|-------|
| 4.20 V  | 100              | 100          | 0     |
| 3.95 V  | ~85              | 79           | -6    |
| 3.85 V  | ~65              | 71           | +6    |
| 3.75 V  | ~45              | 62           | +17   |
| 3.65 V  | ~25              | 54           | +29   |
| 3.55 V  | ~12              | 46           | +34   |
| 3.30 V  | ~3               | 25           | +22   |
| 3.00 V  | 0                | 0            | 0     |

The plateau around 3.7 V is long and should map to a narrow percentage
band; the linear map stretches it across half the gauge, which is
visually misleading. Once Ariyan measures the actual cell on the bench
(see calibration procedure below), replace the linear map with a
piecewise table of `(voltage → percent)` breakpoints.

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
6. Replace `vbatToPercent()` in the firmware with a piecewise lookup
   that interpolates between the recorded points. The single-segment
   linear function can stay as a fallback if the table is empty.
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
- **Sleep-mode battery management** — when no BLE client is
  subscribed, the firmware can drop to deep-sleep between loop ticks
  to extend runtime. Wake on the SOS button GPIO interrupt.
- **OTA update of the discharge table** — once the cell ages, the
  curve drifts. A future revision can store the lookup table in NVS
  and let the app push an updated table over a custom characteristic.
