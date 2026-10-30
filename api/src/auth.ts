import type { Context } from "./http.ts";
import type { Env, User } from "./env.ts";
import { balanceCents } from "./money.ts";
import { sendEmail } from "./email.ts";
import { runnerProfile } from "./ratings.ts";
import { HttpError, decodeBase64, encodeBase64, json, minutesFromNow, now, readJSON, text } from "./http.ts";

const iterations = 100_000;
const sessionDays = 30;

type Credentials = { password_hash: string; password_salt: string };

export async function register(c: Context): Promise<Response> {
  const body = await readJSON(c.request);
  const email = text(body.email).toLowerCase();
  const username = text(body.username, 40);
  const firstName = text(body.firstName, 60);
  const lastName = text(body.lastName, 60);
  const password = typeof body.password === "string" ? body.password : "";

  if (!email.includes("@") || !username || !firstName || !lastName) {
    throw new HttpError(400, "Fill in every field.");
  }
  const domain = c.env.ALLOWED_EMAIL_DOMAIN.toLowerCase();
  if (domain && !email.endsWith(`@${domain}`)) {
    throw new HttpError(400, `Use your @${domain} email.`, "email_domain");
  }
  if (password.length < 8) {
    throw new HttpError(400, "Use a password of at least 8 characters.");
  }

  const existing = await c.env.DB.prepare("SELECT username, email FROM users WHERE username = ?1 OR email = ?2")
    .bind(username, email)
    .first<{ username: string; email: string }>();
  if (existing) {
    const taken = existing.email.toLowerCase() === email ? "email" : "username";
    throw new HttpError(409, `That ${taken} is already registered.`);
  }

  const salt = crypto.getRandomValues(new Uint8Array(16));
  const userID = crypto.randomUUID();
  await c.env.DB.prepare(
    `INSERT INTO users (id, username, email, first_name, last_name, password_hash, password_salt)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)`,
  )
    .bind(userID, username, email, firstName, lastName, await hashPassword(password, salt), encodeBase64(salt))
    .run();

  const user = (await userByID(c.env, userID))!;
  const token = await createSession(c.env, userID);
  // Signed in straight away, but ordering and running wait for the emailed code (POST /email-verification/confirm).
  const sending = sendVerificationCode(c.env, user);
  if (exposesCodes(c.env)) return json({ ...publicUser(user, token), devCode: await sending });
  c.ctx.waitUntil(sending);
  return json(publicUser(user, token));
}

export async function login(c: Context): Promise<Response> {
  const body = await readJSON(c.request);
  const username = text(body.username);
  const password = typeof body.password === "string" ? body.password : "";
  if (!username || !password) {
    throw new HttpError(400, "Enter your username and password.");
  }

  const user = await c.env.DB.prepare("SELECT * FROM users WHERE username = ?1")
    .bind(username)
    .first<User & Credentials>();
  if (!user || !(await passwordMatches(password, user))) {
    throw new HttpError(401, "Username or password is wrong.");
  }
  return json(publicUser(user, await createSession(c.env, user.id)));
}

/** GET /me — the account, plus `runner` rating stats for Profile. */
export async function me(c: Context): Promise<Response> {
  const { user, token } = await authenticate(c);
  return json({ ...publicUser(user, token), runner: await runnerProfile(c.env, user.id) });
}

export async function logout(c: Context): Promise<Response> {
  const token = bearer(c.request);
  if (token) await c.env.DB.prepare("DELETE FROM sessions WHERE token = ?1").bind(token).run();
  return json({ ok: true });
}

/**
 * DELETE /me — wipes the account: name, email, password, saved drop-off, trips and sign-ins.
 * The row stays (as "Deleted account") so past orders and the ledger still add up for the other side.
 */
export async function deleteAccount(c: Context): Promise<Response> {
  const { user } = await authenticate(c);
  const busy = await c.env.DB.prepare(
    `SELECT customer_id = ?1 AS mine FROM orders
     WHERE (customer_id = ?1 AND status IN ('awaiting_payment', 'matching', 'accepted', 'picked_up'))
        OR (runner_id = ?1 AND status IN ('accepted', 'picked_up'))
     LIMIT 1`,
  )
    .bind(user.id)
    .first<{ mine: number }>();
  if (busy) {
    throw new HttpError(
      409,
      busy.mine ? "Cancel or finish your current order first." : "Finish your current delivery first.",
      "account_busy",
    );
  }
  const balance = await balanceCents(c.env, user.id);
  if (balance > 0) {
    throw new HttpError(409, `Cash out your $${(balance / 100).toFixed(2)} in Earnings first.`, "balance_left");
  }

  const at = now();
  await c.env.DB.batch([
    c.env.DB.prepare(
      `UPDATE users SET username = 'deleted-' || id, email = 'deleted-' || id || '@deleted.invalid',
         first_name = 'Deleted', last_name = 'account', password_hash = '', password_salt = '',
         stripe_customer_id = NULL, blurb = '', dropoff_building_id = NULL, dropoff_room = '', dropoff_note = '',
         runner_available = 0, runner_lat = NULL, runner_lng = NULL, deleted_at = ?2
       WHERE id = ?1`,
    ).bind(user.id, at),
    c.env.DB.prepare("UPDATE trips SET cancelled_at = ?2 WHERE runner_id = ?1 AND cancelled_at IS NULL").bind(user.id, at),
    c.env.DB.prepare("DELETE FROM notifications WHERE user_id = ?1").bind(user.id),
    c.env.DB.prepare("DELETE FROM password_resets WHERE user_id = ?1").bind(user.id),
    c.env.DB.prepare("DELETE FROM email_verifications WHERE user_id = ?1").bind(user.id),
    c.env.DB.prepare("DELETE FROM devices WHERE user_id = ?1").bind(user.id),
    c.env.DB.prepare("DELETE FROM sessions WHERE user_id = ?1").bind(user.id),
  ]);
  return json({ ok: true });
}

// MARK: Emailed codes

/** A code lasts this long and allows this many wrong tries. */
const codeMinutes = 15;
const codeTries = 5;
/** A new code isn't sent while the last one is younger than this; stops email floods and code cycling. */
const codeCooldownSeconds = 60;

/** One pending code per account per purpose. Both tables have the same columns. */
type CodeTable = "password_resets" | "email_verifications";

/** Only with `PAYMENTS=off` (local development) and no email provider; never in production. */
function exposesCodes(env: Env): boolean {
  return env.PAYMENTS === "off" && !env.RESEND_API_KEY;
}

/** Stores a fresh code for the account, replacing any older one. Null while the cooldown is running. */
async function storeCode(env: Env, table: CodeTable, userID: string): Promise<string | null> {
  const recent = await env.DB.prepare(`SELECT 1 FROM ${table} WHERE user_id = ?1 AND created_at > ?2`)
    .bind(userID, new Date(Date.now() - codeCooldownSeconds * 1000).toISOString())
    .first();
  if (recent) return null;

  const code = sixDigitCode();
  const salt = crypto.getRandomValues(new Uint8Array(16));
  await env.DB.prepare(
    `INSERT INTO ${table} (user_id, code_hash, code_salt, attempts, created_at, expires_at)
     VALUES (?1, ?2, ?3, 0, ?4, ?5)
     ON CONFLICT(user_id) DO UPDATE SET code_hash = ?2, code_salt = ?3, attempts = 0, created_at = ?4, expires_at = ?5`,
  )
    .bind(userID, await hashPassword(code, salt), encodeBase64(salt), now(), minutesFromNow(codeMinutes))
    .run();
  return code;
}

/**
 * Checks `code` against the account's pending one. Throws `wrong` for a bad or expired code, and
 * `locked` (dropping the code) on the last allowed miss. The row is left for the caller to delete on success.
 */
async function checkCode(
  env: Env,
  table: CodeTable,
  userID: string,
  code: string,
  wrong: HttpError,
  locked: HttpError,
): Promise<void> {
  // Count the try before checking it, so parallel guesses can't get past the limit.
  const pending = await env.DB.prepare(
    `UPDATE ${table} SET attempts = attempts + 1
     WHERE user_id = ?1 AND expires_at > ?2 AND attempts < ?3
     RETURNING code_hash, code_salt, attempts`,
  )
    .bind(userID, now(), codeTries)
    .first<{ code_hash: string; code_salt: string; attempts: number }>();
  if (!pending) throw wrong;
  if ((await hashPassword(code, decodeBase64(pending.code_salt))) === pending.code_hash) return;
  if (pending.attempts < codeTries) throw wrong;
  await env.DB.prepare(`DELETE FROM ${table} WHERE user_id = ?1`).bind(userID).run();
  throw locked;
}

function sixDigits(value: unknown): string {
  const code = text(value, 20);
  if (!/^\d{6}$/.test(code)) throw new HttpError(400, "Enter the 6-digit code from the email.");
  return code;
}

// MARK: Email verification

/**
 * Emails a code proving the account owns its address. Returns the code, or null when it wasn't sent
 * (cooldown, or the send failed). Never throws, so it can run after the response.
 */
async function sendVerificationCode(env: Env, user: User): Promise<string | null> {
  try {
    const code = await storeCode(env, "email_verifications", user.id);
    if (!code) return null;
    await sendEmail(env, {
      to: user.email,
      subject: `${code} is your On My Way code`,
      text: `Welcome to On My Way, ${user.first_name}. Your email verification code is ${code}. It expires in ${codeMinutes} minutes.\n\nIf you didn't make an On My Way account, ignore this email.`,
    });
    return code;
  } catch (error) {
    console.warn("Verification email failed", error);
    return null;
  }
}

/** POST /email-verification — sends a new code to the signed-in account's email. At most one a minute. */
export async function resendVerification(c: Context): Promise<Response> {
  const { user } = await authenticate(c);
  if (user.email_verified_at) return json({ ok: true, verified: true });
  const sending = sendVerificationCode(c.env, user);
  if (exposesCodes(c.env)) return json({ ok: true, verified: false, devCode: await sending });
  c.ctx.waitUntil(sending);
  return json({ ok: true, verified: false });
}

/** POST /email-verification/confirm `{ code }` — marks the email verified and returns the account like /me. */
export async function confirmVerification(c: Context): Promise<Response> {
  const { user, token } = await authenticate(c);
  if (!user.email_verified_at) {
    const code = sixDigits((await readJSON(c.request)).code);
    await checkCode(
      c.env,
      "email_verifications",
      user.id,
      code,
      new HttpError(400, "That code is wrong or has expired.", "verification_code"),
      new HttpError(429, "Too many wrong codes. Send a new one.", "verification_locked"),
    );
    await c.env.DB.batch([
      c.env.DB.prepare("UPDATE users SET email_verified_at = ?2 WHERE id = ?1").bind(user.id, now()),
      c.env.DB.prepare("DELETE FROM email_verifications WHERE user_id = ?1").bind(user.id),
    ]);
  }
  return json(publicUser((await userByID(c.env, user.id))!, token));
}

// MARK: Forgot password

/**
 * POST /password-reset `{ email }` — emails a 6-digit code. Always `{ ok: true }`, whether or not the
 * email has an account; the lookup and sending happen after the response so timing gives nothing away either.
 */
export async function requestPasswordReset(c: Context): Promise<Response> {
  const body = await readJSON(c.request);
  const email = resetEmail(c.env, body.email);
  const sending = sendResetCode(c.env, email);
  if (exposesCodes(c.env)) {
    // Local development only: hand the code back so test/flow.mjs can finish the reset.
    return json({ ok: true, devCode: await sending });
  }
  c.ctx.waitUntil(sending);
  return json({ ok: true });
}

/**
 * POST /password-reset/confirm `{ email, code, password }` — sets the new password, ends every
 * other sign-in, and signs in here like /login. Getting the code also proves the email, so it counts
 * as verifying it.
 */
export async function confirmPasswordReset(c: Context): Promise<Response> {
  const body = await readJSON(c.request);
  const email = resetEmail(c.env, body.email);
  const code = sixDigits(body.code);
  const password = typeof body.password === "string" ? body.password : "";
  if (password.length < 8) {
    throw new HttpError(400, "Use a password of at least 8 characters.");
  }

  const wrong = new HttpError(400, "That code is wrong or has expired.", "reset_code");
  const user = await c.env.DB.prepare("SELECT * FROM users WHERE email = ?1 AND deleted_at IS NULL")
    .bind(email)
    .first<User>();
  if (!user) throw wrong;
  await checkCode(
    c.env,
    "password_resets",
    user.id,
    code,
    wrong,
    new HttpError(429, "Too many wrong codes. Request a new one.", "reset_locked"),
  );

  const salt = crypto.getRandomValues(new Uint8Array(16));
  const at = now();
  await c.env.DB.batch([
    c.env.DB.prepare(
      `UPDATE users SET password_hash = ?2, password_salt = ?3, email_verified_at = COALESCE(email_verified_at, ?4)
       WHERE id = ?1`,
    ).bind(user.id, await hashPassword(password, salt), encodeBase64(salt), at),
    c.env.DB.prepare("DELETE FROM password_resets WHERE user_id = ?1").bind(user.id),
    c.env.DB.prepare("DELETE FROM email_verifications WHERE user_id = ?1").bind(user.id),
    c.env.DB.prepare("DELETE FROM devices WHERE user_id = ?1").bind(user.id),
    c.env.DB.prepare("DELETE FROM sessions WHERE user_id = ?1").bind(user.id),
  ]);
  return json(publicUser((await userByID(c.env, user.id))!, await createSession(c.env, user.id)));
}

function resetEmail(env: Env, value: unknown): string {
  const email = text(value).toLowerCase();
  if (!email.includes("@")) throw new HttpError(400, "Enter your email.");
  const domain = env.ALLOWED_EMAIL_DOMAIN.toLowerCase();
  if (domain && !email.endsWith(`@${domain}`)) {
    throw new HttpError(400, `Use your @${domain} email.`, "email_domain");
  }
  return email;
}

/** Stores a fresh reset code for the account and emails it. Returns the code, or null. */
async function sendResetCode(env: Env, email: string): Promise<string | null> {
  try {
    const user = await env.DB.prepare("SELECT id FROM users WHERE email = ?1 AND deleted_at IS NULL")
      .bind(email)
      .first<{ id: string }>();
    if (!user) return null;
    const code = await storeCode(env, "password_resets", user.id);
    if (!code) return null;
    await sendEmail(env, {
      to: email,
      subject: `${code} is your On My Way code`,
      text: `Your On My Way password reset code is ${code}. It expires in ${codeMinutes} minutes.\n\nIf you didn't ask to reset your password, ignore this email; your password hasn't changed.`,
    });
    return code;
  } catch (error) {
    console.warn("Password reset failed", error);
    return null;
  }
}

/** Uniform 000000–999999. 4_294_000_000 is the largest multiple of a million below 2^32. */
function sixDigitCode(): string {
  const value = new Uint32Array(1);
  do crypto.getRandomValues(value);
  while (value[0] >= 4_294_000_000);
  return String(value[0] % 1_000_000).padStart(6, "0");
}

/**
 * The signed-in user with a verified email, or a 401 / 403. Account endpoints (/me, /logout, devices,
 * notifications, verification itself) use `authenticate` instead, so an unverified account can still reach them.
 */
export async function requireUser(c: Context): Promise<User> {
  const { user } = await authenticate(c);
  if (!user.email_verified_at) {
    throw new HttpError(403, `Verify your email first. We sent a code to ${user.email}.`, "email_unverified");
  }
  return user;
}

export function publicUser(user: User, token: string) {
  return {
    id: user.id,
    username: user.username,
    email: user.email,
    firstName: user.first_name,
    lastName: user.last_name,
    emailVerified: Boolean(user.email_verified_at),
    token,
  };
}

export async function userByID(env: Env, userID: string): Promise<User | null> {
  return env.DB.prepare("SELECT * FROM users WHERE id = ?1").bind(userID).first<User>();
}

export async function authenticate(c: Context): Promise<{ user: User; token: string }> {
  const token = bearer(c.request);
  if (!token) throw new HttpError(401, "Sign in again.", "signed_out");
  const user = await c.env.DB.prepare(
    `SELECT users.* FROM sessions JOIN users ON users.id = sessions.user_id
     WHERE sessions.token = ?1 AND (sessions.expires_at IS NULL OR sessions.expires_at > ?2)`,
  )
    .bind(token, now())
    .first<User>();
  if (!user) throw new HttpError(401, "Sign in again.", "signed_out");
  return { user, token };
}

async function createSession(env: Env, userID: string): Promise<string> {
  const token = crypto.randomUUID();
  await env.DB.prepare("INSERT INTO sessions (token, user_id, expires_at) VALUES (?1, ?2, ?3)")
    .bind(token, userID, minutesFromNow(sessionDays * 24 * 60))
    .run();
  return token;
}

async function hashPassword(password: string, salt: Uint8Array): Promise<string> {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", salt, iterations, hash: "SHA-256" }, key, 256);
  return encodeBase64(new Uint8Array(bits));
}

async function passwordMatches(password: string, user: Credentials): Promise<boolean> {
  const actual = await hashPassword(password, decodeBase64(user.password_salt));
  return actual === user.password_hash;
}

function bearer(request: Request): string | null {
  const header = request.headers.get("Authorization") ?? "";
  const match = header.match(/^Bearer\s+(.+)$/i);
  return match?.[1] ?? null;
}
