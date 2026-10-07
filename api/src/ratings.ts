import type { Context } from "./http.ts";
import { paymentsEnabled } from "./env.ts";
import { HttpError, cents, id, json, now, readJSON } from "./http.ts";
import { requireUser } from "./auth.ts";
import { participantOrder } from "./orders.ts";
import { paymentSheet, stripe, type PaymentIntent } from "./stripe.ts";
import { creditTip } from "./money.ts";

const tagOptions = ["On time", "Friendly", "Careful with food"];
const maxExtraTipCents = 2000;

/**
 * POST /orders/:id/rating `{ stars, tags, extraTipCents }`
 * Returns `{ tip, payment }`; `payment` is a PaymentSheet config when an extra tip needs charging.
 */
export async function rateOrder(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const order = await participantOrder(c.env, c.params.id, user);
  if (order.customer_id !== user.id || !order.runner_id) throw new HttpError(403, "Only the customer can rate.");
  if (order.status !== "delivered") throw new HttpError(409, "Rate after your order arrives.");

  const body = await readJSON(c.request);
  const stars = Number(body.stars);
  if (!Number.isInteger(stars) || stars < 1 || stars > 5) throw new HttpError(400, "Pick 1 to 5 stars.");
  const tags = Array.isArray(body.tags) ? body.tags.filter((tag): tag is string => tagOptions.includes(tag as string)) : [];
  const extraTip = cents(body.extraTipCents ?? 0);
  if (extraTip === null || extraTip > maxExtraTipCents) throw new HttpError(400, "Pick a tip.");

  const inserted = await c.env.DB.prepare(
    `INSERT OR IGNORE INTO ratings (order_id, customer_id, runner_id, stars, tags, created_at)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6)`,
  )
    .bind(order.id, user.id, order.runner_id, stars, JSON.stringify(tags), now())
    .run();
  if (!inserted.meta.changes) throw new HttpError(409, "You already rated this order.", "already_rated");

  if (!extraTip) return json({ tip: null, payment: null }, 201);

  const tipID = id();
  await c.env.DB.prepare(
    `INSERT INTO tips (id, order_id, customer_id, runner_id, amount_cents, status, created_at)
     VALUES (?1, ?2, ?3, ?4, ?5, 'pending', ?6)`,
  )
    .bind(tipID, order.id, user.id, order.runner_id, extraTip, now())
    .run();

  if (!paymentsEnabled(c.env)) {
    await creditTip(c.env, tipID);
    return json({ tip: { id: tipID, amountCents: extraTip, status: "paid" }, payment: null }, 201);
  }
  const { intentID, sheet } = await paymentSheet(
    c.env,
    user,
    extraTip,
    "automatic",
    { tip_id: tipID, order_id: order.id, kind: "tip" },
    `tip-${tipID}`,
  );
  await c.env.DB.prepare("UPDATE tips SET payment_intent_id = ?2 WHERE id = ?1").bind(tipID, intentID).run();
  return json({ tip: { id: tipID, amountCents: extraTip, status: "pending" }, payment: sheet }, 201);
}

/** POST /tips/:id/payment — the app finished PaymentSheet for an extra tip. */
export async function confirmTip(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const tip = await c.env.DB.prepare("SELECT id, status, payment_intent_id FROM tips WHERE id = ?1 AND customer_id = ?2")
    .bind(c.params.id, user.id)
    .first<{ id: string; status: string; payment_intent_id: string | null }>();
  if (!tip) throw new HttpError(404, "Tip not found.");
  if (tip.status === "pending" && tip.payment_intent_id) {
    const intent = await stripe<PaymentIntent>(c.env, "GET", `payment_intents/${tip.payment_intent_id}`);
    if (intent.status !== "succeeded") throw new HttpError(402, "Payment didn't go through.", "payment_incomplete");
    await creditTip(c.env, tip.id);
  }
  return json({ tip: { id: tip.id, status: "paid" } });
}
