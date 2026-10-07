import type { Context } from "./http.ts";
import type { Env, User } from "./env.ts";
import { HttpError, json, minutesFromNow, readJSON, text } from "./http.ts";
import { requireUser } from "./auth.ts";

export type Building = { id: string; name: string; lat: number; lng: number };
export type Point = { lat: number; lng: number };

export type RestaurantRow = {
  id: string;
  name: string;
  transact_id: number;
  building_id: string;
  opens_min: number;
  closes_min: number | null;
  sort: number;
};

/** Walking pace and how much longer real paths are than a straight line. */
const metersPerMinute = 80;
const pathFactor = 1.3;
const metersPerMile = 1609.34;

export function walkMinutes(a: Point, b: Point): number {
  return Math.max(1, Math.round((meters(a, b) * pathFactor) / metersPerMinute));
}

export function walkMiles(a: Point, b: Point): number {
  return Math.round(((meters(a, b) * pathFactor) / metersPerMile) * 10) / 10;
}

function meters(a: Point, b: Point): number {
  const radians = (degrees: number) => (degrees * Math.PI) / 180;
  const dLat = radians(b.lat - a.lat);
  const dLng = radians(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(radians(a.lat)) * Math.cos(radians(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6_371_000 * Math.asin(Math.sqrt(h));
}

export async function buildingMap(env: Env): Promise<Map<string, Building>> {
  const { results } = await env.DB.prepare("SELECT id, name, lat, lng FROM buildings").all<Building>();
  return new Map(results.map((building) => [building.id, building]));
}

export async function restaurantMap(env: Env): Promise<Map<string, RestaurantRow>> {
  const { results } = await env.DB.prepare("SELECT * FROM restaurants ORDER BY sort").all<RestaurantRow>();
  return new Map(results.map((restaurant) => [restaurant.id, restaurant]));
}

/** Minutes after midnight in the campus time zone. */
export function campusMinute(env: Env, date = new Date()): number {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: env.CAMPUS_TIME_ZONE,
    hour: "numeric",
    minute: "numeric",
    hourCycle: "h23",
  }).formatToParts(date);
  const value = (type: string) => Number(parts.find((part) => part.type === type)?.value ?? 0);
  return value("hour") * 60 + value("minute");
}

export function isOpen(env: Env, restaurant: RestaurantRow): boolean {
  const minute = campusMinute(env);
  return minute >= restaurant.opens_min && minute < (restaurant.closes_min ?? 24 * 60);
}

export function restaurantView(env: Env, restaurant: RestaurantRow, buildings: Map<string, Building>, to?: Building) {
  const building = buildings.get(restaurant.building_id);
  const walk = building && to ? walkMinutes(building, to) : null;
  return {
    id: restaurant.id,
    name: restaurant.name,
    transactId: restaurant.transact_id,
    building: building ? { id: building.id, name: building.name } : null,
    opensMinute: restaurant.opens_min,
    closesMinute: restaurant.closes_min,
    isOpen: isOpen(env, restaurant),
    distanceMiles: building && to ? walkMiles(building, to) : null,
    walkMinutes: walk,
  };
}

export function destinationView(user: User, buildings: Map<string, Building>) {
  const building = user.dropoff_building_id ? buildings.get(user.dropoff_building_id) : undefined;
  if (!building) return null;
  return { buildingId: building.id, building: building.name, room: user.dropoff_room, note: user.dropoff_note };
}

/** GET /buildings */
export async function listBuildings(c: Context): Promise<Response> {
  await requireUser(c);
  const buildings = [...(await buildingMap(c.env)).values()].sort((a, b) => a.name.localeCompare(b.name));
  return json({ buildings });
}

/** GET /home — restaurants, saved drop-off, and runners heading there. */
export async function home(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const buildings = await buildingMap(c.env);
  const restaurants = await restaurantMap(c.env);
  const destination = destinationView(user, buildings);
  const to = destination ? buildings.get(destination.buildingId) : undefined;

  // Available runners seen recently, preferring those with a trip ending at the drop-off building.
  const seenSince = minutesFromNow(-30);
  const heading = to
    ? await c.env.DB.prepare(
        `SELECT COUNT(DISTINCT trips.runner_id) AS count FROM trips
         JOIN users ON users.id = trips.runner_id
         WHERE trips.to_building_id = ?1 AND trips.cancelled_at IS NULL
           AND trips.leave_at BETWEEN ?2 AND ?3 AND users.runner_available = 1 AND users.id != ?4`,
      )
        .bind(to.id, minutesFromNow(-30), minutesFromNow(90), user.id)
        .first<{ count: number }>()
    : null;
  const available = await c.env.DB.prepare(
    "SELECT COUNT(*) AS count FROM users WHERE runner_available = 1 AND runner_seen_at > ?1 AND id != ?2",
  )
    .bind(seenSince, user.id)
    .first<{ count: number }>();

  return json({
    destination,
    runnersHeadingYourWay: heading?.count || available?.count || 0,
    restaurants: [...restaurants.values()].map((restaurant) => restaurantView(c.env, restaurant, buildings, to)),
  });
}

/** PUT /me/destination `{ buildingId, room, note }` */
export async function setDestination(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const body = await readJSON(c.request);
  const buildingID = text(body.buildingId, 64);
  const buildings = await buildingMap(c.env);
  if (!buildings.has(buildingID)) throw new HttpError(400, "Pick a building on campus.");
  const room = text(body.room, 60);
  const note = text(body.note, 200);
  await c.env.DB.prepare(
    "UPDATE users SET dropoff_building_id = ?2, dropoff_room = ?3, dropoff_note = ?4 WHERE id = ?1",
  )
    .bind(user.id, buildingID, room, note)
    .run();
  return json({ destination: { buildingId: buildingID, building: buildings.get(buildingID)!.name, room, note } });
}
