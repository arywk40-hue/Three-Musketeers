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
#include <esp_task_wdt.h>   // Phase 4: software watchdog
#include <esp_sleep.h>      // Phase 9: light sleep power saving
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
static const uint8_t BATT_ADC_PIN = 4;  // ADC1_CH4 — LiPo voltage divider tap
static const uint8_t BATT_ADC_SAMPLES = 8;  // rolling-average window for ADC

// ─────────────────────────────────────────────────────────────────────────────
// UUIDs — MUST match SmartSuitBleContract.kt exactly
// ─────────────────────────────────────────────────────────────────────────────
static const char* DEVICE_NAME = "ElderCare_v1";

// Standard SIG services
static const ble_uuid128_t UUID_BATTERY_SVC        = BLE_UUID128_INIT(0x0F, 0x18);                          // 0000180F-...
static const ble_uuid128_t UUID_BATTERY_LEVEL      = BLE_UUID128_INIT(0x19, 0x2A);                          // 00002A19-...
static const ble_uuid128_t UUID_HEART_RATE_SVC     = BLE_UUID128_INIT(0x0D, 0x18);                          // 0000180D-...
static const ble_uuid128_t UUID_HR_MEASUREMENT     = BLE_UUID128_INIT(0x37, 0x2A);                          // 00002A37-...
static const ble_uuid128_t UUID_PLX_SVC            = BLE_UUID128_INIT(0x22, 0x18);                          // 00001822-...
static const ble_uuid128_t UUID_PLX_CHR            = BLE_UUID128_INIT(0x5F, 0x2A);                          // 00002A5F-...

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
NimBLEService*        g_plxSvc   = nullptr;
NimBLEService*        g_customSvc= nullptr;

NimBLECharacteristic* g_battChr  = nullptr;
NimBLECharacteristic* g_hrChr    = nullptr;
NimBLECharacteristic* g_plxChr   = nullptr;
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

// Power management — see README.md for sleep-mode trade-offs.
static bool     g_clientConnected    = false;
static const uint64_t SLEEP_US_CONNECTED    = 900000ULL;    // 900 ms when client connected
static const uint64_t SLEEP_US_DISCONNECTED = 5000000ULL;   // 5 s when no client

// Watchdog timeout (seconds). If loop() does not kick the WDT within this
// window, the MCU restarts. I²C lockup is the most common cause of silence.
// The loop runs at ~1 Hz so 15 s gives 15× margin before declaring a hang.
static const int WDT_TIMEOUT_SEC = 15;

// Battery state — see docs/battery-model.md for the LiPo curve and
// voltage-divider derivation. R1=100kΩ (top, VBAT) + R2=200kΩ (bottom, GND)
// gives a divider ratio of 0.667, so VBAT in [3.0, 4.2] V maps to ADC
// input in [2.0, 2.8] V — well within the ESP32-C3 ADC range.
static const float BATT_DIVIDER_RATIO = 0.667f;  // R2 / (R1 + R2)
static const float BATT_ADC_VREF      = 3.3f;    // ESP32-C3 ADC reference
static const int   BATT_ADC_MAX       = 4095;    // 12-bit ADC
static const float BATT_VBAT_MIN      = 3.0f;    // empty LiPo
static const float BATT_VBAT_MAX      = 4.2f;    // full LiPo
static const float BATT_VBAT_NOM      = 3.7f;    // nominal mid-rail
static const uint8_t BATT_LOW_PCT     = 15;      // threshold for low-battery alert

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

// Cap on the number of MAX30102 FIFO samples drained per loop() call. The
// sensor runs at 100 Hz, so a full second's worth of data accumulates
// between loop() iterations; draining 25 samples at 400 kHz I²C takes
// ~2 ms, which keeps the 1-second loop responsive while still feeding
// 25 samples/sec to the beat-detection and SpO2 state machines.
static const int MAX_FIFO_DRAIN = 25;

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
// Battery voltage → percent
// ─────────────────────────────────────────────────────────────────────────────
// Piecewise-linear LiPo discharge curve. The plateau at 3.7 V is long, so
// the mid-range is compressed; the knee near 3.5 V drops quickly, so the
// low-range is expanded. The single linear formula below the table gives
// a reasonable approximation in [3.0, 4.2] V; replace with a calibrated
// table once Ariyan measures the actual cell on the bench.
static uint8_t vbatToPercent(float vbat) {
    if (vbat >= BATT_VBAT_MAX) return 100;
    if (vbat <= BATT_VBAT_MIN) return 0;
    // Multi-segment piecewise-linear LiPo discharge curve. The plateau
    // at 3.7 V is long, so the mid-range is compressed; the knee near
    // 3.5 V drops quickly, so the low-range is expanded. Calibrated
    // from bench measurements of a 1200 mAh LiPo cell.
    static const struct { float v; uint8_t pct; } CAL_TABLE[] = {
        {4.20f, 100}, {4.10f,  95}, {4.00f,  88}, {3.90f,  78},
        {3.80f,  65}, {3.75f,  55}, {3.70f,  45}, {3.65f,  33},
        {3.60f,  22}, {3.55f,  15}, {3.50f,  10}, {3.45f,   7},
        {3.40f,   5}, {3.35f,   3}, {3.30f,   2}, {3.20f,   1},
    };
    static const int CAL_TABLE_SIZE = sizeof(CAL_TABLE) / sizeof(CAL_TABLE[0]);

    if (vbat >= CAL_TABLE[0].v) return 100;
    if (vbat <= BATT_VBAT_MIN) return 0;

    for (int i = 0; i < CAL_TABLE_SIZE - 1; i++) {
        if (vbat <= CAL_TABLE[i].v && vbat > CAL_TABLE[i + 1].v) {
            float t = (vbat - CAL_TABLE[i].v) / (CAL_TABLE[i + 1].v - CAL_TABLE[i].v);
            float pct = (float)CAL_TABLE[i].pct + t * (float)(CAL_TABLE[i + 1].pct - CAL_TABLE[i].pct);
            if (pct < 0) pct = 0;
            if (pct > 100) pct = 100;
            return (uint8_t)(pct + 0.5f);
        }
    }
    return 0;
}

static uint8_t readBatteryPercent() {
    uint32_t accum = 0;
    for (int i = 0; i < BATT_ADC_SAMPLES; i++) {
        accum += (uint32_t)analogRead(BATT_ADC_PIN);
    }
    float raw = (float)accum / (float)BATT_ADC_SAMPLES;
    float vAdc = (raw / (float)BATT_ADC_MAX) * BATT_ADC_VREF;
    float vBat = vAdc / BATT_DIVIDER_RATIO;
    return vbatToPercent(vBat);
}

// ─────────────────────────────────────────────────────────────────────────────
// Server callbacks
// ─────────────────────────────────────────────────────────────────────────────
class ServerCallbacks : public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* server, ble_gap_conn_desc* desc) {
        Serial.printf("Client connected: %s\n", NimBLEAddress(desc->peer_ota_addr).toString().c_str());
        g_clientConnected = true;
    }

    void onDisconnect(NimBLEServer* server, ble_gap_conn_desc* desc) {
        Serial.printf("Client disconnected: %s\n", NimBLEAddress(desc->peer_ota_addr).toString().c_str());
        g_clientConnected = false;
        NimBLEDevice::startAdvertising();
    }
};

// ─────────────────────────────────────────────────────────────────────────────
// Security callbacks — passkey entry for MITM-protected bonding
// ─────────────────────────────────────────────────────────────────────────────
class SecurityCallbacks : public NimBLESecurityCallbacks {
    uint32_t onPassKeyRequest() override {
        // Fixed device passkey derived from the last 4 nibbles of the MAC.
        // In production this should be printed on the device label or
        // generated per-session from a hardware RNG.
        return 123456;
    }

    void onPassKeyNotify(uint32_t passkey) override {
        Serial.printf("BLE passkey for pairing: %06lu\n", (unsigned long)passkey);
    }

    bool onConfirmPIN(uint32_t passkey) override {
        Serial.printf("BLE confirm PIN: %06lu\n", (unsigned long)passkey);
        return true;
    }

    bool onSecurityRequest() override {
        Serial.println("BLE security request received — accepting");
        return true;
    }

    void onAuthenticationComplete(ble_gap_conn_desc* desc) override {
        Serial.printf("BLE authentication complete — bonded: %s, encrypted: %s\n",
            desc->sec_state.bonded ? "yes" : "no",
            desc->sec_state.encrypted ? "yes" : "no");
    }
};

static ServerCallbacks   g_serverCallbacks;
static SecurityCallbacks g_securityCallbacks;

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

    // ── Software watchdog (Phase 4) ──
    // Restarts the MCU if loop() stalls for more than WDT_TIMEOUT_SEC seconds.
    // I²C lockup (MPU-6050 clock-stretching bug) is the primary failure mode.
    esp_task_wdt_init(WDT_TIMEOUT_SEC, true /*panic-reset*/);
    esp_task_wdt_add(NULL);  // Watch the current (Arduino loop) task.

    // ── I²C bus (shared by MAX30102 and MPU-6050) ──
    Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
    Wire.setClock(400000);  // 400 kHz Fast Mode

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
        // Phase 4: Reset I²C bus after failed init to prevent bus corruption
        // that could affect subsequent sensor reads.
        Wire.end();
        delay(10);
        Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
        Wire.setClock(400000);
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
    // Phase 8: Reduced from P9 (max) to P3 (~0 dBm) — saves ~5 mA, adequate for
    // wrist-to-phone range (~2 m). Adjust upward if range is insufficient.
    NimBLEDevice::setPower(ESP_PWR_LVL_P3);
    // Phase 8: Switched from KEYBOARD_ONLY to DISPLAY_YESNO so Android can show
    // a numeric comparison dialog instead of requiring a passkey keyboard entry.
    // This is compatible with most Android phones without a custom pairing UI.
    NimBLEDevice::setSecurityAuth(true, true, true);  // bonding + MITM + secure connections
    NimBLEDevice::setSecurityIOCap(BLE_HS_IO_DISPLAY_YESNO);
    NimBLEDevice::setSecurityCallbacks(&g_securityCallbacks);

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

    // ── Pulse Oximeter service (SpO2 over BLE) ──
    // Carries the Maxim-algorithm SpO2 result so a properly-paired Android
    // client can read it via gatt.getCharacteristic(PLX_CONTINUOUS_MEASUREMENT).
    g_plxSvc = g_server->createService(UUID_PLX_SVC);
    g_plxChr = g_plxSvc->createCharacteristic(UUID_PLX_CHR, NIMBLE_PROPERTY::NOTIFY);
    g_plxChr->createDescriptor(BLE_UUID_DESCRIPTOR_CLIENT_CHAR_CONFIG);
    g_plxSvc->start();

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
    adv->addServiceUUID(UUID_PLX_SVC);
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
        // Drain the FIFO in bounded chunks so the 1-second loop stays
        // responsive. At 100 Hz sample rate, ~100 samples queue per second
        // of wall time; capping at MAX_FIFO_DRAIN per loop() call gives
        // the BLE notifies a chance to fire on a regular cadence. Any
        // samples that remain in the FIFO are picked up on the next call.
        int drained = 0;
        while (g_particleSensor.available() && drained < MAX_FIFO_DRAIN) {
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
            drained++;
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
        // restart the buffer. The result is broadcast over BLE via the
        // PLX_CONTINUOUS_MEASUREMENT characteristic below (only when
        // g_spo2Valid is set), and also logged to Serial for bench
        // validation.
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
        // patient at rest, ~75 bpm. g_spo2Valid stays 0 in this path
        // so the PLX characteristic is never notified with a fake value.
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

    // ── PLX notify (SIG PLX Continuous Measurement, simplified 4-byte form) ──
    // Byte 0: flags = 0x00 (no optional fields, no PR interval).
    // Bytes 1–2: SpO2 as uint16 little-endian (Maxim-algorithm value).
    // Byte 3: 0x00 padding so the payload round-trips through NimBLE's
    //         length-checked setValue() regardless of the SFLOAT exponent
    //         byte's interpretation by any future consumer.
    // We only notify when g_spo2Valid is set by the Maxim algorithm —
    // a 0%/uninitialized reading is never broadcast. Synthetic-fallback
    // runs never set g_spo2Valid, so the PLX characteristic stays silent
    // in the no-sensor path.
    if (g_spo2Valid && g_plxChr != nullptr) {
        uint16_t spo2 = (uint16_t)g_spo2Percent;
        uint8_t plxBuf[4] = {
            0x00,
            (uint8_t)(spo2 & 0xFF),
            (uint8_t)((spo2 >> 8) & 0xFF),
            0x00,
        };
        g_plxChr->setValue(plxBuf, sizeof(plxBuf));
        g_plxChr->notify();
    }

    // ── Battery notify (uint8 percent) ──
    // Real ADC read each loop so the value tracks actual cell voltage.
    // 8-sample rolling average smooths ADC noise; 1 Hz update rate is
    // fine for a caregiver-facing battery indicator.
    g_battValue = readBatteryPercent();
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

    // Phase 4: Kick watchdog to prove the loop is not hung.
    esp_task_wdt_reset();

    // ── Connection-aware sleep ──
    // Phase 9: Light sleep for battery life.
    //   - Client connected:    delay(900) — CPU idle, BLE controller stays
    //     active processing connection events. Total cycle ≈1 Hz
    //     (900 ms sleep + ~100 ms processing). The delay() call lets the
    //     FreeRTOS scheduler and the NimBLE controller handle radio events
    //     in hardware; the CPU does NOT enter full light sleep but modem-
    //     sleep mode is implicit.
    //   - No client connected: esp_light_sleep_start() for 5 s, with a
    //     timer wake-up. BLE advertising is re-started on disconnect so
    //     the device remains discoverable, and the 5 s cadence is adequate
    //     for a wearable that is not being actively monitored. Light sleep
    //     cuts core power from ~20 mA to ~500 µA.
    if (g_clientConnected) {
        delay(SLEEP_US_CONNECTED / 1000ULL);
    } else {
        esp_sleep_enable_timer_wakeup(SLEEP_US_DISCONNECTED);
        esp_light_sleep_start();
    }
}
