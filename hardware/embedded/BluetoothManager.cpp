#include "BluetoothManager.h"
#include "BluetoothSerial.h"
#include <ArduinoJson.h>

BluetoothSerial SerialBT;

void initBluetooth(String deviceName) {
    SerialBT.begin(deviceName);
    Serial.println("Biometric Bluetooth Hub Online.");
}

// Runs 256 times per second. Highly optimized format: H,[ECG],[RED],[IR]
void sendHighFrequencyPacket(BiometricData data) {
    if (data.ecgLeadsOff) {
        SerialBT.println("H,LEADSOFF,0,0");
    } else {
        // Compact CSV-styled representation to save airtime overhead
        SerialBT.printf("H,%d,%u,%u\n", data.ecgRaw, data.ppgRed, data.ppgIR);
    }
}

// Runs 1 time per second. Transmits the full telemetry matrix via JSON
void sendLowFrequencyPacket(BiometricData data) {
    StaticJsonDocument<256> doc;
    doc["hr"] = data.heartRate;
    doc["spo2"] = data.spo2;
    doc["skin_t"] = data.skinTemp;
    doc["sweat_h"] = data.ambientHum;
    doc["amb_t"] = data.ambientTemp;

    String jsonPayload;
    serializeJson(doc, jsonPayload);
    
    // Prefix 'L:' specifies Low-Frequency payload bundle to mobile parser
    SerialBT.print("L:" + jsonPayload + "\n");
}
