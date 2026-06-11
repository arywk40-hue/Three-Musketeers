import * as admin from 'firebase-admin';

let initialized = false;

function getApp(): admin.app.App {
  if (initialized) return admin.app();

  const serviceAccountRaw = process.env.FCM_SERVICE_ACCOUNT_JSON;
  if (!serviceAccountRaw) {
    console.warn('FCM_SERVICE_ACCOUNT_JSON not set — FCM calls will fail');
    return admin.initializeApp({ projectId: 'eldercare-guardian' });
  }

  try {
    const serviceAccount = JSON.parse(serviceAccountRaw) as admin.ServiceAccount;
    admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
  } catch {
    console.warn('Failed to parse FCM_SERVICE_ACCOUNT_JSON — using default credentials');
    admin.initializeApp();
  }

  initialized = true;
  return admin.app();
}

export async function sendToToken(
  token: string,
  title: string,
  body: string,
): Promise<{ success: boolean; messageId?: string; error?: string }> {
  try {
    const message: admin.messaging.Message = {
      token,
      notification: { title, body },
      android: { priority: 'high' },
    };
    const messageId = await getApp().messaging().send(message);
    return { success: true, messageId };
  } catch (err: unknown) {
    const error = err instanceof Error ? err.message : String(err);
    return { success: false, error };
  }
}
