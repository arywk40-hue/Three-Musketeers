import * as admin from 'firebase-admin';

let initialized = false;

function getApp(): admin.app.App {
  if (initialized) return admin.app();

  const serviceAccountRaw = process.env.FCM_SERVICE_ACCOUNT_JSON;
  if (!serviceAccountRaw) {
    console.warn('FCM_SERVICE_ACCOUNT_JSON not set — FCM calls will fail');
    admin.initializeApp({ projectId: 'eldercare-guardian' });
    initialized = true;
    return admin.app();
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
  level?: string,
  reason?: string,
  patientName?: string,
): Promise<{ success: boolean; messageId?: string; error?: string }> {
  try {
    const message: admin.messaging.Message = {
      token,
      notification: { title, body },
      // Data payload reaches onMessageReceived() when app is foregrounded.
      // When backgrounded, notification auto-displays AND data is available
      // via the launch intent extras.
      data: {
        alertLevel: level || 'Unknown',
        reason: reason || '',
        patientName: patientName || '',
      },
      android: { priority: 'high' },
    };
    const messageId = await getApp().messaging().send(message);
    return { success: true, messageId };
  } catch (err: unknown) {
    const error = err instanceof Error ? err.message : String(err);
    return { success: false, error };
  }
}
