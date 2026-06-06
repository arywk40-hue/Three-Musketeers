# Smart Suit Android App

This is the Android application foundation for the Smart Workout Suit.

## Current Build Stage

- Jetpack Compose dashboard scaffold with Vitals, Workout, Power, and Readiness tabs.
- Simulator-backed data stream for pitch reliability.
- BLE contract and data-source seam prepared.
- Runtime permission handling for BLE scan/connect, body sensors, and activity recognition.
- Ready tab can scan for `SmartSuit_v1`, list discovered devices, and start a GATT connection skeleton.
- Samsung Health integration seam prepared.
- Samsung Health SDK classes are not imported yet, because the SDK AAR must be downloaded manually from Samsung Developer Portal.

## Open in Android Studio

Open this directory:

```text
apps/android
```

Use:
- Android Studio Ladybug or later.
- JDK 17.
- Android SDK 35.
- Real phone for BLE and Samsung Health testing.

## Samsung Health Notes

Samsung Health Data SDK v1.1.0 requires:
- Android 10/API 29 or later.
- Samsung Health app 6.30.2 or later.
- Java 17 or later.
- Real device testing.
- Partner request for write/distribution access.

During showcase work, keep the dashboard usable with simulator data even before Samsung access is approved.
