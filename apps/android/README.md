# Smart Suit Android App

This is the Android application foundation for the Smart Workout Suit.

## Current Build Stage

- Jetpack Compose dashboard scaffold.
- Simulator-backed data stream for pitch reliability.
- BLE and Samsung Health integration seams prepared.
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
