/*
 * ElderCare Guardian — ESP32-C3 BLE GATT server
 * Library: NimBLE-Arduino (Arduino Library Manager)
 * Board:   ESP32-C3 Dev Module
 *
 * Phase 2: real MAX30102 (HR + SpO2) and MPU-6050 (IMU) on the I²C bus.
 * Both sensors share GPIO 6 (SDA) / GPIO 7 (SCL). Each sensor is
 * initialised in setup() and a `*_ready` flag is set. If begin() /
 * testConnection() fails the synthetic fallback path is used so the
 * BLE pipe stays up and the Android dashboard does not break.
 *
 * SOS button is wired to GPIO 9 (active low, INPUT_PULLUP).
 * All custom characteristic UUIDs MUST match SmartSuitBleContract.kt.
 */

#include <Arduino.h>
#include <Wire.h>
#include <NimBLEDevice.h>
#include <NimBLEServer.h>
#include <NimBLEUtils.h>
#include <NimBLE2902.h>
#include <math.h>
#include "MAX30105.h"
#include "heartRate.h"
#include "spo2_algorithm.h"
#include "MPU6050.h"

// ─────────────────────────────────────────────────────────────────────────────
// Hardware pin map
// ─────────────────────────────────────────────────────────────────────────────
static const uint8_t SOS_BTN_PIN = 9;   // active low, INPUT_PULLUP
static const uint8_t I2C_SDA_PIN = 6;
static const uint8_t I2C_SCL_PIN = 7;

// ─────────────────────────────────────────────────────────────────────────────
// UUIDs — MUST match SmartSuitBleContract.kt exactly
// ─────────────────────────────────────────────────────────────────────────────
static const char* DEVICE_NAME = "ElderCare_v1";

// Standard SIG services
static const ble_uuid128_t UUID_BATTERY_SVC        = BLE_UUID128_INIT(0x0F, 0x18);                          // 0000180F-...
static const ble_uuid128_t UUID_BATTERY_LEVEL      = BLE_UUID128_INIT(0x19, 0x2A);                          // 00002A19-...
static const ble_uuid128_t UUID_HEART_RATE_SVC     = BLE_UUID128_INIT(0x0D, 0x18);                          // 0000180D-...
static const ble_uuid128_t UUID_HR_MEASUREMENT     = BLE_UUID128_INIT(0x37, 0x2A);                          // 00002A37-...

// Custom service 12345678-1234-5678-1234-567812345678
static const ble_uuid128_t UUID_CUSTOM_SVC = BLE_UUID128_INIT(
    0x78, 0x56, 0x34, 0x12,
    0x78, 0x56, 0x34, 0x12,
    0x34, 0x12, 0x78, 0x56,
    0x12, 0x34, 0x56, 0x78);

static const ble_uuid128_t UUID_ECG_RAW       = BLE_UUID128_INIT(0x79, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12, 0x34, 0x12, 0x78, 0x56, 0x12, 0x34, 0x56, 0x78); // ...5679
static const ble_uuid128_t UUID_IMU_WRIST     = BLE_UUID128_INIT(0x7a, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12, 0x34, 0x12, 0x78, 0x56, 0x12, 0x34, 0x56, 0x78); // ...567a
static const ble_uuid128_t UUID_SOS_STATE     = BLE_UUID128_INIT(0x7b, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12, 0x34, 0x12, 0x78, 0x56, 0x12, 0x34, 0x56, 0x78); // ...567b
static const ble_uuid128_t UUID_FALL_RISK     = BLE_UUID128_INIT(0x7c, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12, 0x34, 0x12, 0x78, 0x56, 0x12, 0x34, 0x56, 0x78); // ...567c
static const ble_uuid128_t UUID_HUMIDITY      = BLE_UUID128_INIT(0x7d, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12, 0x34, 0x12, 0x78, 0x56, 0x12, 0x34, 0x56, 0x78); // ...567d
static const ble_uuid128_t UUID_RESP_RATE     = BLE_UUID128_INIT(0x7e, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12, 0x34, 0x12, 0x78, 0x56, 0x12, 0x34, 0x56, 0x78); // ...567e
static const ble_uuid128_t UUID_DEVICE_STATE  = BLE_UUID128_INIT(0x7f, 0x56, 0x34, 0x12, 0x78, 0x56, 0x34, 0x12, 0x34, 0x12, 0x78, 0x56, 0x12, 0x34, 0x56, 0x78); // ...567f

// ─────────────────────────────────────────────────────────────────────────────
// Globals
// ─────────────────────────────────────────────────────────────────────────────
NimBLEServer*         g_server   = nullptr;
NimBLEService*        g_battSvc  = nullptr;
NimBLEService*        g_hrSvc    = nullptr;
NimBLEService*        g_customSvc= nullptr;

NimBLECharacteristic* g_battChr  = nullptr;
NimBLECharacteristic* g_hrChr    = nullptr;
NimBLECharacteristic* g_ecgChr   = nullptr;
NimBLECharacteristic* g_imuChr   = nullptr;
NimBLECharacteristic* g_sosChr   = nullptr;
NimBLECharacteristic* g_fallChr  = nullptr;
NimBLECharacteristic* g_humChr   = nullptr;
NimBLECharacteristic* g_respChr  = nullptr;
NimBLECharacteristic* g_stateChr = nullptr;

uint8_t g_battValue     = 88;
uint8_t g_sosValue      = 0;
uint8_t g_deviceState   = 0;
uint32_t g_tickCounter  = 0;

// ─────────────────────────────────────────────────────────────────────────────
// Sensor state
// ─────────────────────────────────────────────────────────────────────────────
static MAX30105 g_particleSensor;
static MPU6050  g_mpu;
static bool g_maxReady = false;     // MAX30102 detected and initialised
static bool g_imuReady = false;     // MPU-6050 detected and initialised

// Beat-to-beat interval history (circular buffer). Valid intervals are
// those in [250, 2000] ms, i.e. 30 – 240 bpm.
static const int BEAT_BUFFER_SIZE = 4;
static int  g_beatIntervals[BEAT_BUFFER_SIZE] = {0, 0, 0, 0};
static int  g_beatBufferIdx   = 0;
static int  g_validBeatCount  = 0;
static unsigned long g_lastBeatMs = 0;

// SpO2 sample buffer — must hold exactly 100 samples for the Maxim algorithm.
static const int SPO2_BUFFER_SIZE = 100;
static uint32_t g_irBuffer[SPO2_BUFFER_SIZE];
static uint32_t g_redBuffer[SPO2_BUFFER_SIZE];
static int  g_spo2BufferIdx = 0;
static int8_t g_spo2Percent = 0;
static int8_t g_spo2Valid   = 0;

// ─────────────────────────────────────────────────────────────────────────────
// Float packing helpers
// ─────────────────────────────────────────────────────────────────────────────
void packFloat(uint8_t* buf, int offset, float val) {
    memcpy(buf + offset, &val, sizeof(float));
}

void packFloatArray(uint8_t* buf, int offset, const float* values, int count) {
    for (int i = 0; i < count; i++) {
        packFloat(buf, offset + i * 4, values[i]);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Server callbacks
// ─────────────────────────────────────────────────────────────────────────────
class ServerCallbacks : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* server, ble_gap_conn_desc* desc) {
        Serial.printf("Client connected: %s\n", NimBLEAddress(desc->peer_ota_addr).toString().c_str());
    }

    void onDisconnect(NimBLEServer* server, ble_gap_conn_desc* desc) {
        Serial.printf("Client disconnected: %s\n", NimBLEAddress(desc->peer_ota_addr).toString().c_str());
        NimBLEDevice::startAdvertising();
    }
};

static ServerCallbacks g_serverCallbacks;

// ─────────────────────────────────────────────────────────────────────────────
// Characteristic creation helper
// ─────────────────────────────────────────────────────────────────────────────
NimBLECharacteristic* makeNotifyChar(const ble_uuid128_t& uuid, const char* name) {
    NimBLECharacteristic* chr = g_customSvc->createCharacteristic(uuid, NIMBLE_PROPERTY::NOTIFY);
    NimBLE2902* cccd = (NimBLE2902*)chr->createDescriptor(BLE_UUID_DESCRIPTOR_CLIENT_CHAR_CONFIG);
    cccd->setNotifications(true);
    Serial.printf("Created notify char: %s\n", name);
    return chr;
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup
// ─────────────────────────────────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    delay(200);
    Serial.println("ElderCare Guardian — ESP32-C3 BLE server starting...");

    pinMode(SOS_BTN_PIN, INPUT_PULLUP);

    // ── I²C bus (shared by MAX30102 and MPU-6050) ──
    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);

    // ── MAX30102 (pulse oximeter) ──
    if (g_particleSensor.begin(Wire, I2C_SPEED_FAST)) {
        Serial.println("MAX30102 found");
        g_particleSensor.setup();
        // Low-power red LED (0x0A ≈ 6.4 mA), green LED off (we only need IR for HR + SpO2 R).
        g_particleSensor.setPulseAmplitudeRed(0x0A);
        g_particleSensor.setPulseAmplitudeGreen(0);
        g_maxReady = true;
    } else {
        Serial.println("MAX30102 NOT found — synthetic fallback active");
        g_maxReady = false;
    }

    // ── MPU-6050 (6-axis IMU) ──
    g_mpu.initialize();
    if (g_mpu.testConnection()) {
        Serial.println("MPU6050 found");
        g_imuReady = true;
    } else {
        Serial.println("MPU6050 NOT found — synthetic fallback active");
        g_imuReady = false;
    }

    NimBLEDevice::init(DEVICE_NAME);
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);

    g_server = NimBLEDevice::createServer();
    g_server->setCallbacks(&g_serverCallbacks);

    // ── Battery service ──
    g_battSvc = g_server->createService(UUID_BATTERY_SVC);
    g_battChr = g_battSvc->createCharacteristic(UUID_BATTERY_LEVEL, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    g_battChr->createDescriptor(BLE_UUID_DESCRIPTOR_CLIENT_CHAR_CONFIG);
    g_battSvc->start();

    // ── Heart rate service ──
    g_hrSvc = g_server->createService(UUID_HEART_RATE_SVC);
    g_hrChr = g_hrSvc->createCharacteristic(UUID_HR_MEASUREMENT, NIMBLE_PROPERTY::NOTIFY);
    g_hrChr->createDescriptor(BLE_UUID_DESCRIPTOR_CLIENT_CHAR_CONFIG);
    g_hrSvc->start();

    // ── Custom service ──
    g_customSvc = g_server->createService(UUID_CUSTOM_SVC);
    g_ecgChr   = makeNotifyChar(UUID_ECG_RAW,      "ECG_RAW");
    g_imuChr   = makeNotifyChar(UUID_IMU_WRIST,    "IMU_WRIST");
    g_sosChr   = makeNotifyChar(UUID_SOS_STATE,    "SOS_STATE");
    g_fallChr  = makeNotifyChar(UUID_FALL_RISK,    "FALL_RISK");
    g_humChr   = makeNotifyChar(UUID_HUMIDITY,     "HUMIDITY");
    g_respChr  = makeNotifyChar(UUID_RESP_RATE,    "RESP_RATE");
    g_stateChr = makeNotifyChar(UUID_DEVICE_STATE, "DEVICE_STATE");
    g_customSvc->start();

    // ── Advertising ──
    NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
    adv->addServiceUUID(UUID_BATTERY_SVC);
    adv->addServiceUUID(UUID_HEART_RATE_SVC);
    adv->addServiceUUID(UUID_CUSTOM_SVC);
    adv->setScanResponse(true);
    adv->start();

    Serial.println("BLE advertising started as 'ElderCare_v1'.");
}

// ─────────────────────────────────────────────────────────────────────────────
// Loop — notify subscribed clients every 1000 ms
// ─────────────────────────────────────────────────────────────────────────────
void loop() {
    g_tickCounter++;

    float t = (float)g_tickCounter * 0.1f;
    float hrBpmF;

    // ── Heart rate / SpO2 — real MAX30102 if available, else synthetic ──
    if (g_maxReady) {
        // Default fallback while we accumulate at least 3 valid intervals.
        hrBpmF = 75.0f;
        // Drain the FIFO without blocking. Each call to check() advances
        // the library's beat-detection state machine and pushes one
        // sample into the FIFO. We pull IR and RED for both beat detection
        // and the SpO2 algorithm.
        while (g_particleSensor.available()) {
            g_particleSensor.check();
            long irValue = g_particleSensor.getIR();
            // Beat detection: checkForBeat returns true on a detected R-wave.
            if (checkForBeat(irValue)) {
                unsigned long now = millis();
                if (g_lastBeatMs > 0) {
                    long interval = (long)(now - g_lastBeatMs);
                    if (interval >= 250 && interval <= 2000) {
                        g_beatIntervals[g_beatBufferIdx] = (int)interval;
                        g_beatBufferIdx = (g_beatBufferIdx + 1) % BEAT_BUFFER_SIZE;
                        if (g_validBeatCount < BEAT_BUFFER_SIZE) g_validBeatCount++;
                    }
                }
                g_lastBeatMs = now;
            }
            // Accumulate samples for the SpO2 algorithm. We accumulate
            // IR and RED in lockstep so they correspond to the same moment.
            if (g_spo2BufferIdx < SPO2_BUFFER_SIZE) {
                g_redBuffer[g_spo2BufferIdx] = g_particleSensor.getRed();
                g_irBuffer[g_spo2BufferIdx]  = (uint32_t)irValue;
                g_spo2BufferIdx++;
            }
        }
        // Compute mean HR from the last N beat-to-beat intervals.
        if (g_validBeatCount >= 3) {
            long sum = 0;
            for (int i = 0; i < g_validBeatCount; i++) sum += g_beatIntervals[i];
            long meanInterval = sum / g_validBeatCount;
            if (meanInterval > 0) {
                hrBpmF = 60000.0f / (float)meanInterval;
            }
        }
        // Run the SpO2 algorithm once the buffer is full. Reset and
        // restart the buffer. We log to Serial because no PLX
        // characteristic exists in the current GATT contract.
        if (g_spo2BufferIdx >= SPO2_BUFFER_SIZE) {
            int32_t computedHr = 0, computedSpo2 = 0;
            int8_t hrValid = 0;
            maxim_heart_rate_and_oxygen_saturation(
                g_irBuffer, SPO2_BUFFER_SIZE, g_redBuffer,
                &computedSpo2, &computedHr, &g_spo2Valid, &g_spo2Percent);
            g_spo2BufferIdx = 0;
            if (g_spo2Valid) {
                Serial.printf("SpO2: %d%%  HR(alg): %ld\n", g_spo2Percent, (long)computedHr);
            }
        }
    } else {
        // Synthetic fallback so the BLE pipe stays up and the demo
        // continues to render the dashboard. The phase matches a
        // patient at rest, ~75 bpm.
        hrBpmF = 75.0f + 6.0f * sinf(t * 0.5f);
    }
    uint8_t hrBpm = (uint8_t)(hrBpmF + 0.5f);

    // ── IMU — real MPU-6050 if available, else synthetic ──
    // Accelerometer: ±2g range → 16384 LSB/g. Convert g → m/s² to match
    // FallDetectionEngine thresholds (spike 24.5 m/s², stillness 3.0 m/s²).
    // Gyroscope: ±250°/s range → 131 LSB/(°/s).
    float ax, ay, az, gx, gy, gz;
    if (g_imuReady) {
        int16_t ax16, ay16, az16, gx16, gy16, gz16;
        g_mpu.getMotion6(&ax16, &ay16, &az16, &gx16, &gy16, &gz16);
        ax = (ax16 / 16384.0f) * 9.81f;
        ay = (ay16 / 16384.0f) * 9.81f;
        az = (az16 / 16384.0f) * 9.81f;
        gx = gx16 / 131.0f;
        gy = gy16 / 131.0f;
        gz = gz16 / 131.0f;
    } else {
        // Synthetic: ~9.8 m/s² at rest, with tiny noise.
        ax = 0.05f * sinf(t * 1.3f);
        ay = 0.04f * cosf(t * 1.7f);
        az = 9.81f + 0.06f * sinf(t * 0.9f);
        gx = 0.5f  * sinf(t * 0.6f);
        gy = 0.4f  * cosf(t * 0.5f);
        gz = 0.3f  * sinf(t * 0.4f);
    }

    // SOS mirrors GPIO9 button (active low → 1 when pressed)
    g_sosValue = (digitalRead(SOS_BTN_PIN) == LOW) ? 1 : 0;

    // DEVICE_STATE: 0=Normal 1=Check 2=Urgent
    if (g_sosValue == 1 || hrBpm > 130 || hrBpm < 40) {
        g_deviceState = 2;   // Urgent
    } else if (hrBpm > 110 || hrBpm < 50) {
        g_deviceState = 1;   // Check
    } else {
        g_deviceState = 0;   // Normal
    }

    // ── HR notify (SIG format: flags=0, hr=uint8) ──
    uint8_t hrBuf[2] = { 0x00, hrBpm };
    g_hrChr->setValue(hrBuf, sizeof(hrBuf));
    g_hrChr->notify();

    // ── Battery notify (uint8 percent) ──
    g_battChr->setValue(&g_battValue, 1);
    g_battChr->notify();

    // ── ECG_RAW (float32[256], 1024 bytes) — synthetic QRS-spike train ──
    // 256 samples at 256 Hz = 1 second of ECG. The QRS spike is placed every
    // (60 / hrBpm) seconds, so the Android HeartRateExtractor sees a realistic
    // R-peak interval that matches the device's nominal HR. The float cursor
    // keeps beat timing drift-free across notify windows.
    {
        uint8_t ecgBuf[1024];
        const float sampleRateHz = 256.0f;
        const float samplesPerBeat = (60.0f / hrBpmF) * sampleRateHz;
        const float qrsCenterFrac = 0.35f;
        const float qrsWidth = 3.0f;        // ~12 ms — narrow positive R-wave
        const float qrsAmplitude = 0.9f;
        static float sampleCursor = 0.0f;
        for (int i = 0; i < 256; i++) {
            float posInBeat = fmodf(sampleCursor + (float)i, samplesPerBeat);
            float qrsCenter = samplesPerBeat * qrsCenterFrac;
            float diff = posInBeat - qrsCenter;
            float qrs = qrsAmplitude * expf(-(diff * diff) / (2.0f * qrsWidth * qrsWidth));
            float baseline = 0.05f * sinf(t * 0.05f);
            float v = baseline + qrs;
            packFloat(ecgBuf, i * 4, v);
        }
        sampleCursor += 256.0f;
        g_ecgChr->setValue(ecgBuf, sizeof(ecgBuf));
        g_ecgChr->notify();
    }

    // ── IMU_WRIST (float32[6], 24 bytes) ──
    {
        uint8_t imuBuf[24];
        float imuVals[6] = { ax, ay, az, gx, gy, gz };
        packFloatArray(imuBuf, 0, imuVals, 6);
        g_imuChr->setValue(imuBuf, sizeof(imuBuf));
        g_imuChr->notify();
    }

    // ── SOS_STATE (uint8: 0=off 1=active) ──
    g_sosChr->setValue(&g_sosValue, 1);
    g_sosChr->notify();

    // ── FALL_RISK (float32: 0.0–1.0) ──
    {
        uint8_t fallBuf[4];
        float fallRisk = 0.0f;
        if (g_sosValue == 1) fallRisk = 0.95f;
        packFloat(fallBuf, 0, fallRisk);
        g_fallChr->setValue(fallBuf, sizeof(fallBuf));
        g_fallChr->notify();
    }

    // ── HUMIDITY (float32[2]: %RH, temp_C) ──
    {
        uint8_t humBuf[8];
        float humidity[2] = { 48.0f + 1.5f * sinf(t * 0.2f), 32.5f + 0.2f * cosf(t * 0.3f) };
        packFloatArray(humBuf, 0, humidity, 2);
        g_humChr->setValue(humBuf, sizeof(humBuf));
        g_humChr->notify();
    }

    // ── RESP_RATE (float32: breaths/min) ──
    {
        uint8_t respBuf[4];
        float respRate = 16.0f + 1.0f * sinf(t * 0.3f);
        packFloat(respBuf, 0, respRate);
        g_respChr->setValue(respBuf, sizeof(respBuf));
        g_respChr->notify();
    }

    // ── DEVICE_STATE (uint8: 0=Normal 1=Check 2=Urgent) ──
    g_stateChr->setValue(&g_deviceState, 1);
    g_stateChr->notify();

    delay(1000);
}
