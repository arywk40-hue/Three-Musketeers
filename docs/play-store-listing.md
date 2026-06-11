# Play Store Listing — ElderCare Guardian

## App Details

| Field | Value |
|-------|-------|
| App name | ElderCare Guardian |
| Short description (80 char) | Real-time fall detection and vitals monitoring for elderly care. |
| Full description | ElderCare Guardian pairs with a BLE wearable (ESP32-C3) to monitor heart rate, SpO2, motion, and temperature. On-device ML detects falls, dehydration, and cardiac anomalies. Caregiver alerts are sent via SMS or push notification. All health data is encrypted locally — never uploaded to the cloud. |
| Category | Medical |
| Content rating | Everyone (no user-generated content, no violence) |
| Price | Free |
| In-app purchases | None |
| Ads | None |

## Screenshots (required: 2–8 screenshots, 16:9 or 9:16)

| # | Screen | Device frame | Caption |
|---|--------|-------------|---------|
| 1 | Vitals Dashboard | Pixel 7 Pro, 9:16 | Real-time heart rate, SpO2, and ECG waveform |
| 2 | Safety Panel | Pixel 7 Pro, 9:16 | Fall risk, posture, and SOS status at a glance |
| 3 | Caregiver Alert | Pixel 7 Pro, 9:16 | Alert timeline with call caregiver button |
| 4 | Readiness | Pixel 7 Pro, 9:16 | BLE connection, device telemetry, Samsung Health status |
| 5 | Settings | Pixel 7 Pro, 9:16 | Patient profiles and caregiver contact configuration |
| 6 | Data Privacy | Pixel 7 Pro, 9:16 | DPDPA consent screen (first-launch flow) |

### Screenshot generation

```bash
# Using Android Studio Device Explorer:
# 1. Run on Pixel 7 Pro API 35 emulator
# 2. Navigate to each screen
# 3. Cmd+S (macOS) / Ctrl+S (Windows) to capture
# 4. Copy to docs/play-store-screenshots/
```

## Graphics

| Asset | Size | Notes |
|-------|------|-------|
| App icon | 512×512 px | Teal (#0F766E) background with white heart pulse logo |
| Feature graphic | 1024×500 px | "ElderCare Guardian — IIT Mandi" banner |
| Phone screenshot | 1080×1920 px | 9:16 ratio, no status bar |

## Privacy Policy URL

```
https://eldercareguardian.github.io/privacy-policy/
```

Host the `apps/privacy-policy/index.html` page via GitHub Pages from a `gh-pages` branch.

## Tags

- eldercare
- fall-detection
- health-monitor
- wearable
- caregiver
- iit-mandi

## Google Play Console Setup

1. Create a Google Play Developer account ($25 one-time fee).
2. Navigate to **Create app** → fill in the app details from the table above.
3. **Set up store listing** → upload graphics, screenshots, and privacy policy URL.
4. **Set up app access** → "All features are available without special access".
5. **Set up ads** → "No, my app does not contain ads".
6. **App content** → Complete the target audience, news, and data safety sections.
7. **Production track** → Upload the signed AAB from `./gradlew bundleRelease`.
8. Release rollout → Start with 10% staged rollout, monitor crash rate for 48h.

## Data Safety Section (Play Console)

| Question | Answer |
|----------|--------|
| Does your app collect data? | Yes |
| Health data | Heart rate, SpO2, temperature |
| Is data encrypted in transit? | Yes (BLE bonding + MITM) |
| Is data encrypted at rest? | Yes (AES-256-GCM via SQLCipher) |
| Can user request data deletion? | Yes (Settings → Delete all data) |
| Is data shared with third parties? | No |
| Is data processed on device? | Yes (all ML is on-device) |
