import type { Env } from "./env.ts";

/** Thrown from handlers; becomes `{ error, code? }` with the given status. */
export class HttpError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly code?: string,
  ) {
    super(message);
  }
}

export type Context = {
  request: Request;
  env: Env;
  ctx: ExecutionContext;
  url: URL;
  params: Record<string, string>;
};

export type Handler = (c: Context) => Promise<Response>;

type Route = { method: string; parts: string[]; handler: Handler };

/** Matches `METHOD /path/:param` routes in the order they were added. */
export class Router {
  private routes: Route[] = [];

  on(method: string, path: string, handler: Handler): this {
    this.routes.push({ method, parts: path.split("/").filter(Boolean), handler });
    return this;
  }

  match(method: string, pathname: string): { handler: Handler; params: Record<string, string> } | null {
    const parts = pathname.split("/").filter(Boolean);
    for (const route of this.routes) {
      if (route.method !== method || route.parts.length !== parts.length) continue;
      const params: Record<string, string> = {};
      const matches = route.parts.every((part, index) => {
        if (part.startsWith(":")) {
          params[part.slice(1)] = decodeURIComponent(parts[index]);
          return true;
        }
        return part === parts[index];
      });
      if (matches) return { handler: route.handler, params };
    }
    return null;
  }
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function cors(response: Response): Response {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
  headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
  return new Response(response.body, { status: response.status, headers });
}

export async function readJSON(request: Request): Promise<Record<string, unknown>> {
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

export function text(value: unknown, maxLength = 500): string {
  return typeof value === "string" ? value.trim().slice(0, maxLength) : "";
}

/** Non-negative whole number, or null. */
export function cents(value: unknown): number | null {
  return typeof value === "number" && Number.isInteger(value) && value >= 0 ? value : null;
}

export function now(): string {
  return new Date().toISOString();
}

export function minutesFromNow(minutes: number): string {
  return new Date(Date.now() + minutes * 60_000).toISOString();
}

/** ISO timestamp, or null when missing or unparseable. */
export function isoDate(value: unknown): string | null {
  if (typeof value !== "string" || !value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

export function id(): string {
  return crypto.randomUUID();
}

export function encodeBase64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

export function decodeBase64(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

export function hex(bytes: ArrayBuffer): string {
  return [...new Uint8Array(bytes)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/** "Evan Brooks" → "Evan B." */
export function shortName(firstName: string, lastName: string): string {
  const initial = lastName.trim().charAt(0);
  return initial ? `${firstName} ${initial.toUpperCase()}.` : firstName;
}
