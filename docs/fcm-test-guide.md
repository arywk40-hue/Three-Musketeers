# End-to-End FCM Test Guide

## Prerequisites

1. Firebase service account JSON downloaded from Firebase Console
2. AWS CLI configured (`aws configure`)
3. Two Android phones (one patient, one caregiver)
4. Debug APK installed on both phones

## 1. Deploy the backend

```bash
cd apps/backend

# Set your Firebase service account JSON
export FCM_SERVICE_ACCOUNT_JSON='{"type":"service_account","project_id":"..."}'

# Deploy to AWS Lambda + API Gateway
npm run deploy
```

On success you'll see output like:

```
✔ Service deployed to: https://xxxxxxxxxx.execute-api.ap-south-1.amazonaws.com
```

## 2. Configure the patient app

1. Open the ElderCare Guardian app on the **patient phone**
2. Go to **Settings**
3. Find the **Backend URL** field
4. Paste the API Gateway URL from step 1 (e.g., `https://xxxxxxxxxx.execute-api.ap-south-1.amazonaws.com`)
5. Make sure the caregiver FCM token is set (it auto-generates on first launch)
6. Save settings

## 3. Trigger a test alert

1. On the patient phone, go to the **Safety** tab
2. Use the **SOS** button or wait for an automatic alert
3. The app sends a POST to `/notify` with the alert details

## 4. Verify delivery

1. On the **caregiver phone**, check the notification tray
2. You should see: `ElderCare Guardian — Emergency`
3. Tap the notification — verify it opens the app
4. Check backend logs: `npx serverless logs -f api` (or watch in CloudWatch)

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| `No FCM token – skipping push` | Caregiver token not set | Register FCM token in the app, verify in Settings |
| `Backend URL or FCM token is empty` | URL not configured | Paste the full API Gateway URL in Settings |
| `Failed to fetch` in logs | Network / CORS | Verify backend is publicly accessible; check API Gateway settings |
| 429 responses | Rate limiting | Wait 10 seconds between alerts per device |
