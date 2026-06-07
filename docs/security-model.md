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

Production hardening checklist (not yet done):
- [ ] Integrate `net.zetetic:android-database-sqlcipher` + `androidx.sqlite:sqlite`.
- [ ] Wrap key in `AndroidKeyStore` (`KeyGenParameterSpec` with
      `setUserAuthenticationRequired` for caregiver-only access).
- [ ] Migrate existing v1 schema to encrypted v2.
- [ ] Fail-closed on key loss: render the DB unreadable rather than silently
      falling back to plaintext.

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
| Pairing mode | **Just Works** (no user confirmation) |
| Bonding | None |
| MITM protection | **None** |
| LE Secure Connections | Not used |

`Just Works` is acceptable for the showcase because:
- All transmitted data is synthetic during demos.
- No real patient PII crosses the air.

**Production requirement**: switch to Passkey Entry or Numeric
Comparison for real patient data. This requires firmware changes (set
`BLE_SM_PAIR_AUTHREQ_BOND | BLE_SM_PAIR_AUTHREQ_MITM | BLE_SM_PAIR_AUTHREQ_SC`
in NimBLE's `setSecurityIOCap`) and Android-side bonding wiring
(`BluetoothDevice.createBond()` + `ACTION_BOND_STATE_CHANGED` listener).

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
