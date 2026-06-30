import express from 'express';
import dotenv from 'dotenv';
import { sendToToken } from './fcm';
import { fetchMedicalKnowledge } from './medical_knowledge';

dotenv.config();

const app = express();
app.use(express.json());

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

app.post('/notify', async (req, res) => {
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

  const result = await sendToToken(token, title, body, level, reason, patientName || '');
  res.json(result);
});

app.get('/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

app.get('/medical-knowledge', async (req, res) => {
  const q = (req.query.q as string | undefined)?.trim();
  if (!q || q.length < 2) {
    res.status(400).json({ success: false, error: 'Missing query param: q (min 2 chars)' });
    return;
  }
  if (q.length > 100) {
    res.status(400).json({ success: false, error: 'Query too long (max 100 chars)' });
    return;
  }
  try {
    const result = await fetchMedicalKnowledge(q);
    res.json({ success: true, ...result });
  } catch (err: unknown) {
    const error = err instanceof Error ? err.message : String(err);
    res.status(500).json({ success: false, error });
  }
});

export default app;
