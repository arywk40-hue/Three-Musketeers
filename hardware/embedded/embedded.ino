#include "Sensors.h"
#include "BluetoothManager.h"

BiometricData vMedStream;

unsigned long lastEcgSampleMicros = 0;
const unsigned long ECG_SAMPLE_PERIOD_MICROS = 3906; // 1,000,000 micros / 256 Hz

unsigned long lastLowFreqMillis = 0;

void setup() {
    Serial.begin(115200);
    
    initSensors();
    initBluetooth("ESP32_Vitals_Monitor");
    
    lastEcgSampleMicros = micros();
}

void loop() {
    unsigned long currentMicros = micros();
    unsigned long currentMillis = millis();

    // 1. High Frequency Task Loop: Triggers strictly at 256 Hz
    if (currentMicros - lastEcgSampleMicros >= ECG_SAMPLE_PERIOD_MICROS) {
        lastEcgSampleMicros += ECG_SAMPLE_PERIOD_MICROS; // Catch up clock anchor
        
        sampleECG(vMedStream);
        sendHighFrequencyPacket(vMedStream);
    }

    // 2. Low Frequency Task Loop: Triggers asynchronously at 1 Hz
    if (currentMillis - lastLowFreqMillis >= 1000) {
        lastLowFreqMillis = currentMillis;
        
        updateI2CSensors(vMedStream);
        sendLowFrequencyPacket(vMedStream);
    }
}
