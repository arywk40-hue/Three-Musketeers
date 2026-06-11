# Local SDK AARs

Place Samsung Health Data SDK AAR files here after downloading from
https://developer.samsung.com/health/data

Expected:
- `samsung-health-data-api-*.aar`

The app always compiles (bridge uses reflection). When the AAR is placed here
and the device has the Samsung Health app 6.30.2+, the **Readiness** tab's
Samsung Health panel transitions from NeedsSdkAar to NeedsPartnerApproval
on the next `startSamsungBridge()` call.

## Download steps

1. Go to https://developer.samsung.com/health/data
2. Sign in with your Samsung Developer account.
3. Agree to the SDK terms.
4. Download `samsung-health-data-api-1.1.0.aar` (or later).
5. Copy the `.aar` file into this directory.
6. Rebuild the app: `./gradlew assembleDebug`

## Partner App Program

Even with the AAR, SDK write access requires your app ID to be registered at
https://developer.samsung.com/health/partner. Without it the bridge stays in
`NeedsPartnerApproval` state. Apply for the program, provide your package name
(`com.eldercareguardian`), and wait for approval (typically 1-2 business days).

## Notes

- Does not work on emulators — requires a physical Samsung device with the
  Samsung Health app 6.30.2 or later installed.
- The GATT profile already uses standard SIG services (0x180D, 0x1822, 0x1810,
  0x1809) compatible with Samsung Health Accessory SDK certification.
