# End-to-End FCM Test Instructions

**Priority:** P1 (Quality assurance before pilot)  
**Time required:** 30–45 minutes  
**Prerequisites:** FCM backend deployed (NEXT_STEPS task #4)

---

## Overview

This test validates the complete caregiver alert pipeline:
1. Patient device detects emergency (fall/SOS/high HR)
2. Android app sends FCM notification request to backend
3. Backend forwards notification to caregiver device via Firebase Cloud Messaging
4. Caregiver receives push notification within 60 seconds

---

## Prerequisites

### 1. Deploy the FCM backend

Follow instructions in `apps/backend/README.md`:

```bash
cd apps/backend
npm install
```

**Option A: Railway**
```bash
railway login
railway init
railway up
```

**Option B: Render**
1. Connect GitHub repo to Render
2. Set build command: `npm run build`
3. Set start command: `npm start`
4. Add environment variables (see below)

**Set environment variables:**
- `FCM_SERVICE_ACCOUNT_JSON`: Paste the **entire** service account JSON from Firebase Console → Project Settings → Service Accounts → Generate new private key
- `PORT`: `3001` (Railway auto-detects, Render requires explicit value)

**Get the deployed URL:**
- Railway: `railway status` shows the public URL (e.g., `https://eldercare-backend-production.up.railway.app`)
- Render: Copy from the dashboard (e.g., `https://eldercare-backend.onrender.com`)

### 2. Install the debug APK on two Android phones

**Build the APK:**
```bash
cd /Users/ariyanbhakat/Three-Musketeers/apps/android
./gradlew assembleDebug
```

**Transfer to phones:**
```bash
# Phone 1 (patient device)
adb -s <device1_serial> install app/build/outputs/apk/debug/app-debug.apk

# Phone 2 (caregiver device)
adb -s <device2_serial> install app/build/outputs/apk/debug/app-debug.apk
```

Or use Android File Transfer / Google Drive to manually install.

---

## Test procedure

### Step 1: Configure phone 1 (patient device)

1. **Launch app** → Accept DPDPA consent
2. **Settings screen:**
   - Tap **"Add Patient"**
   - Enter name: `Test Patient`
   - Enter age: `75`
   - Leave caregiver fields empty for now
   - Tap **Save**
3. **Settings → Backend URL:**
   - Paste the deployed backend URL (e.g., `https://eldercare-backend-production.up.railway.app`)
   - Tap **Save**
4. **Dashboard screen:**
   - Tap **"Enable Simulator"** (if no wearable device connected)
   - Verify vitals are updating (HR, SpO2, Temp)

### Step 2: Configure phone 2 (caregiver device)

1. **Launch app** → Accept DPDPA consent
2. **Settings screen:**
   - Tap **"Add Patient"**
   - Enter name: `Test Patient`
   - Enter age: `75`
   - Tap **Save**
3. **Wait 5–10 seconds** for FCM token to be generated
4. **Copy the FCM token:**
   - Open Android logcat: `adb -s <device2_serial> logcat -s ElderCare`
   - Look for: `FCM token registered: <long-token-string>`
   - Copy the token (starts with something like `cHxG7...`)

### Step 3: Link caregiver to patient

**On phone 1 (patient device):**
1. **Settings → Edit Patient** (tap the patient name)
2. **Caregiver section:**
   - Caregiver Name: `Test Caregiver`
   - Caregiver Phone: `+919876543210` (any valid number)
   - **Caregiver FCM Token:** Paste the token from phone 2
3. Tap **Save**

### Step 4: Trigger an alert

**On phone 1 (patient device):**

Choose one method:

#### Option A: SOS button
1. Dashboard → Tap **"SOS"** button (red, top-right)
2. Confirm the dialog
3. Verify alert status changes to **Emergency** (red banner)

#### Option B: Fall simulator
1. Dashboard → Three-dot menu → **"Simulate Fall"** (if available in debug builds)
2. Verify alert status changes to **Warning** or **Emergency**

#### Option C: Sustained high HR
1. Dashboard → Enable Simulator
2. Wait until simulated HR goes above 110 bpm for ~3 minutes
3. Verify alert status changes to **Warning**

### Step 5: Verify notification delivery

**On phone 2 (caregiver device):**
1. **Check notification tray** within 60 seconds
2. Expected notification:
   - **Title:** `ElderCare Alert: Warning` (or `Emergency`)
   - **Body:** `Test Patient — <reason>` (e.g., "Fall detected" or "Sustained high heart rate")
   - **Icon:** App icon
3. Tap the notification → Should open the app

**If notification does NOT arrive:**
- Check phone 2 notification permissions: Settings → Apps → ElderCare Guardian → Notifications → **Allow**
- Check phone 1 logs: `adb logcat -s ElderCare:D FcmAlertSender:D`
  - Look for `FcmAlertSender: Sending alert to backend...`
  - Look for `FcmAlertSender: Alert sent successfully` or error message
- Check backend logs (Railway: `railway logs` or Render dashboard)
  - Look for `POST /notify` request
  - Look for FCM success message or error

---

## Success criteria

✅ **Pass:** Notification arrives on phone 2 within 60 seconds of triggering alert on phone 1  
✅ **Pass:** Notification shows correct alert level and patient name  
✅ **Pass:** Tapping notification opens the app  

❌ **Fail:** No notification after 2 minutes  
❌ **Fail:** Notification shows generic text or wrong patient name  
❌ **Fail:** Backend logs show `401 Unauthorized` (invalid service account JSON)  

---

## Troubleshooting

### "Backend URL or FCM token is empty"
- Verify phone 1 Settings → Backend URL is set
- Verify phone 1 Settings → Patient has `caregiverFcmToken` filled

### "FCM backend returned 400: Invalid token"
- The FCM token from phone 2 might have expired (tokens refresh periodically)
- Clear app data on phone 2 and repeat Step 2
- Copy the new token and update it in phone 1 Settings

### "FCM backend returned 401 Unauthorized"
- The service account JSON in backend env vars is incorrect or malformed
- Download a fresh key from Firebase Console → Service Accounts → Generate new private key
- Paste the **entire JSON** into the `FCM_SERVICE_ACCOUNT_JSON` env var
- Restart the backend service

### No logs on phone 1
- Enable logging: `adb shell setprop log.tag.ElderCare DEBUG`
- Re-trigger the alert
- Check: `adb logcat -s ElderCare:* FcmAlertSender:*`

### Backend not reachable
- Test with curl:
  ```bash
  curl https://your-backend-url.app/health
  # Expected: {"status":"ok"}
  ```
- If timeout: Check Railway/Render deployment status
- If 404: URL might be wrong (missing `/notify` is OK for `/health`)

---

## Cleanup

After testing:
1. Phone 1: Settings → Delete Patient → Confirm
2. Phone 2: Settings → Delete Patient → Confirm
3. Uninstall app from both devices (or keep for further testing)

---

## Security notes

- **FCM tokens are NOT secrets** — they identify a device, but cannot be used maliciously without the server account key
- **Server account JSON IS a secret** — stored only in backend env vars, never in the Android app
- **Backend URL** is public — rate limiting (1 req/10s per token) prevents abuse

---

## Next steps

After successful test:
1. Document the backend URL in `DEPLOYMENT_PLAN.md`
2. Update `README.md` with production backend URL
3. Add backend URL to Play Store app description (optional)
4. Set up backend monitoring (Railway: built-in, Render: Prometheus/Grafana)

---

## Related files

- `apps/backend/src/index.ts` — FCM backend implementation
- `apps/android/app/src/main/java/com/eldercareguardian/notifications/FcmAlertSender.kt` — Client-side sender
- `apps/android/app/src/main/java/com/eldercareguardian/notifications/FcmTokenManager.kt` — Token registration
- `NEXT_STEPS.md` — Task #7 (this document fulfills that task)
