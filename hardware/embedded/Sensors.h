#ifndef SENSORS_H
#define SENSORS_H

#include <Arduino.h>

struct BiometricData {
    // High Frequency Waveforms
    int ecgRaw;             // 256 Hz stream
    uint32_t ppgRed;        // Raw Red light from MAX30102
    uint32_t ppgIR;         // Raw Infrared light from MAX30102

    // Processed & Static Metrics
    float heartRate;        // BPM calculated from MAX30102
    float spo2;             // Blood oxygen level %
    float skinTemp;         // Precision clinical temperature from TMP117
    float ambientHum;       // Sweat monitoring via SHT40
    float ambientTemp;      // SHT40 secondary temperature
    
    bool ecgLeadsOff;       // AD8232 status check (L0+ / L0-)
};

void initSensors();
void updateI2CSensors(BiometricData &data);
void sampleECG(BiometricData &data);

#endif
