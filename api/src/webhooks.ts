import type { Context } from "./http.ts";
import type { Env } from "./env.ts";
import { json, now } from "./http.ts";
import { verifyWebhook } from "./stripe.ts";
import { cancel, markPaid, orderByID } from "./orders.ts";
import { creditDelivery, creditTip, refreshPayoutsEnabled } from "./money.ts";

/**
 * POST /stripe/webhook — backs up the app's own confirm calls, so an order still
 * moves on if the app is killed right after paying.
 * Subscribe to: payment_intent.amount_capturable_updated, payment_intent.succeeded,
 * payment_intent.payment_failed, account.updated.
 */
export async function stripeWebhook(c: Context): Promise<Response> {
  const event = await verifyWebhook(c.env, c.request);
  const fresh = await c.env.DB.prepare("INSERT OR IGNORE INTO stripe_events (id, type, created_at) VALUES (?1, ?2, ?3)")
    .bind(event.id, event.type, now())
    .run();
  if (!fresh.meta.changes) return json({ received: true, duplicate: true });

  try {
    await handle(c.env, event.type, event.data.object);
  } catch (error) {
    // Let Stripe retry.
    await c.env.DB.prepare("DELETE FROM stripe_events WHERE id = ?1").bind(event.id).run();
    throw error;
  }
  return json({ received: true });
}

async function handle(env: Env, type: string, object: any): Promise<void> {
  const metadata: Record<string, string> = object.metadata ?? {};
  switch (type) {
    case "payment_intent.amount_capturable_updated":
      if (metadata.kind === "delivery" && metadata.order_id) await markPaid(env, metadata.order_id);
      break;
    case "payment_intent.succeeded":
      if (metadata.kind === "delivery" && metadata.order_id) {
        const order = await orderByID(env, metadata.order_id);
        if (order?.status === "delivered") await creditDelivery(env, order);
      }
      if (metadata.kind === "tip" && metadata.tip_id) await creditTip(env, metadata.tip_id);
      break;
    case "payment_intent.payment_failed":
      if (metadata.kind === "tip" && metadata.tip_id) {
        await env.DB.prepare("UPDATE tips SET status = 'failed' WHERE id = ?1 AND status = 'pending'")
          .bind(metadata.tip_id)
          .run();
      }
      if (metadata.kind === "delivery" && metadata.order_id) {
        const order = await orderByID(env, metadata.order_id);
        if (order?.status === "awaiting_payment") await cancel(env, order, "Payment failed");
      }
      break;
    case "account.updated":
      await refreshPayoutsEnabled(env, object.id);
      break;
  }
}

/** Landing pages for the end of Stripe Connect onboarding. */
export async function connectReturn(): Promise<Response> {
  return page("You're set up", "Head back to On My Way to cash out.");
}

export async function connectRefresh(): Promise<Response> {
  return page("Link expired", "Go back to On My Way and tap Cash out again to continue setup.");
}

function page(title: string, message: string): Response {
  return new Response(
    `<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title>
<body style="font-family:-apple-system,system-ui,sans-serif;text-align:center;padding:64px 24px">
<h1>${title}</h1><p>${message}</p></body>`,
    { headers: { "Content-Type": "text/html; charset=utf-8" } },
  );
}
