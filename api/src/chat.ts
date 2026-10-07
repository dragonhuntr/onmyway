import type { Context } from "./http.ts";
import { HttpError, id, isoDate, json, now, readJSON, text } from "./http.ts";
import { requireUser } from "./auth.ts";
import { participantOrder } from "./orders.ts";

/** Chat opens once a runner accepts and stays readable afterwards. */
const chatStatuses = ["accepted", "picked_up", "delivered"];

/** GET /orders/:id/messages?after=ISO — poll with the last message's `createdAt`. */
export async function listMessages(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const order = await participantOrder(c.env, c.params.id, user);
  const after = isoDate(c.url.searchParams.get("after")) ?? "";
  const { results } = await c.env.DB.prepare(
    "SELECT id, sender_id, body, created_at FROM messages WHERE order_id = ?1 AND created_at > ?2 ORDER BY created_at LIMIT 200",
  )
    .bind(order.id, after)
    .all<{ id: string; sender_id: string; body: string; created_at: string }>();
  return json({
    messages: results.map((message) => ({
      id: message.id,
      body: message.body,
      createdAt: message.created_at,
      fromMe: message.sender_id === user.id,
    })),
  });
}

/** POST /orders/:id/messages `{ body }` */
export async function sendMessage(c: Context): Promise<Response> {
  const user = await requireUser(c);
  const order = await participantOrder(c.env, c.params.id, user);
  if (!order.runner_id || !chatStatuses.includes(order.status)) {
    throw new HttpError(409, "You can message once a runner has your order.", "chat_closed");
  }
  if (order.status === "delivered" && order.delivered_at && Date.parse(order.delivered_at) < Date.now() - 24 * 3600_000) {
    throw new HttpError(409, "This chat has closed.", "chat_closed");
  }
  const body = text((await readJSON(c.request)).body, 1000);
  if (!body) throw new HttpError(400, "Type a message.");
  const message = { id: id(), body, createdAt: now(), fromMe: true };
  await c.env.DB.prepare("INSERT INTO messages (id, order_id, sender_id, body, created_at) VALUES (?1, ?2, ?3, ?4, ?5)")
    .bind(message.id, order.id, user.id, body, message.createdAt)
    .run();
  return json({ message }, 201);
}
