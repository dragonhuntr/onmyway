import type { Context } from "./http.ts";
import type { Env } from "./env.ts";
import { HttpError, json, now, readJSON, text } from "./http.ts";
import { authenticate } from "./auth.ts";

/** A notification, plus where a tap should land in the app. */
export type Push = {
  title: string;
  body: string;
  kind: "order" | "delivery" | "chat";
  orderId: string;
};

type Device = { token: string; platform: "ios" | "android" };

/** POST /devices `{ token, platform }` — the app's push token, sent after sign-in and whenever it changes. */
export async function registerDevice(c: Context): Promise<Response> {
  const { user, token: session } = await authenticate(c);
  const body = await readJSON(c.request);
  const token = text(body.token, 512);
  const platform = body.platform;
  if (!token || (platform !== "ios" && platform !== "android")) {
    throw new HttpError(400, "Send a push token and platform.");
  }
  await c.env.DB.prepare(
    `INSERT INTO devices (token, user_id, session_token, platform, updated_at) VALUES (?1, ?2, ?3, ?4, ?5)
     ON CONFLICT(token) DO UPDATE SET user_id = ?2, session_token = ?3, platform = ?4, updated_at = ?5`,
  )
    .bind(token, user.id, session, platform, now())
    .run();
  return json({ ok: true }, 201);
}

/** Sends `push` to every phone the user is signed in on. Never throws; platforms without credentials are skipped. */
export async function notify(env: Env, userID: string | null, push: Push): Promise<void> {
  if (!userID) return;
  const { results } = await env.DB.prepare("SELECT token, platform FROM devices WHERE user_id = ?1")
    .bind(userID)
    .all<Device>();
  await Promise.all(
    results.map(async (device) => {
      try {
        const gone = device.platform === "ios" ? await sendAPNs(env, device.token, push) : await sendFCM(env, device.token, push);
        if (gone) await env.DB.prepare("DELETE FROM devices WHERE token = ?1").bind(device.token).run();
      } catch (error) {
        console.warn("Push failed", device.platform, error);
      }
    }),
  );
}

// MARK: Apple Push Notification service

let apnsToken: { value: string; expires: number } | null = null;

/** Returns true when Apple says the token is no longer valid. */
async function sendAPNs(env: Env, token: string, push: Push): Promise<boolean> {
  if (!env.APNS_KEY || !env.APNS_KEY_ID || !env.APNS_TEAM_ID) return false;
  const host = env.APNS_ENVIRONMENT === "production" ? "api.push.apple.com" : "api.sandbox.push.apple.com";
  const response = await fetch(`https://${host}/3/device/${token}`, {
    method: "POST",
    headers: {
      authorization: `bearer ${await apnsJWT(env)}`,
      "apns-topic": env.APNS_TOPIC,
      "apns-push-type": "alert",
    },
    body: JSON.stringify({
      aps: { alert: { title: push.title, body: push.body }, sound: "default" },
      kind: push.kind,
      orderId: push.orderId,
    }),
  });
  if (response.ok) return false;
  const reason = ((await response.json().catch(() => ({}))) as { reason?: string }).reason ?? "";
  if (response.status === 410 || reason === "BadDeviceToken") return true;
  throw new Error(`APNs ${response.status} ${reason}`);
}

/** Provider token, reused for 50 minutes (Apple allows up to 60). */
async function apnsJWT(env: Env): Promise<string> {
  if (apnsToken && apnsToken.expires > Date.now()) return apnsToken.value;
  const key = await crypto.subtle.importKey("pkcs8", pem(env.APNS_KEY!), { name: "ECDSA", namedCurve: "P-256" }, false, [
    "sign",
  ]);
  const value = await jwt({ alg: "ES256", kid: env.APNS_KEY_ID }, { iss: env.APNS_TEAM_ID, iat: seconds() }, (data) =>
    crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, data),
  );
  apnsToken = { value, expires: Date.now() + 50 * 60_000 };
  return value;
}

// MARK: Firebase Cloud Messaging

type ServiceAccount = { project_id: string; client_email: string; private_key: string; token_uri: string };

let fcmToken: { value: string; expires: number } | null = null;

/** Returns true when Firebase says the token is no longer registered. */
async function sendFCM(env: Env, token: string, push: Push): Promise<boolean> {
  if (!env.FCM_SERVICE_ACCOUNT) return false;
  const account: ServiceAccount = JSON.parse(env.FCM_SERVICE_ACCOUNT);
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`, {
    method: "POST",
    headers: { authorization: `Bearer ${await fcmAccessToken(account)}`, "content-type": "application/json" },
    body: JSON.stringify({
      message: {
        token,
        notification: { title: push.title, body: push.body },
        data: { kind: push.kind, orderId: push.orderId },
        android: { priority: "high", notification: { channel_id: "orders" } },
      },
    }),
  });
  if (response.ok) return false;
  if (response.status === 404) return true;
  throw new Error(`FCM ${response.status} ${await response.text()}`);
}

/** OAuth access token from the service account, reused until five minutes before it expires. */
async function fcmAccessToken(account: ServiceAccount): Promise<string> {
  if (fcmToken && fcmToken.expires > Date.now()) return fcmToken.value;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pem(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const iat = seconds();
  const assertion = await jwt(
    { alg: "RS256" },
    {
      iss: account.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: account.token_uri,
      iat,
      exp: iat + 3600,
    },
    (data) => crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, data),
  );
  const response = await fetch(account.token_uri, {
    method: "POST",
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  if (!response.ok) throw new Error(`Google OAuth ${response.status} ${await response.text()}`);
  const { access_token, expires_in } = (await response.json()) as { access_token: string; expires_in: number };
  fcmToken = { value: access_token, expires: Date.now() + (expires_in - 300) * 1000 };
  return access_token;
}

// MARK: JWT

async function jwt(
  header: Record<string, unknown>,
  claims: Record<string, unknown>,
  sign: (data: Uint8Array) => Promise<ArrayBuffer>,
): Promise<string> {
  const encoder = new TextEncoder();
  const input = `${base64url(encoder.encode(JSON.stringify({ typ: "JWT", ...header })))}.${base64url(
    encoder.encode(JSON.stringify(claims)),
  )}`;
  return `${input}.${base64url(new Uint8Array(await sign(encoder.encode(input))))}`;
}

function base64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** The DER bytes inside a PEM private key. Accepts literal `\n`s, as pasted from a JSON file. */
function pem(key: string): ArrayBuffer {
  const body = key.replace(/\\n/g, "\n").replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  return Uint8Array.from(atob(body), (char) => char.charCodeAt(0)).buffer;
}

function seconds(): number {
  return Math.floor(Date.now() / 1000);
}
