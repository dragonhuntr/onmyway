import type { Env, User } from "./env.ts";
import { HttpError, hex } from "./http.ts";

/** Pinned so ephemeral keys match what the iOS PaymentSheet expects. */
const apiVersion = "2024-06-20";

interface Params {
  [key: string]: string | number | boolean | undefined | Params;
}

export type PaymentIntent = {
  id: string;
  status: string;
  amount: number;
  client_secret: string;
  metadata: Record<string, string>;
};

/** Calls the Stripe REST API with form-encoded params. Throws a 502 with Stripe's message on failure. */
export async function stripe<T>(
  env: Env,
  method: "GET" | "POST" | "DELETE",
  path: string,
  params: Params = {},
  options: { idempotencyKey?: string } = {},
): Promise<T> {
  if (!env.STRIPE_SECRET_KEY) throw new HttpError(503, "Payments aren't set up yet.", "payments_unconfigured");
  const body = new URLSearchParams();
  flatten(params, "", body);
  const url = `https://api.stripe.com/v1/${path}${method === "GET" && body.size ? `?${body}` : ""}`;
  const headers: Record<string, string> = {
    Authorization: `Bearer ${env.STRIPE_SECRET_KEY}`,
    "Stripe-Version": apiVersion,
  };
  if (method !== "GET") headers["Content-Type"] = "application/x-www-form-urlencoded";
  if (options.idempotencyKey) headers["Idempotency-Key"] = options.idempotencyKey;

  const response = await fetch(url, { method, headers, body: method === "GET" ? undefined : body });
  const result = (await response.json()) as T & { error?: { message?: string } };
  if (!response.ok) {
    console.error("Stripe error", path, response.status, result.error);
    throw new HttpError(502, result.error?.message ?? "Payment failed. Try again.", "stripe_error");
  }
  return result;
}

function flatten(params: Params, prefix: string, into: URLSearchParams) {
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined) continue;
    const name = prefix ? `${prefix}[${key}]` : key;
    if (typeof value === "object") flatten(value, name, into);
    else into.append(name, String(value));
  }
}

/** The user's Stripe customer, created on first use. */
export async function customerFor(env: Env, user: User): Promise<string> {
  if (user.stripe_customer_id) return user.stripe_customer_id;
  const customer = await stripe<{ id: string }>(
    env,
    "POST",
    "customers",
    { email: user.email, name: `${user.first_name} ${user.last_name}`, metadata: { user_id: user.id } },
    { idempotencyKey: `customer-${user.id}` },
  );
  await env.DB.prepare("UPDATE users SET stripe_customer_id = ?2 WHERE id = ?1").bind(user.id, customer.id).run();
  return customer.id;
}

/**
 * Creates a PaymentIntent and returns what the iOS/Android PaymentSheet needs.
 * `capture: "manual"` only authorizes; the order is captured when delivered.
 */
export async function paymentSheet(
  env: Env,
  user: User,
  amountCents: number,
  capture: "manual" | "automatic",
  metadata: Record<string, string>,
  idempotencyKey: string,
) {
  const customer = await customerFor(env, user);
  const intent = await stripe<PaymentIntent>(
    env,
    "POST",
    "payment_intents",
    {
      amount: amountCents,
      currency: "usd",
      customer,
      capture_method: capture,
      automatic_payment_methods: { enabled: true },
      metadata,
    },
    { idempotencyKey },
  );
  const key = await stripe<{ secret: string }>(env, "POST", "ephemeral_keys", { customer });
  return {
    intentID: intent.id,
    sheet: {
      paymentIntentClientSecret: intent.client_secret,
      customerId: customer,
      customerEphemeralKeySecret: key.secret,
      publishableKey: env.STRIPE_PUBLISHABLE_KEY ?? "",
    },
  };
}

/** Checks the `Stripe-Signature` header and returns the parsed event. */
export async function verifyWebhook(env: Env, request: Request): Promise<{ id: string; type: string; data: { object: any } }> {
  if (!env.STRIPE_WEBHOOK_SECRET) throw new HttpError(503, "Webhook secret not set.");
  const payload = await request.text();
  const header = request.headers.get("Stripe-Signature") ?? "";
  const parts = header.split(",").map((part) => part.split("="));
  const timestamp = parts.find(([key]) => key === "t")?.[1];
  const signatures = parts.filter(([key]) => key === "v1").map(([, value]) => value);
  if (!timestamp || !signatures.length) throw new HttpError(400, "Missing signature.");
  if (Math.abs(Date.now() / 1000 - Number(timestamp)) > 300) throw new HttpError(400, "Stale signature.");

  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(env.STRIPE_WEBHOOK_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const expected = hex(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`${timestamp}.${payload}`)));
  if (!signatures.some((signature) => timingSafeEqual(signature, expected))) {
    throw new HttpError(400, "Bad signature.");
  }
  return JSON.parse(payload);
}

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let difference = 0;
  for (let index = 0; index < a.length; index += 1) difference |= a.charCodeAt(index) ^ b.charCodeAt(index);
  return difference === 0;
}
