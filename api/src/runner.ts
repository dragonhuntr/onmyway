import type { Context } from "./http.ts";
import type { Env, User } from "./env.ts";
import { HttpError, id, isoDate, json, minutesFromNow, now, readJSON, shortName, text } from "./http.ts";
import { requireUser } from "./auth.ts";
import type { Building, Point } from "./places.ts";
import { walkMiles, walkMinutes } from "./places.ts";
import type { OrderRow, Places } from "./orders.ts";
import { orderByID, orderView, participantOrder, pickupAndDropoff, places } from "./orders.ts";
import { balanceCents, settleDelivery } from "./money.ts";

type TripRow = {
  id: string;
  runner_id: string;
  from_building_id: string;
  to_building_id: string;
  leave_at: string;
  note: string;
  created_at: string;
  cancelled_at: string | null;
};

/** A detour this short counts as "on my route". */
const onRouteMinutes = 6;
/** Pickup this close to where the runner is counts as "nearby". */
const nearbyMiles = 0.25;

function tripView(trip: TripRow, buildings: Map<string, Building>) {
  const from = buildings.get(trip.from_building_id)!;
  const to = buildings.get(trip.to_building_id)!;
  return {
    id: trip.id,
    from: { id: from.id, name: from.name },
    to: { id: to.id, name: to.name },
    leaveAt: trip.leave_at,
    note: trip.note,
    walkMinutes: walkMinutes(from, to),
  };
}

/** The runner's current or next trip: left up to an hour ago, or leaving in the next 12 hours. */
async function currentTrip(env: Env, runnerID: string): Promise<TripRow | null> {
  return env.DB.prepare(
    `SELECT * FROM trips WHERE runner_id = ?1 AND cancelled_at IS NULL AND leave_at BETWEEN ?2 AND ?3
     ORDER BY leave_at LIMIT 1`,
  )
    .bind(runnerID, minutesFromNow(-60), minutesFromNow(12 * 60))
    .first<TripRow>();
}

async function activeDelivery(env: Env, runnerID: string): Promise<OrderRow | null> {
  return env.DB.prepare(
    "SELECT * FROM orders WHERE runner_id = ?1 AND status IN ('accepted', 'picked_up') ORDER BY accepted_at DESC LIMIT 1",
  )
    .bind(runnerID)
    .first<OrderRow>();
}

/** GET /runner — availability, next trip, the delivery in progress, and cash-out balance. */
export async function runnerHome(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const known = await places(c.env);
  const trip = await currentTrip(c.env, user.id);
  const delivery = await activeDelivery(c.env, user.id);
  return json({
    available: user.runner_available === 1,
    trip: trip ? tripView(trip, known.buildings) : null,
    activeDelivery: delivery ? await orderView(c.env, delivery, user, known) : null,
    balanceCents: await balanceCents(c.env, user.id),
  });
}

/** PUT /runner/status `{ available }` */
export async function setAvailability(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const available = (await readJSON(c.request)).available === true;
  await c.env.DB.prepare("UPDATE users SET runner_available = ?2, runner_seen_at = ?3 WHERE id = ?1")
    .bind(user.id, available ? 1 : 0, now())
    .run();
  return json({ available });
}

/** POST /runner/location `{ lat, lng }` — sent every ~15s while delivering. */
export async function updateLocation(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const body = await readJSON(c.request);
  const lat = Number(body.lat);
  const lng = Number(body.lng);
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
    throw new HttpError(400, "Bad location.");
  }
  await c.env.DB.prepare("UPDATE users SET runner_lat = ?2, runner_lng = ?3, runner_seen_at = ?4 WHERE id = ?1")
    .bind(user.id, lat, lng, now())
    .run();
  return json({ ok: true });
}

// MARK: Trips

/** GET /trips — upcoming trips. */
export async function listTrips(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const { buildings } = await places(c.env);
  const { results } = await c.env.DB.prepare(
    "SELECT * FROM trips WHERE runner_id = ?1 AND cancelled_at IS NULL AND leave_at > ?2 ORDER BY leave_at",
  )
    .bind(user.id, minutesFromNow(-60))
    .all<TripRow>();
  return json({ trips: results.map((trip) => tripView(trip, buildings)) });
}

/** POST /trips `{ fromBuildingId, toBuildingId, leaveAt, note }` */
export async function createTrip(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const { buildings } = await places(c.env);
  const fields = tripFields(await readJSON(c.request), buildings);
  const trip: TripRow = { id: id(), runner_id: user.id, ...fields, created_at: now(), cancelled_at: null };
  await c.env.DB.prepare(
    `INSERT INTO trips (id, runner_id, from_building_id, to_building_id, leave_at, note, created_at)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)`,
  )
    .bind(trip.id, trip.runner_id, trip.from_building_id, trip.to_building_id, trip.leave_at, trip.note, trip.created_at)
    .run();
  return json({ trip: tripView(trip, buildings) }, 201);
}

/** PUT /trips/:id — same body as POST. */
export async function updateTrip(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const { buildings } = await places(c.env);
  const fields = tripFields(await readJSON(c.request), buildings);
  const result = await c.env.DB.prepare(
    `UPDATE trips SET from_building_id = ?3, to_building_id = ?4, leave_at = ?5, note = ?6
     WHERE id = ?1 AND runner_id = ?2 AND cancelled_at IS NULL`,
  )
    .bind(c.params.id, user.id, fields.from_building_id, fields.to_building_id, fields.leave_at, fields.note)
    .run();
  if (!result.meta.changes) throw new HttpError(404, "Trip not found.");
  const trip = await c.env.DB.prepare("SELECT * FROM trips WHERE id = ?1").bind(c.params.id).first<TripRow>();
  return json({ trip: tripView(trip!, buildings) });
}

/** DELETE /trips/:id */
export async function deleteTrip(c: Context): Promise<Response> {
  const user = await requireUser(c);
  await c.env.DB.prepare("UPDATE trips SET cancelled_at = ?3 WHERE id = ?1 AND runner_id = ?2")
    .bind(c.params.id, user.id, now())
    .run();
  return json({ ok: true });
}

function tripFields(body: Record<string, unknown>, buildings: Map<string, Building>) {
  const from = text(body.fromBuildingId, 64);
  const to = text(body.toBuildingId, 64);
  if (!buildings.has(from) || !buildings.has(to)) throw new HttpError(400, "Pick where you're walking from and to.");
  if (from === to) throw new HttpError(400, "Pick two different buildings.");
  const leaveAt = isoDate(body.leaveAt);
  if (!leaveAt) throw new HttpError(400, "Pick when you're leaving.");
  return { from_building_id: from, to_building_id: to, leave_at: leaveAt, note: text(body.note, 120) };
}

// MARK: Requests feed

type Filter = "on_my_route" | "nearby" | "ready_now";

/**
 * GET /runner/requests?filter=on_my_route|nearby|ready_now
 * Paid orders waiting for a runner, scored against the runner's trip (or live location).
 */
export async function listRequests(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const filter = (c.url.searchParams.get("filter") ?? "on_my_route") as Filter;
  const known = await places(c.env);
  const trip = await currentTrip(c.env, user.id);
  const { results } = await c.env.DB.prepare(
    "SELECT * FROM orders WHERE status = 'matching' AND customer_id != ?1 ORDER BY created_at LIMIT 50",
  )
    .bind(user.id)
    .all<OrderRow>();

  const start: Point | null = trip
    ? known.buildings.get(trip.from_building_id)!
    : user.runner_lat !== null && user.runner_lng !== null
      ? { lat: user.runner_lat, lng: user.runner_lng }
      : null;
  const end = trip ? known.buildings.get(trip.to_building_id)! : null;

  const requests = await Promise.all(results.map((order) => requestView(c.env, order, known, start, end)));
  const visible = requests
    .filter((request) => {
      switch (filter) {
        case "nearby":
          return start === null || request.pickupMilesAway <= nearbyMiles;
        case "ready_now":
          return request.readyNow;
        default:
          return trip === null || request.detourMinutes <= onRouteMinutes;
      }
    })
    .sort((a, b) => a.detourMinutes - b.detourMinutes);

  return json({ trip: trip ? tripView(trip, known.buildings) : null, requests: visible });
}

async function requestView(env: Env, order: OrderRow, known: Places, start: Point | null, end: Point | null) {
  const { restaurant, pickup, dropoff } = pickupAndDropoff(order, known);
  const errand = walkMinutes(pickup, dropoff);
  let detourMinutes = errand;
  let detourMiles = walkMiles(pickup, dropoff);
  if (start && end) {
    detourMinutes = Math.max(0, walkMinutes(start, pickup) + errand + walkMinutes(dropoff, end) - walkMinutes(start, end));
    detourMiles = Math.max(0, Math.round((walkMiles(start, pickup) + walkMiles(pickup, dropoff) + walkMiles(dropoff, end) - walkMiles(start, end)) * 10) / 10);
  } else if (start) {
    detourMinutes = walkMinutes(start, pickup) + errand;
    detourMiles = Math.round((walkMiles(start, pickup) + walkMiles(pickup, dropoff)) * 10) / 10;
  }

  const customer = await env.DB.prepare(
    `SELECT users.first_name, users.last_name,
       (SELECT COUNT(*) FROM orders WHERE customer_id = users.id AND status = 'delivered') AS orders
     FROM users WHERE id = ?1`,
  )
    .bind(order.customer_id)
    .first<{ first_name: string; last_name: string; orders: number }>();

  const readyAt = order.ready_at ? Date.parse(order.ready_at) : Date.now();
  return {
    id: order.id,
    restaurant: { id: restaurant.id, name: restaurant.name, building: { id: pickup.id, name: pickup.name } },
    destination: { buildingId: dropoff.id, building: dropoff.name, room: order.dropoff_room, note: order.dropoff_note },
    number: order.order_number,
    nameOnOrder: order.name_on_order,
    readyAt: order.ready_at,
    readyNow: readyAt <= Date.now() + 2 * 60_000,
    dropoffBy: new Date(Math.max(readyAt, Date.now()) + (errand + 5) * 60_000).toISOString(),
    detourMinutes,
    detourMiles,
    onRoute: end !== null && detourMinutes <= onRouteMinutes,
    pickupMilesAway: start ? walkMiles(start, pickup) : 0,
    payoutCents: order.delivery_fee_cents + order.tip_cents,
    customer: customer
      ? { name: shortName(customer.first_name, customer.last_name), orders: customer.orders }
      : { name: "Student", orders: 0 },
  };
}

// MARK: Delivery steps

/** POST /orders/:id/accept `{ tripId? }` — first runner to call this gets the order. */
export async function acceptOrder(c: Context): Promise<Response> {
  const user = await requireUser(c);
  if (await activeDelivery(c.env, user.id)) {
    throw new HttpError(409, "Finish your current delivery first.", "delivery_active");
  }
  const tripID = text((await readJSON(c.request)).tripId, 64) || (await currentTrip(c.env, user.id))?.id || null;
  const result = await c.env.DB.prepare(
    `UPDATE orders SET status = 'accepted', runner_id = ?2, trip_id = ?3, accepted_at = ?4
     WHERE id = ?1 AND status = 'matching' AND customer_id != ?2`,
  )
    .bind(c.params.id, user.id, tripID, now())
    .run();
  if (!result.meta.changes) throw new HttpError(409, "Another runner took this order.", "order_taken");
  await c.env.DB.prepare("UPDATE users SET runner_available = 1, runner_seen_at = ?2 WHERE id = ?1")
    .bind(user.id, now())
    .run();
  return json({ order: await orderView(c.env, (await orderByID(c.env, c.params.id))!, user) });
}

/** POST /orders/:id/pickup */
export async function confirmPickup(c: Context): Promise<Response> {
  return step(c, "accepted", "picked_up", "picked_up_at");
}

/** POST /orders/:id/deliver — captures the payment and pays the runner. */
export async function confirmDropoff(c: Context): Promise<Response> {
  const response = await step(c, "picked_up", "delivered", "delivered_at");
  const order = (await orderByID(c.env, c.params.id))!;
  c.ctx.waitUntil(settleDelivery(c.env, order).catch((error) => console.error("Settle failed", order.id, error)));
  return response;
}

/** POST /orders/:id/release — runner gives an accepted order back before pickup. */
export async function releaseOrder(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const result = await c.env.DB.prepare(
    `UPDATE orders SET status = 'matching', runner_id = NULL, trip_id = NULL, accepted_at = NULL
     WHERE id = ?1 AND runner_id = ?2 AND status = 'accepted'`,
  )
    .bind(c.params.id, user.id)
    .run();
  if (!result.meta.changes) throw new HttpError(409, "You can only hand back an order before pickup.");
  return json({ ok: true });
}

async function step(c: Context, from: string, to: string, column: "picked_up_at" | "delivered_at"): Promise<Response> {
  const user: User = await requireUser(c);
  const order = await participantOrder(c.env, c.params.id, user);
  if (order.runner_id !== user.id) throw new HttpError(403, "This isn't your delivery.");
  const result = await c.env.DB.prepare(
    `UPDATE orders SET status = ?3, ${column} = ?4 WHERE id = ?1 AND runner_id = ?2 AND status = ?5`,
  )
    .bind(order.id, user.id, to, now(), from)
    .run();
  if (!result.meta.changes && order.status !== to) {
    throw new HttpError(409, "This order already moved on.", "order_state");
  }
  return json({ order: await orderView(c.env, (await orderByID(c.env, order.id))!, user) });
}
