type UserRow = {
  id: string;
  username: string;
  email: string;
  first_name: string;
  last_name: string;
  password_hash: string;
  password_salt: string;
};

const iterations = 100_000;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") {
      return cors(new Response(null, { status: 204 }));
    }

    const url = new URL(request.url);
    try {
      if (request.method === "POST" && url.pathname === "/register") {
        return cors(await register(request, env));
      }
      if (request.method === "POST" && url.pathname === "/login") {
        return cors(await login(request, env));
      }
      if (request.method === "GET" && url.pathname === "/me") {
        return cors(await me(request, env));
      }
      return cors(json({ error: "Not found" }, 404));
    } catch (error) {
      console.error(error);
      return cors(json({ error: "Something went wrong" }, 500));
    }
  },
};

async function register(request: Request, env: Env): Promise<Response> {
  const body = await readJSON(request);
  const email = text(body.email).toLowerCase();
  const username = text(body.username);
  const firstName = text(body.firstName);
  const lastName = text(body.lastName);
  const password = text(body.password);

  if (!email.includes("@") || !username || !firstName || !lastName) {
    return json({ error: "Fill in every field." }, 400);
  }
  if (password.length < 8) {
    return json({ error: "Use a password of at least 8 characters." }, 400);
  }

  const existing = await env.DB.prepare(
    "SELECT username, email FROM users WHERE username = ?1 OR email = ?2",
  )
    .bind(username, email)
    .first<{ username: string; email: string }>();
  if (existing) {
    const taken = existing.email.toLowerCase() === email ? "email" : "username";
    return json({ error: `That ${taken} is already registered.` }, 409);
  }

  const salt = crypto.getRandomValues(new Uint8Array(16));
  const id = crypto.randomUUID();
  const token = crypto.randomUUID();
  await env.DB.prepare(
    `INSERT INTO users (id, username, email, first_name, last_name, password_hash, password_salt)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)`,
  )
    .bind(id, username, email, firstName, lastName, await hashPassword(password, salt), encodeBase64(salt))
    .run();
  await env.DB.prepare("INSERT INTO sessions (token, user_id) VALUES (?1, ?2)").bind(token, id).run();

  return json({
    id,
    username,
    email,
    firstName,
    lastName,
    token,
  });
}

async function login(request: Request, env: Env): Promise<Response> {
  const body = await readJSON(request);
  const username = text(body.username);
  const password = text(body.password);
  if (!username || !password) {
    return json({ error: "Enter your username and password." }, 400);
  }

  const user = await env.DB.prepare(
    "SELECT id, username, email, first_name, last_name, password_hash, password_salt FROM users WHERE username = ?1",
  )
    .bind(username)
    .first<UserRow>();
  if (!user || !(await passwordMatches(password, user))) {
    return json({ error: "Username or password is wrong." }, 401);
  }

  const token = crypto.randomUUID();
  await env.DB.prepare("INSERT INTO sessions (token, user_id) VALUES (?1, ?2)").bind(token, user.id).run();
  return json(publicUser(user, token));
}

async function me(request: Request, env: Env): Promise<Response> {
  const token = bearer(request);
  if (!token) return json({ error: "Sign in again." }, 401);

  const user = await env.DB.prepare(
    `SELECT users.id, users.username, users.email, users.first_name, users.last_name
     FROM sessions JOIN users ON users.id = sessions.user_id
     WHERE sessions.token = ?1`,
  )
    .bind(token)
    .first<Omit<UserRow, "password_hash" | "password_salt">>();
  if (!user) return json({ error: "Sign in again." }, 401);
  return json(publicUser(user, token));
}

function publicUser(user: Omit<UserRow, "password_hash" | "password_salt">, token: string) {
  return {
    id: user.id,
    username: user.username,
    email: user.email,
    firstName: user.first_name,
    lastName: user.last_name,
    token,
  };
}

async function hashPassword(password: string, salt: Uint8Array): Promise<string> {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt, iterations, hash: "SHA-256" },
    key,
    256,
  );
  return encodeBase64(new Uint8Array(bits));
}

async function passwordMatches(password: string, user: UserRow): Promise<boolean> {
  const actual = await hashPassword(password, decodeBase64(user.password_salt));
  return actual === user.password_hash;
}

function bearer(request: Request): string | null {
  const header = request.headers.get("Authorization") ?? "";
  const match = header.match(/^Bearer\s+(.+)$/i);
  return match?.[1] ?? null;
}

async function readJSON(request: Request): Promise<Record<string, unknown>> {
  try {
    const value = await request.json();
    if (value && typeof value === "object" && !Array.isArray(value)) {
      return value as Record<string, unknown>;
    }
  } catch {
    // Fall through to the empty object.
  }
  return {};
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function cors(response: Response): Response {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
  headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  return new Response(response.body, { status: response.status, headers });
}

function encodeBase64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function decodeBase64(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}
