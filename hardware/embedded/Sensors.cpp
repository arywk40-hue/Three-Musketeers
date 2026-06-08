#include "Sensors.h"
#include <Wire.h>
#include "MAX30105.h"       // SparkFun MAX3010X Library
#include <Adafruit_TMP117.h>
#include <Adafruit_SHT4x.h>

// Pin Configurations
#define ECG_ANALOG_PIN  34
#define ECG_LO_PLUS     25
#define ECG_LO_MINUS    26

// Sensor Instances
MAX30105 particleSensor;
Adafruit_TMP117 tmp117;
Adafruit_SHT4x sht40;

unsigned long lastI2CUpdate = 0;
const unsigned long I2C_INTERVAL = 1000; // Update slow sensors every 1 second

void initSensors() {
    Wire.begin(21, 22); // Custom I2C mappings: SDA = 21, SCL = 22
    Wire.setClock(400000); // Elevate to 400kHz Fast I2C Bus mode

    // 1. Setup AD8232 Leads Detection
    pinMode(ECG_LO_PLUS, INPUT);
    pinMode(ECG_LO_MINUS, INPUT);

    // 2. Initialize MAX30102 Pulse Oximeter
    if (!particleSensor.begin(Wire, I2C_SPEED_FAST)) {
        Serial.println("Warning: MAX30102 not detected!");
    } else {
        // Configure MAX30102 with optimized settings for PPG extraction
        byte ledBrightness = 60; // Options: 0=Off to 255=50mA
        byte sampleAverage = 4;  // Options: 1, 2, 4, 8, 16, 32
        byte ledMode = 2;        // Options: 1 = Red only, 2 = Red + IR
        int sampleRate = 100;    // Options: 50, 100, 200, 400, 800
        int pulseWidth = 411;    // Options: 69, 118, 215, 411
        int adcRange = 4096;     // Options: 2048, 4096, 8192, 16384
        particleSensor.setup(ledBrightness, sampleAverage, ledMode, sampleRate, pulseWidth, adcRange);
    }

    // 3. Initialize High-Precision Clinical Temp Sensor
    if (!tmp117.begin()) {
        Serial.println("Warning: TMP117 not detected!");
    }

    // 4. Initialize Humidity Sensor (Sweat metrics)
    if (!sht40.begin()) {
        Serial.println("Warning: SHT40 not detected!");
    } else {
        sht40.setPrecision(SHT4X_HIGH_PRECISION);
        sht40.setHeater(SHT4X_NO_HEATER);
    }
}

// Read raw ECG data instantly (Executes at 256Hz within loop)
void sampleECG(BiometricData &data) {
    if ((digitalRead(ECG_LO_PLUS) == 1) || (digitalRead(ECG_LO_MINUS) == 1)) {
        data.ecgLeadsOff = true;
        data.ecgRaw = 0;
    } else {
        data.ecgLeadsOff = false;
        data.ecgRaw = analogRead(ECG_ANALOG_PIN);
    }

    // Constantly pull raw optical PPG data into the packet from sensor FIFO
    if (particleSensor.available()) {
        data.ppgRed = particleSensor.getRed();
        data.ppgIR = particleSensor.getIR();
    }
}

// Asynchronous background monitoring for slow-changing vitals
void updateI2CSensors(BiometricData &data) {
    unsigned long currentMillis = millis();
    if (currentMillis - lastI2CUpdate >= I2C_INTERVAL) {
        lastI2CUpdate = currentMillis;

        // Fetch TMP117 High Precision Skin Temp
        sensors_event_t tempEvent;
        tmp117.getEvent(&tempEvent);
        data.skinTemp = tempEvent.temperature;

        // Fetch SHT40 Relative Humidity
        sensors_event_t humidity, ambient;
        sht40.getEvent(&humidity, &ambient);
        data.ambientHum = humidity.relative_humidity;
        data.ambientTemp = ambient.temperature;

        // Embedded processing placeholders (SpO2, derived BP, Respiratory rate calculations)
        data.heartRate = 72.0; 
        data.spo2 = 98.5;      
    }
}
