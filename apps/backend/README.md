# ElderCare Guardian — FCM Notification Backend

## Setup

```bash
cd apps/backend
npm install
cp .env.example .env
```

Edit `.env` with your Firebase service account JSON:

```
FCM_SERVICE_ACCOUNT_JSON={"type":"service_account","project_id":"..."}
PORT=3001
```

## Run

```bash
npm run dev       # development with ts-node
npm run build     # compile to dist/
npm start         # run compiled JS
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

**Response:**

```json
{ "success": true, "messageId": "..." }
```

Rate limited to 1 call per 10 seconds per token.

### `GET /health`

Returns `{ status: "ok" }`.

## Deployment

Recommended: [Railway](https://railway.app) or [Render](https://render.com).

```bash
# Railway
railway login
railway up

# Render — connect GitHub repo, set build command: npm run build, start: npm start
```
