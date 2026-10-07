import type { Env } from "./env.ts";
import { minutesFromNow, now } from "./http.ts";
import type { OrderRow } from "./orders.ts";
import { cancel } from "./orders.ts";

/** Nobody accepted in time. */
const matchingMinutes = 20;
/** Checkout opened but never paid. */
const unpaidMinutes = 30;
/** Runners who stop checking in go offline. */
const runnerIdleMinutes = 120;

/** Runs every minute from the cron trigger. */
export async function sweep(env: Env): Promise<void> {
  const { results: stale } = await env.DB.prepare(
    `SELECT * FROM orders
     WHERE (status = 'matching' AND matching_at < ?1) OR (status = 'awaiting_payment' AND created_at < ?2)
     LIMIT 50`,
  )
    .bind(minutesFromNow(-matchingMinutes), minutesFromNow(-unpaidMinutes))
    .all<OrderRow>();
  for (const order of stale) {
    const reason = order.status === "matching" ? "No runner was heading your way" : "Payment wasn't finished";
    await cancel(env, order, reason, "expired");
  }

  await env.DB.prepare(
    `UPDATE users SET runner_available = 0
     WHERE runner_available = 1 AND (runner_seen_at IS NULL OR runner_seen_at < ?1)
       AND id NOT IN (SELECT runner_id FROM orders WHERE status IN ('accepted', 'picked_up') AND runner_id IS NOT NULL)`,
  )
    .bind(minutesFromNow(-runnerIdleMinutes))
    .run();

  await env.DB.prepare("DELETE FROM sessions WHERE expires_at < ?1").bind(now()).run();
}
