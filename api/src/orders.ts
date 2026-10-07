import type { Context } from "./http.ts";
import type { Env, User } from "./env.ts";
import { deliveryFeeCents, paymentsEnabled } from "./env.ts";
import { HttpError, cents, id, isoDate, json, minutesFromNow, now, readJSON, shortName, text } from "./http.ts";
import { requireUser, userByID } from "./auth.ts";
import type { Building, Point, RestaurantRow } from "./places.ts";
import { buildingMap, restaurantMap, walkMiles, walkMinutes } from "./places.ts";
import { paymentSheet, stripe, type PaymentIntent } from "./stripe.ts";

export type OrderStatus =
  | "awaiting_payment"
  | "matching"
  | "accepted"
  | "picked_up"
  | "delivered"
  | "cancelled"
  | "expired";

export type OrderRow = {
  id: string;
  customer_id: string;
  restaurant_id: string;
  order_number: string;
  name_on_order: string;
  ready_at: string | null;
  dropoff_building_id: string;
  dropoff_room: string;
  dropoff_note: string;
  delivery_fee_cents: number;
  tip_cents: number;
  status: OrderStatus;
  runner_id: string | null;
  trip_id: string | null;
  payment_intent_id: string | null;
  created_at: string;
  matching_at: string | null;
  accepted_at: string | null;
  picked_up_at: string | null;
  delivered_at: string | null;
  cancelled_at: string | null;
  cancel_reason: string | null;
};

/** Statuses where an order is still in flight. */
export const activeStatuses: OrderStatus[] = ["awaiting_payment", "matching", "accepted", "picked_up"];
const activeList = activeStatuses.map((status) => `'${status}'`).join(", ");

/** How long a runner's last location counts as live. */
const liveLocationMinutes = 5;
const maxTipCents = 2000;

export type Places = { buildings: Map<string, Building>; restaurants: Map<string, RestaurantRow> };

export async function places(env: Env): Promise<Places> {
  const [buildings, restaurants] = await Promise.all([buildingMap(env), restaurantMap(env)]);
  return { buildings, restaurants };
}

export async function orderByID(env: Env, orderID: string): Promise<OrderRow | null> {
  return env.DB.prepare("SELECT * FROM orders WHERE id = ?1").bind(orderID).first<OrderRow>();
}

/** The order if the user is its customer or runner, else 404. */
export async function participantOrder(env: Env, orderID: string, user: User): Promise<OrderRow> {
  const order = await orderByID(env, orderID);
  if (!order || (order.customer_id !== user.id && order.runner_id !== user.id)) {
    throw new HttpError(404, "Order not found.");
  }
  return order;
}

export function pickupAndDropoff(order: OrderRow, { buildings, restaurants }: Places) {
  const restaurant = restaurants.get(order.restaurant_id)!;
  return {
    restaurant,
    pickup: buildings.get(restaurant.building_id)!,
    dropoff: buildings.get(order.dropoff_building_id)!,
  };
}

/** Rough minutes until hand-off, from the runner's live location when we have one. */
function etaMinutes(order: OrderRow, pickup: Building, dropoff: Building, runner: Point | null): number | null {
  if (order.status === "accepted") {
    const from = runner ?? pickup;
    const toPickup = runner ? walkMinutes(from, pickup) : 0;
    const untilReady = order.ready_at ? Math.max(0, (Date.parse(order.ready_at) - Date.now()) / 60_000) : 0;
    return Math.round(Math.max(toPickup, untilReady) + walkMinutes(pickup, dropoff));
  }
  if (order.status === "picked_up") return walkMinutes(runner ?? pickup, dropoff);
  return null;
}

/** Order as JSON for its customer or runner. */
export async function orderView(env: Env, order: OrderRow, viewer: User, known?: Places) {
  const placeData = known ?? (await places(env));
  const { restaurant, pickup, dropoff } = pickupAndDropoff(order, placeData);
  const asRunner = order.runner_id === viewer.id && order.customer_id !== viewer.id;

  let runner = null;
  let location: (Point & { updatedAt: string }) | null = null;
  if (order.runner_id) {
    const person = await userByID(env, order.runner_id);
    if (person) {
      const stats = await runnerStats(env, person.id);
      const trip = order.trip_id
        ? await env.DB.prepare(
            "SELECT trips.note, buildings.name AS destination FROM trips JOIN buildings ON buildings.id = trips.to_building_id WHERE trips.id = ?1",
          )
            .bind(order.trip_id)
            .first<{ note: string; destination: string }>()
        : null;
      const live =
        person.runner_lat !== null &&
        person.runner_lng !== null &&
        person.runner_seen_at !== null &&
        person.runner_seen_at > minutesFromNow(-liveLocationMinutes);
      if (live && (order.status === "accepted" || order.status === "picked_up")) {
        location = { lat: person.runner_lat!, lng: person.runner_lng!, updatedAt: person.runner_seen_at! };
      }
      runner = {
        id: person.id,
        name: shortName(person.first_name, person.last_name),
        firstName: person.first_name,
        rating: stats.rating,
        deliveries: stats.deliveries,
        blurb: person.blurb,
        routeNote: trip
          ? `${person.first_name} was already heading to ${trip.destination}${trip.note ? ` for ${trip.note}` : ""}.`
          : null,
        location,
      };
    }
  }

  const customer = await userByID(env, order.customer_id);
  const customerOrders = await env.DB.prepare(
    "SELECT COUNT(*) AS count FROM orders WHERE customer_id = ?1 AND status = 'delivered'",
  )
    .bind(order.customer_id)
    .first<{ count: number }>();
  const rating = await env.DB.prepare("SELECT stars, tags FROM ratings WHERE order_id = ?1")
    .bind(order.id)
    .first<{ stars: number; tags: string }>();
  const extraTips = await env.DB.prepare(
    "SELECT COALESCE(SUM(amount_cents), 0) AS total FROM tips WHERE order_id = ?1 AND status = 'paid'",
  )
    .bind(order.id)
    .first<{ total: number }>();

  return {
    id: order.id,
    role: asRunner ? "runner" : "customer",
    status: order.status,
    number: order.order_number,
    nameOnOrder: order.name_on_order,
    readyAt: order.ready_at,
    restaurant: {
      id: restaurant.id,
      name: restaurant.name,
      transactId: restaurant.transact_id,
      building: { id: pickup.id, name: pickup.name },
    },
    destination: { buildingId: dropoff.id, building: dropoff.name, room: order.dropoff_room, note: order.dropoff_note },
    walkMinutes: walkMinutes(pickup, dropoff),
    walkMiles: walkMiles(pickup, dropoff),
    deliveryFeeCents: order.delivery_fee_cents,
    tipCents: order.tip_cents,
    extraTipCents: extraTips?.total ?? 0,
    payoutCents: order.delivery_fee_cents + order.tip_cents + (extraTips?.total ?? 0),
    etaMinutes: etaMinutes(order, pickup, dropoff, location),
    runner,
    customer: customer
      ? { id: customer.id, name: shortName(customer.first_name, customer.last_name), orders: customerOrders?.count ?? 0 }
      : null,
    rating: rating ? { stars: rating.stars, tags: JSON.parse(rating.tags) as string[] } : null,
    createdAt: order.created_at,
    acceptedAt: order.accepted_at,
    pickedUpAt: order.picked_up_at,
    deliveredAt: order.delivered_at,
    cancelledAt: order.cancelled_at,
    cancelReason: order.cancel_reason,
  };
}

export async function runnerStats(env: Env, runnerID: string): Promise<{ rating: number | null; deliveries: number }> {
  const row = await env.DB.prepare(
    `SELECT (SELECT ROUND(AVG(stars), 1) FROM ratings WHERE runner_id = ?1) AS rating,
            (SELECT COUNT(*) FROM orders WHERE runner_id = ?1 AND status = 'delivered') AS deliveries`,
  )
    .bind(runnerID)
    .first<{ rating: number | null; deliveries: number }>();
  return { rating: row?.rating ?? null, deliveries: row?.deliveries ?? 0 };
}

/**
 * POST /orders `{ restaurantId, orderNumber, nameOnOrder?, readyAt?, tipCents, destination? }`
 * Returns `{ order, payment }`; `payment` is the PaymentSheet config, or null when payments are off.
 */
export async function createOrder(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const body = await readJSON(c.request);
  const known = await places(c.env);

  const restaurant = known.restaurants.get(text(body.restaurantId, 64));
  if (!restaurant) throw new HttpError(400, "Pick a restaurant.");
  const orderNumber = text(body.orderNumber, 20);
  if (!orderNumber) throw new HttpError(400, "Enter your Transact order number.");
  const tip = cents(body.tipCents ?? 0);
  if (tip === null || tip > maxTipCents) throw new HttpError(400, "Pick a tip.");

  const requested = body.destination && typeof body.destination === "object" ? (body.destination as Record<string, unknown>) : {};
  const buildingID = text(requested.buildingId, 64) || user.dropoff_building_id || "";
  if (!known.buildings.has(buildingID)) throw new HttpError(400, "Pick where to deliver.", "destination_missing");
  const room = "buildingId" in requested ? text(requested.room, 60) : user.dropoff_room;
  const note = "buildingId" in requested ? text(requested.note, 200) : user.dropoff_note;

  // A checkout abandoned before paying is replaced; anything further along blocks a new order.
  const existing = await c.env.DB.prepare(
    `SELECT * FROM orders WHERE customer_id = ?1 AND status IN (${activeList}) ORDER BY created_at DESC LIMIT 1`,
  )
    .bind(user.id)
    .first<OrderRow>();
  if (existing?.status === "awaiting_payment") await cancel(c.env, existing, "Replaced by a new order");
  else if (existing) throw new HttpError(409, "You already have an order on the way.", "order_active");

  const orderID = id();
  const fee = deliveryFeeCents(c.env);
  const payNow = paymentsEnabled(c.env);
  const createdAt = now();
  await c.env.DB.batch([
    c.env.DB.prepare(
      `INSERT INTO orders (id, customer_id, restaurant_id, order_number, name_on_order, ready_at,
         dropoff_building_id, dropoff_room, dropoff_note, delivery_fee_cents, tip_cents, status, created_at, matching_at)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)`,
    ).bind(
      orderID,
      user.id,
      restaurant.id,
      orderNumber,
      text(body.nameOnOrder, 60) || shortName(user.first_name, user.last_name),
      isoDate(body.readyAt),
      buildingID,
      room,
      note,
      fee,
      tip,
      payNow ? "awaiting_payment" : "matching",
      createdAt,
      payNow ? null : createdAt,
    ),
    // Remember the drop-off for next time.
    c.env.DB.prepare(
      "UPDATE users SET dropoff_building_id = ?2, dropoff_room = ?3, dropoff_note = ?4 WHERE id = ?1",
    ).bind(user.id, buildingID, room, note),
  ]);

  let payment = null;
  if (payNow) {
    const { intentID, sheet } = await paymentSheet(
      c.env,
      user,
      fee + tip,
      "manual",
      { order_id: orderID, kind: "delivery" },
      `order-${orderID}`,
    );
    await c.env.DB.prepare("UPDATE orders SET payment_intent_id = ?2 WHERE id = ?1").bind(orderID, intentID).run();
    payment = sheet;
  }

  const order = (await orderByID(c.env, orderID))!;
  return json({ order: await orderView(c.env, order, user, known), payment }, 201);
}

/** POST /orders/:id/payment — the app finished PaymentSheet; check with Stripe and start matching. */
export async function confirmPayment(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const order = await participantOrder(c.env, c.params.id, user);
  if (order.customer_id !== user.id) throw new HttpError(404, "Order not found.");
  if (order.status === "awaiting_payment" && order.payment_intent_id) {
    const intent = await stripe<PaymentIntent>(c.env, "GET", `payment_intents/${order.payment_intent_id}`);
    if (intent.status === "requires_capture" || intent.status === "succeeded") {
      await markPaid(c.env, order.id);
    } else {
      throw new HttpError(402, "Payment didn't go through.", "payment_incomplete");
    }
  }
  return json({ order: await orderView(c.env, (await orderByID(c.env, order.id))!, user) });
}

/** Payment authorized: the order becomes visible to runners. Safe to call twice. */
export async function markPaid(env: Env, orderID: string): Promise<void> {
  await env.DB.prepare(
    "UPDATE orders SET status = 'matching', matching_at = ?2 WHERE id = ?1 AND status = 'awaiting_payment'",
  )
    .bind(orderID, now())
    .run();
}

/** GET /orders/:id */
export async function getOrder(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const order = await participantOrder(c.env, c.params.id, user);
  return json({ order: await orderView(c.env, order, user) });
}

/** GET /orders/active — the customer's in-flight order, if any. */
export async function activeOrder(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const order = await c.env.DB.prepare(
    `SELECT * FROM orders WHERE customer_id = ?1 AND status IN (${activeList}) ORDER BY created_at DESC LIMIT 1`,
  )
    .bind(user.id)
    .first<OrderRow>();
  return json({ order: order ? await orderView(c.env, order, user) : null });
}

/** GET /orders?before=ISO — the customer's past orders, newest first. */
export async function listOrders(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const before = isoDate(c.url.searchParams.get("before")) ?? "9999";
  const { results } = await c.env.DB.prepare(
    "SELECT * FROM orders WHERE customer_id = ?1 AND created_at < ?2 AND status != 'awaiting_payment' ORDER BY created_at DESC LIMIT 20",
  )
    .bind(user.id, before)
    .all<OrderRow>();
  const known = await places(c.env);
  return json({ orders: await Promise.all(results.map((order) => orderView(c.env, order, user, known))) });
}

/** POST /orders/:id/cancel — only before a runner accepts. */
export async function cancelOrder(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const order = await participantOrder(c.env, c.params.id, user);
  if (order.customer_id !== user.id) throw new HttpError(403, "Only the customer can cancel.");
  if (!(await cancel(c.env, order, "Cancelled by customer"))) {
    throw new HttpError(409, "A runner already has this order. Message them instead.", "order_taken");
  }
  return json({ order: await orderView(c.env, (await orderByID(c.env, order.id))!, user) });
}

/** Cancels an order that no runner holds, releasing the card hold. Returns false if it was too late. */
export async function cancel(env: Env, order: OrderRow, reason: string, status: "cancelled" | "expired" = "cancelled") {
  const result = await env.DB.prepare(
    `UPDATE orders SET status = ?2, cancelled_at = ?3, cancel_reason = ?4
     WHERE id = ?1 AND status IN ('awaiting_payment', 'matching')`,
  )
    .bind(order.id, status, now(), reason)
    .run();
  if (!result.meta.changes) return false;
  if (order.payment_intent_id && paymentsEnabled(env)) {
    try {
      await stripe(env, "POST", `payment_intents/${order.payment_intent_id}/cancel`);
    } catch (error) {
      // Already cancelled or never confirmed; nothing is held either way.
      console.warn("PaymentIntent cancel failed", order.payment_intent_id, error);
    }
  }
  return true;
}

/** POST /orders/:id/report `{ reason, details }` */
export async function reportOrder(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const order = await participantOrder(c.env, c.params.id, user);
  const body = await readJSON(c.request);
  const reason = text(body.reason, 60);
  if (!reason) throw new HttpError(400, "Say what went wrong.");
  await c.env.DB.prepare(
    "INSERT INTO reports (id, order_id, reporter_id, reason, details, created_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
  )
    .bind(id(), order.id, user.id, reason, text(body.details, 1000), now())
    .run();
  return json({ ok: true }, 201);
}
