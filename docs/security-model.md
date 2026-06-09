# Security model — ElderCare Guardian

This document captures the threat model and the privacy/security decisions
that shaped the Android build. Every decision here is intentional and
reviewable. Anything not on this list is **out of scope** for the current
Phase 1/2 work and should be promoted onto it before production rollout.

## Data at rest

### Room database (`eldercare.db`)

Stores `AlertEventEntity` rows — alert level, reason, timestamp. The Room
file is **not encrypted at rest** in the current build.

| Decision | Value |
|---|---|
| At-rest encryption | **None** (SQLCipher not integrated) |
| Threat-model acceptance | Acceptable for showcase / Phase 2 single-user testing |
| Production requirement | **Encrypt before any real patient data lands on a device** |

Why we ship without SQLCipher right now:
- Showcase demo runs on dev devices with no real patient data.
- SQLCipher adds an AAR dependency, key-management responsibility
  (Android Keystore wrap), and migration friction.
- Pranay/Pratyush need to weigh the operational cost of key loss
  (a forgotten Keystore alias = total data loss) against the threat of
  the device falling into the wrong hands.

Threats the **current** unencrypted DB is exposed to:
- Adversary with physical access to an unlocked device.
- Adversary with root access on a rooted device.
- Backup extraction via `adb` (mitigated by `allowBackup="false"`).
- A future code change that accidentally opts in to backup.

Threats the **current** unencrypted DB is **not** exposed to:
- Network adversary (no cloud sync yet).
- Compromised app process reading a different sandboxed app (Android
  UID isolation).

Production hardening checklist:
- [x] Integrate `net.zetetic:android-database-sqlcipher` + `androidx.sqlite:sqlite`.
      2026-06-08: integrated via SupportFactory with AndroidKeyStore-wrapped
      256-bit passphrase (DatabaseEncryption.kt). Database file renamed
      from eldercare.db to eldercare_encrypted.db for a clean break from
      pre-existing plaintext data. The passphrase is generated once on first
      launch, stored in EncryptedSharedPreferences (AES256-GCM value,
      AES256-SIV key, MasterKey hardware-bound on TEE-capable devices).
      The old eldercare.db file is orphaned — acceptable because no real
      patient data existed before encryption was added.
- [ ] Wrap key in `AndroidKeyStore` (`KeyGenParameterSpec` with
      `setUserAuthenticationRequired` for caregiver-only access).
      **Deferred**: required for production where device sharing is common.
      The current implementation wraps the passphrase in EncryptedSharedPreferences
      via MasterKey, which already uses AndroidKeyStore — but without
      user-authentication gating. Adding biometric unlock is a future UX task.
- [ ] Migrate existing v1 schema to encrypted v2.
      **Deferred**: schema stays v1; SQLCipher sits transparently underneath.
      No migration needed because the database file name was changed (clean
      break). Future schema changes will use Room's AutoMigration; SQLCipher
      does not affect the Room migration framework.
- [x] Fail-closed on key loss: render the DB unreadable rather than silently
      falling back to plaintext.
      SQLCipher's SupportFactory requires the correct passphrase bytes for
      every connection. If EncryptedSharedPreferences returns null (key store
      loss), the database is simply unreachable — no silent fallback.

### Auto-backup

`AndroidManifest.xml` declares:

- `android:allowBackup="false"`
- `android:dataExtractionRules="@xml/data_extraction_rules"` (API 31+)
- `android:fullBackupContent="@xml/backup_rules"` (API < 31)

Both XML rule files explicitly exclude the `eldercare.db*` family and the
shared-prefs / file domains. There is no data path that will silently
exfiltrate the alert history to Google Drive or a device-transfer target.

The previous `MIGRATION_COMPLETED` `<meta-data>` element was removed
because it belongs to the **deprecated** Samsung Health SDK. The current
Data SDK v1.1.0 does not use that meta-data.

## Data in motion

### BLE (GATT)

| Property | Value |
|---|---|
| Pairing mode | **KEYBOARD_ONLY** (passkey entry on phone) |
| Bonding | Yes (`createBond()` + `ACTION_BOND_STATE_CHANGED`) |
| MITM protection | **Enabled** (`setSecurityAuth(true, true, true)`) |
| LE Secure Connections | Enabled |
| Passkey | Fixed device passkey (123456 — printed to Serial for debugging; production to use per-device PIN on label) |

Current state:
- Firmware: `NimBLEDevice::setSecurityAuth(true, true, true)` + `KEYBOARD_ONLY`
  IO capability. `SecurityCallbacks` class handles `onPassKeyRequest()`,
  `onPassKeyNotify()`, `onSecurityRequest()`, and `onAuthenticationComplete()`.
  Authentication result (bonded, encrypted flags) logged to Serial.
- Android: `SmartSuitBleDataSource` registers a `BroadcastReceiver` for
  `ACTION_BOND_STATE_CHANGED`. On `STATE_CONNECTED`, if not yet bonded, calls
  `BluetoothDevice.createBond()`. Bond receiver handles `BOND_BONDING` /
  `BOND_BONDED` transitions, triggering `reconnectGatt()` post-bond.
  CCCD subscription proceeds only after bonding is complete.

Known gap in current build:
- **Hardcoded passkey 123456** — shared across all devices. Acceptable for
  prototype testing. Production must use a per-device passkey printed on the
  enclosure label.
- **No Android passkey-entry UI** — the phone's system dialog handles passkey
  entry automatically when the firmware uses `KEYBOARD_ONLY` IO capability.
  This has been verified to work with Android's BLE stack on API 29+.

Acceptable for the showcase because:
- All transmitted data is synthetic or prototype-grade.
- No real patient PII crosses the air.
- Bonding + encryption is enabled, which is more secure than Just Works.
- The passkey is documented and can be overridden for production.

### Outbound caregiver notification

- Local Android `NotificationCompat` notification on Urgent caregiver
  alert. No data leaves the device.
- Tap-to-dial intent (`Intent.ACTION_DIAL`) for the configured caregiver
  number. No `CALL_PHONE` permission is requested, so the dialer is
  shown with the number pre-filled and the user taps to place the call.

Out-of-scope for Phase 1/2:
- SMS/FCM push to remote caregivers (requires a backend, account, and
  a privacy review of message content).

## Permissions

Manifest declares the minimum needed for the showcase:

- `BLUETOOTH_SCAN` (neverForLocation), `BLUETOOTH_CONNECT`,
  `BLUETOOTH_ADVERTISE` — GATT client.
- `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` — maxSdk 30
  legacy fallback.
- `BODY_SENSORS`, `ACTIVITY_RECOGNITION` — sensor reading.
- `POST_NOTIFICATIONS` (API 33+) — local caregiver alert notification.

**Not** requested:
- `CALL_PHONE` — tap-to-dial uses `ACTION_DIAL` and is permission-free.
- `READ_PHONE_STATE`, `READ_CONTACTS`, `READ_EXTERNAL_STORAGE`,
  `RECORD_AUDIO` — none of these are needed for the showcase.

## Out of scope for this build (audit reminders)

- Multi-user / per-caregiver-pairing model — currently one phone, one
  simulated patient. Production needs a binding model.
- Audit log of caregiver acknowledgement (who acked what, when) — not
  stored yet.
- Tamper detection / jailbreak detection on the Android device.
- Server-side rate-limiting on the notification path (there is no
  server yet).
