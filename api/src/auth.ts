import type { Context } from "./http.ts";
import type { Env, User } from "./env.ts";
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

  const user = await userByID(c.env, userID);
  return json(publicUser(user!, await createSession(c.env, userID)));
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

export async function me(c: Context): Promise<Response> {
  const { user, token } = await authenticate(c);
  return json(publicUser(user, token));
}

export async function logout(c: Context): Promise<Response> {
  const token = bearer(c.request);
  if (token) await c.env.DB.prepare("DELETE FROM sessions WHERE token = ?1").bind(token).run();
  return json({ ok: true });
}

/** The signed-in user, or a 401. */
export async function requireUser(c: Context): Promise<User> {
  return (await authenticate(c)).user;
}

export function publicUser(user: User, token: string) {
  return {
    id: user.id,
    username: user.username,
    email: user.email,
    firstName: user.first_name,
    lastName: user.last_name,
    token,
  };
}

export async function userByID(env: Env, userID: string): Promise<User | null> {
  return env.DB.prepare("SELECT * FROM users WHERE id = ?1").bind(userID).first<User>();
}

async function authenticate(c: Context): Promise<{ user: User; token: string }> {
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
