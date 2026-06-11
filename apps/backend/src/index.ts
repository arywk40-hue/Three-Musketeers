import express, { Request, Response } from 'express';
import dotenv from 'dotenv';
import { sendToToken } from './fcm';

dotenv.config();

const app = express();
app.use(express.json());

// In-memory rate limit: 1 call per 10 seconds per token
const rateLimitMap = new Map<string, number>();
const RATE_LIMIT_MS = 10_000;

function isRateLimited(token: string): boolean {
  const last = rateLimitMap.get(token);
  const now = Date.now();
  if (last && now - last < RATE_LIMIT_MS) return true;
  rateLimitMap.set(token, now);
  return false;
}

interface NotifyBody {
  token: string;
  level: 'Check' | 'Warning' | 'Emergency';
  reason: string;
  patientName: string;
}

app.post('/notify', async (req: Request, res: Response) => {
  const { token, level, reason, patientName } = req.body as NotifyBody;

  if (!token || !level || !reason) {
    res.status(400).json({ success: false, error: 'Missing required fields: token, level, reason' });
    return;
  }

  if (isRateLimited(token)) {
    res.status(429).json({ success: false, error: 'Rate limited — one notification per 10 seconds' });
    return;
  }

  const title = `ElderCare Guardian — ${level}`;
  const body = `${patientName || 'Your patient'}: ${reason}`;

  const result = await sendToToken(token, title, body);
  res.json(result);
});

app.get('/health', (_req: Request, res: Response) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

const PORT = parseInt(process.env.PORT || '3001', 10);
app.listen(PORT, () => {
  console.log(`ElderCare Guardian backend listening on port ${PORT}`);
});
