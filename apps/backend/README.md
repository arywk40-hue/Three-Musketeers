# ElderCare Guardian — FCM Notification Backend

## Setup

```bash
cd apps/backend
npm install
cp .env.example .env
```

Edit `.env` with your Firebase service account JSON.

## Run locally

```bash
npm run dev       # development with ts-node
npm run build     # compile to dist/
npm start         # run compiled JS
npm run offline   # simulate Lambda locally (serverless-offline)
```

## Deploy to AWS Lambda + API Gateway

### Prerequisites

1. **AWS account** with IAM credentials configured:

```bash
# Install AWS CLI if you don't have it
# Then configure credentials:
aws configure
# Access Key ID:  <your-key>
# Secret Access Key: <your-secret>
# Default region: ap-south-1  (Mumbai)
```

2. **Firebase service account JSON** — download from Firebase Console → Project Settings → Service Accounts → Generate new private key.

### Deploy

```bash
cd apps/backend

# Set your Firebase service account JSON as env var
export FCM_SERVICE_ACCOUNT_JSON='{"type":"service_account","project_id":"..."}'

# Deploy
npm run deploy
```

On first deploy, Serverless Framework will:
- Create S3 bucket for deployment artifacts
- Create Lambda function + API Gateway HTTP API
- Output the API URL: `https://xxxxxxxxxx.execute-api.ap-south-1.amazonaws.com`

### Set the URL in the Android app

1. Open the ElderCare Guardian app
2. Go to **Settings**
3. Paste the API Gateway URL into the **Backend URL** field (e.g. `https://xxxxxxxxxx.execute-api.ap-south-1.amazonaws.com`)
4. Save

## Clean up

To delete all AWS resources:

```bash
npx serverless remove
```

## API

### `POST /notify`

Sends an FCM push notification to the caregiver's device.

**Body:**

```json
{
  "token": "fcm-device-token",
  "level": "Warning",
  "reason": "Sustained high heart rate detected",
  "patientName": "Ariyan"
}
```

Rate limited to 1 call per 10 seconds per token.

### `GET /health`

Returns `{ status: "ok" }`.

### `GET /medical-knowledge?q=heart+rate+elderly`

Returns recent PubMed + WHO results for the query.
