import type { Context } from "./http.ts";
import type { Env } from "./env.ts";
import { paymentsEnabled } from "./env.ts";
import { HttpError, id, isoDate, json, now, shortName } from "./http.ts";
import { requireUser, userByID } from "./auth.ts";
import { walkMinutes } from "./places.ts";
import type { OrderRow } from "./orders.ts";
import { pickupAndDropoff, places } from "./orders.ts";
import { stripe, type PaymentIntent } from "./stripe.ts";

export async function balanceCents(env: Env, userID: string): Promise<number> {
  const row = await env.DB.prepare("SELECT COALESCE(SUM(amount_cents), 0) AS total FROM ledger WHERE user_id = ?1")
    .bind(userID)
    .first<{ total: number }>();
  return row?.total ?? 0;
}

/**
 * After drop-off: capture the held payment, then credit the runner fee + tip.
 * Idempotent; the Stripe webhook calls `creditDelivery` too.
 */
export async function settleDelivery(env: Env, order: OrderRow): Promise<void> {
  if (order.status !== "delivered" || !order.runner_id) return;
  if (paymentsEnabled(env) && order.payment_intent_id) {
    const intent = await stripe<PaymentIntent>(env, "GET", `payment_intents/${order.payment_intent_id}`);
    if (intent.status === "requires_capture") {
      await stripe(env, "POST", `payment_intents/${intent.id}/capture`, {}, { idempotencyKey: `capture-${order.id}` });
    } else if (intent.status !== "succeeded") {
      console.error("Delivered order has no captured payment", order.id, intent.status);
      return;
    }
  }
  await creditDelivery(env, order);
}

export async function creditDelivery(env: Env, order: OrderRow): Promise<void> {
  if (!order.runner_id) return;
  await env.DB.prepare(
    `INSERT OR IGNORE INTO ledger (id, user_id, order_id, kind, amount_cents, created_at)
     VALUES (?1, ?2, ?3, 'delivery', ?4, ?5)`,
  )
    .bind(id(), order.runner_id, order.id, order.delivery_fee_cents + order.tip_cents, now())
    .run();
}

/** Credits a paid extra tip to its runner. Idempotent. */
export async function creditTip(env: Env, tipID: string): Promise<void> {
  const tip = await env.DB.prepare("SELECT * FROM tips WHERE id = ?1")
    .bind(tipID)
    .first<{ id: string; order_id: string; runner_id: string; amount_cents: number }>();
  if (!tip) return;
  await env.DB.batch([
    env.DB.prepare("UPDATE tips SET status = 'paid' WHERE id = ?1").bind(tip.id),
    env.DB.prepare(
      `INSERT OR IGNORE INTO ledger (id, user_id, order_id, tip_id, kind, amount_cents, created_at)
       VALUES (?1, ?2, ?3, ?4, 'tip', ?5, ?6)`,
    ).bind(id(), tip.runner_id, tip.order_id, tip.id, tip.amount_cents, now()),
  ]);
}

// MARK: Earnings

/** YYYY-MM-DD in the campus time zone. */
function campusDate(env: Env, date: Date): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: env.CAMPUS_TIME_ZONE }).format(date);
}

function addDays(day: string, count: number): string {
  const date = new Date(`${day}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + count);
  return date.toISOString().slice(0, 10);
}

/** Monday of the week containing `day`. */
function weekStart(day: string): string {
  const weekday = new Date(`${day}T12:00:00Z`).getUTCDay(); // 0 = Sunday
  return addDays(day, -((weekday + 6) % 7));
}

/**
 * GET /earnings?week=this|last
 * Daily totals Monday–Sunday (campus time), trip stats, change vs the week before, and balance.
 */
export async function earnings(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const thisWeek = weekStart(campusDate(c.env, new Date()));
  const start = c.url.searchParams.get("week") === "last" ? addDays(thisWeek, -7) : thisWeek;
  const days = Array.from({ length: 7 }, (_, index) => addDays(start, index));
  const previous = Array.from({ length: 7 }, (_, index) => addDays(start, index - 7));

  // A day either side of the two weeks covers any time-zone offset.
  const from = `${addDays(start, -8)}T00:00:00Z`;
  const to = `${addDays(start, 8)}T00:00:00Z`;
  const { results: credits } = await c.env.DB.prepare(
    `SELECT amount_cents, created_at FROM ledger
     WHERE user_id = ?1 AND kind IN ('delivery', 'tip') AND created_at BETWEEN ?2 AND ?3`,
  )
    .bind(user.id, from, to)
    .all<{ amount_cents: number; created_at: string }>();
  const { results: delivered } = await c.env.DB.prepare(
    "SELECT * FROM orders WHERE runner_id = ?1 AND status = 'delivered' AND delivered_at BETWEEN ?2 AND ?3",
  )
    .bind(user.id, from, to)
    .all<OrderRow>();

  const byDay = new Map<string, number>();
  for (const credit of credits) {
    const day = campusDate(c.env, new Date(credit.created_at));
    byDay.set(day, (byDay.get(day) ?? 0) + credit.amount_cents);
  }
  const total = days.reduce((sum, day) => sum + (byDay.get(day) ?? 0), 0);
  const previousTotal = previous.reduce((sum, day) => sum + (byDay.get(day) ?? 0), 0);

  const known = await places(c.env);
  const inWeek = delivered.filter((order) => days.includes(campusDate(c.env, new Date(order.delivered_at!))));
  const walking = inWeek.reduce((sum, order) => {
    const { pickup, dropoff } = pickupAndDropoff(order, known);
    return sum + walkMinutes(pickup, dropoff);
  }, 0);

  return json({
    start,
    end: days[6],
    days: days.map((day) => ({ date: day, cents: byDay.get(day) ?? 0 })),
    totalCents: total,
    changePercent: previousTotal > 0 ? Math.round(((total - previousTotal) / previousTotal) * 100) : null,
    deliveries: inWeek.length,
    walkingMinutes: walking,
    averageCents: inWeek.length ? Math.round(total / inWeek.length) : 0,
    balanceCents: await balanceCents(c.env, user.id),
    payoutsEnabled: !paymentsEnabled(c.env) || user.payouts_enabled === 1,
  });
}

/** GET /deliveries?before=ISO — the runner's completed deliveries, newest first. */
export async function deliveries(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const before = isoDate(c.url.searchParams.get("before")) ?? "9999";
  const { results } = await c.env.DB.prepare(
    `SELECT orders.*, ratings.stars,
       (SELECT COALESCE(SUM(amount_cents), 0) FROM tips WHERE tips.order_id = orders.id AND tips.status = 'paid') AS extra_tip
     FROM orders LEFT JOIN ratings ON ratings.order_id = orders.id
     WHERE orders.runner_id = ?1 AND orders.status = 'delivered' AND orders.delivered_at < ?2
     ORDER BY orders.delivered_at DESC LIMIT 20`,
  )
    .bind(user.id, before)
    .all<OrderRow & { stars: number | null; extra_tip: number }>();

  const known = await places(c.env);
  const items = await Promise.all(
    results.map(async (order) => {
      const { restaurant, dropoff } = pickupAndDropoff(order, known);
      const customer = await userByID(c.env, order.customer_id);
      return {
        id: order.id,
        restaurant: restaurant.name,
        building: dropoff.name,
        customer: customer ? shortName(customer.first_name, customer.last_name) : "Student",
        deliveredAt: order.delivered_at,
        amountCents: order.delivery_fee_cents + order.tip_cents + order.extra_tip,
        tipCents: order.tip_cents + order.extra_tip,
        stars: order.stars,
      };
    }),
  );
  return json({ deliveries: items });
}

// MARK: Payouts

/** POST /runner/payout-account — Stripe Connect onboarding link (creates the account first time). */
export async function payoutAccountLink(c: Context): Promise<Response> {
  const user = await requireUser(c);
  let account = user.stripe_account_id;
  if (!account) {
    const created = await stripe<{ id: string }>(
      c.env,
      "POST",
      "accounts",
      {
        type: "express",
        country: "US",
        email: user.email,
        business_type: "individual",
        capabilities: { transfers: { requested: true } },
        metadata: { user_id: user.id },
      },
      { idempotencyKey: `account-${user.id}` },
    );
    account = created.id;
    await c.env.DB.prepare("UPDATE users SET stripe_account_id = ?2 WHERE id = ?1").bind(user.id, account).run();
  }
  const origin = c.url.origin;
  const link = await stripe<{ url: string }>(c.env, "POST", "account_links", {
    account,
    type: "account_onboarding",
    refresh_url: `${origin}/stripe/connect/refresh`,
    return_url: `${origin}/stripe/connect/return`,
  });
  return json({ url: link.url });
}

/** GET /runner/payout-account — refreshes whether Stripe lets this runner receive money. */
export async function payoutAccountStatus(c: Context): Promise<Response> {
  const user = await requireUser(c);
  if (!paymentsEnabled(c.env)) return json({ connected: true, payoutsEnabled: true });
  if (!user.stripe_account_id) return json({ connected: false, payoutsEnabled: false });
  const enabled = await refreshPayoutsEnabled(c.env, user.stripe_account_id);
  return json({ connected: true, payoutsEnabled: enabled });
}

export async function refreshPayoutsEnabled(env: Env, accountID: string): Promise<boolean> {
  const account = await stripe<{ payouts_enabled: boolean; capabilities?: { transfers?: string } }>(
    env,
    "GET",
    `accounts/${accountID}`,
  );
  const enabled = account.payouts_enabled && account.capabilities?.transfers === "active";
  await env.DB.prepare("UPDATE users SET payouts_enabled = ?2 WHERE stripe_account_id = ?1")
    .bind(accountID, enabled ? 1 : 0)
    .run();
  return enabled;
}

/** POST /payouts — sends the whole balance to the runner's Stripe account. */
export async function cashOut(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const stripeOn = paymentsEnabled(c.env);
  if (stripeOn) {
    if (!user.stripe_account_id) throw new HttpError(409, "Add a bank account first.", "payout_account_missing");
    if (!user.payouts_enabled && !(await refreshPayoutsEnabled(c.env, user.stripe_account_id))) {
      throw new HttpError(409, "Finish setting up payouts first.", "payout_account_incomplete");
    }
  }

  // Reserve the balance in one transaction so two taps can't pay out twice.
  const payoutID = id();
  const createdAt = now();
  await c.env.DB.batch([
    c.env.DB.prepare(
      `INSERT INTO payouts (id, runner_id, amount_cents, status, created_at)
       SELECT ?1, ?2, SUM(amount_cents), 'pending', ?3 FROM ledger WHERE user_id = ?2 HAVING SUM(amount_cents) > 0`,
    ).bind(payoutID, user.id, createdAt),
    c.env.DB.prepare(
      `INSERT INTO ledger (id, user_id, payout_id, kind, amount_cents, created_at)
       SELECT ?1, runner_id, id, 'payout', -amount_cents, ?3 FROM payouts WHERE id = ?2`,
    ).bind(id(), payoutID, createdAt),
  ]);
  const payout = await c.env.DB.prepare("SELECT amount_cents FROM payouts WHERE id = ?1")
    .bind(payoutID)
    .first<{ amount_cents: number }>();
  if (!payout) throw new HttpError(409, "Nothing to cash out yet.", "balance_empty");

  if (!stripeOn) {
    await c.env.DB.prepare("UPDATE payouts SET status = 'paid' WHERE id = ?1").bind(payoutID).run();
  } else {
    try {
      const transfer = await stripe<{ id: string }>(
        c.env,
        "POST",
        "transfers",
        { amount: payout.amount_cents, currency: "usd", destination: user.stripe_account_id!, metadata: { payout_id: payoutID } },
        { idempotencyKey: `payout-${payoutID}` },
      );
      await c.env.DB.prepare("UPDATE payouts SET status = 'paid', stripe_transfer_id = ?2 WHERE id = ?1")
        .bind(payoutID, transfer.id)
        .run();
    } catch (error) {
      await c.env.DB.batch([
        c.env.DB.prepare("UPDATE payouts SET status = 'failed', failure = ?2 WHERE id = ?1").bind(
          payoutID,
          error instanceof Error ? error.message : "Transfer failed",
        ),
        c.env.DB.prepare(
          `INSERT INTO ledger (id, user_id, payout_id, kind, amount_cents, created_at)
           VALUES (?1, ?2, ?3, 'payout_reversal', ?4, ?5)`,
        ).bind(id(), user.id, payoutID, payout.amount_cents, now()),
      ]);
      throw error;
    }
  }
  return json({ payout: { id: payoutID, amountCents: payout.amount_cents }, balanceCents: await balanceCents(c.env, user.id) }, 201);
}
