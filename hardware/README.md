# Hardware

Hardware work is split into bench validation and a simple elder-care wearable prototype.

## Bench Validation

- MAX30102 SpO2 and heart rate.
- MPU-6050 or ICM-42688 IMU for fall and inactivity detection.
- TMP117 skin temperature.
- Push button or capacitive touch input for SOS.

## Wearable Integration

- Wrist band or clip enclosure.
- Optical sensor skin contact window.
- IMU mounted firmly to avoid loose-device false positives.
- USB rechargeable battery or small LiPo for the showcase.
- Optional removable strap for comfort testing.

## Embedded Firmware

The `hardware/embedded` folder contains the microcontroller firmware for the wearable prototype:

- `embedded.ino` - main Arduino sketch. It initializes sensors and Bluetooth, then runs two task loops:
  - high-frequency ECG sampling at 256 Hz,
  - low-frequency sensor updates at 1 Hz.
- `Sensors.h` / `Sensors.cpp` - sensor abstraction and data acquisition for ECG, MAX30102 pulse oximetry, TMP117 skin temperature, and SHT40 humidity/ambient temperature.
- `BluetoothManager.h` / `BluetoothManager.cpp` - Bluetooth Serial setup and packet transmission. High-frequency ECG/PPG packets are sent as compact CSV messages, while low-frequency telemetry is sent as JSON.

Required firmware libraries include `BluetoothSerial`, `ArduinoJson`, `MAX30105`, `Adafruit_TMP117`, and `Adafruit_SHT4x`.

### Example Firmware Snippets

`embedded.ino` main loop:

```cpp
void loop() {
    unsigned long currentMicros = micros();
    unsigned long currentMillis = millis();

    if (currentMicros - lastEcgSampleMicros >= ECG_SAMPLE_PERIOD_MICROS) {
        lastEcgSampleMicros += ECG_SAMPLE_PERIOD_MICROS;
        sampleECG(vMedStream);
        sendHighFrequencyPacket(vMedStream);
    }

    if (currentMillis - lastLowFreqMillis >= 1000) {
        lastLowFreqMillis = currentMillis;
        updateI2CSensors(vMedStream);
        sendLowFrequencyPacket(vMedStream);
    }
}
```

`Sensors.cpp` ECG sampling and slow sensor refresh:

```cpp
void sampleECG(BiometricData &data) {
    if ((digitalRead(ECG_LO_PLUS) == 1) || (digitalRead(ECG_LO_MINUS) == 1)) {
        data.ecgLeadsOff = true;
        data.ecgRaw = 0;
    } else {
        data.ecgLeadsOff = false;
        data.ecgRaw = analogRead(ECG_ANALOG_PIN);
    }

    if (particleSensor.available()) {
        data.ppgRed = particleSensor.getRed();
        data.ppgIR = particleSensor.getIR();
    }
}

void updateI2CSensors(BiometricData &data) {
    unsigned long currentMillis = millis();
    if (currentMillis - lastI2CUpdate >= I2C_INTERVAL) {
        lastI2CUpdate = currentMillis;
        tmp117.getEvent(&tempEvent);
        data.skinTemp = tempEvent.temperature;
        sht40.getEvent(&humidity, &ambient);
        data.ambientHum = humidity.relative_humidity;
        data.ambientTemp = ambient.temperature;
        data.heartRate = 72.0;
        data.spo2 = 98.5;
    }
}
```

`BluetoothManager.cpp` packet formatting:

```cpp
void sendHighFrequencyPacket(BiometricData data) {
    if (data.ecgLeadsOff) {
        SerialBT.println("H,LEADSOFF,0,0");
    } else {
        SerialBT.printf("H,%d,%u,%u\n", data.ecgRaw, data.ppgRed, data.ppgIR);
    }
}

void sendLowFrequencyPacket(BiometricData data) {
    StaticJsonDocument<256> doc;
    doc["hr"] = data.heartRate;
    doc["spo2"] = data.spo2;
    doc["skin_t"] = data.skinTemp;
    doc["sweat_h"] = data.ambientHum;
    doc["amb_t"] = data.ambientTemp;

    String jsonPayload;
    serializeJson(doc, jsonPayload);
    SerialBT.print("L:" + jsonPayload + "\n");
}
```

> The `embedded.ino` sketch must be uploaded to the microcontroller for the wearable prototype to operate.
