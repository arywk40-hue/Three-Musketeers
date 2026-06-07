/*
 * ElderCare Guardian — ESP32-C3 BLE GATT server
 * Library: NimBLE-Arduino (Arduino Library Manager)
 * Board:   ESP32-C3 Dev Module
 *
 * Synthetic-data sketch for Phase 1 BLE showcase.
 * I2C sensor reading (MAX30102 + MPU-6050) is a Phase 2 task.
 *
 * SOS button is wired to GPIO 9 (active low, INPUT_PULLUP).
 * All custom characteristic UUIDs MUST match SmartSuitBleContract.kt.
 */

#include <Arduino.h>
#include <NimBLEDevice.h>
#include <NimBLEServer.h>
#include <NimBLEUtils.h>
#include <NimBLE2902.h>
#include <math.h>

// ─────────────────────────────────────────────────────────────────────────────
// Hardware pin map
// ─────────────────────────────────────────────────────────────────────────────
static const uint8_t SOS_BTN_PIN = 9;   // active low, INPUT_PULLUP

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

    // ── Synthetic data ──
    float t = (float)g_tickCounter * 0.1f;
    float hrBpmF = 75.0f + 6.0f * sinf(t * 0.5f);   // ~75 bpm via sine wave
    uint8_t hrBpm = (uint8_t)(hrBpmF + 0.5f);

    // Accel ~ 9.8 m/s² at rest, with tiny noise
    float ax = 0.05f * sinf(t * 1.3f);
    float ay = 0.04f * cosf(t * 1.7f);
    float az = 9.81f + 0.06f * sinf(t * 0.9f);
    float gx = 0.5f  * sinf(t * 0.6f);
    float gy = 0.4f  * cosf(t * 0.5f);
    float gz = 0.3f  * sinf(t * 0.4f);

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
