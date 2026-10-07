// End-to-end walk through the delivery flow against a running Worker with PAYMENTS=off.
//   npx wrangler d1 migrations apply onmyway --local
//   npx wrangler dev --local --port 8799 --test-scheduled
//   node test/flow.mjs http://localhost:8799
import assert from "node:assert/strict";

const base = process.argv[2] ?? "http://localhost:8799";
const run = Date.now().toString(36);

async function call(method, path, { token, body, expect = 200 } = {}) {
  const response = await fetch(base + path, {
    method,
    headers: {
      ...(body ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await response.json();
  assert.equal(response.status, expect, `${method} ${path} → ${response.status} ${JSON.stringify(data)}`);
  return data;
}

async function person(first, last) {
  const username = `${first.toLowerCase()}-${run}`;
  return call("POST", "/register", {
    body: { email: `${username}@psu.edu`, username, firstName: first, lastName: last, password: "password123" },
  });
}

// Accounts
await call("POST", "/register", {
  body: { email: `x-${run}@gmail.com`, username: `x-${run}`, firstName: "X", lastName: "Y", password: "password123" },
  expect: 400,
});
const evan = await person("Evan", "Brooks");
const wiya = await person("WiYa", "Lin");
assert.equal((await call("GET", "/me", { token: evan.token })).username, evan.username);
const relogin = await call("POST", "/login", { body: { username: wiya.username, password: "password123" } });
assert.ok(relogin.token);

// Places
const { buildings } = await call("GET", "/buildings", { token: evan.token });
assert.ok(buildings.length >= 5);
await call("POST", "/orders", { token: evan.token, body: { restaurantId: "brunos", orderNumber: "4821" }, expect: 400 });
await call("PUT", "/me/destination", {
  token: evan.token,
  body: { buildingId: "burke", room: "Room 174", note: "Text me when you arrive." },
});
const homeData = await call("GET", "/home", { token: evan.token });
assert.equal(homeData.destination.building, "Burke Center");
assert.equal(homeData.restaurants.length, 3);

// Runner sets up a trip and goes available
await call("PUT", "/runner/status", { token: wiya.token, body: { available: true } });
const { trip } = await call("POST", "/trips", {
  token: wiya.token,
  body: { fromBuildingId: "reed", toBuildingId: "burke", leaveAt: new Date().toISOString(), note: "a 10:00 lecture" },
  expect: 201,
});
assert.equal(trip.to.name, "Burke Center");
assert.equal((await call("GET", "/home", { token: evan.token })).runnersHeadingYourWay >= 1, true);

// Customer orders
const created = await call("POST", "/orders", {
  token: evan.token,
  body: { restaurantId: "brunos", orderNumber: "4821", tipCents: 100 },
  expect: 201,
});
assert.equal(created.payment, null);
const orderID = created.order.id;
assert.equal(created.order.status, "matching");
assert.equal(created.order.nameOnOrder, "Evan B.");
await call("POST", "/orders", { token: evan.token, body: { restaurantId: "paws", orderNumber: "1" }, expect: 409 });
assert.equal((await call("GET", "/orders/active", { token: evan.token })).order.id, orderID);

// Runner sees and accepts it
const feed = await call("GET", "/runner/requests", { token: wiya.token });
const request = feed.requests.find((item) => item.id === orderID);
assert.ok(request, "order shows on the runner's route");
assert.equal(request.payoutCents, 200);
assert.equal(request.customer.name, "Evan B.");
assert.equal((await call("GET", "/runner/requests", { token: evan.token })).requests.some((r) => r.id === orderID), false);
await call("GET", `/orders/${orderID}`, { token: wiya.token, expect: 404 });

const accepted = await call("POST", `/orders/${orderID}/accept`, { token: wiya.token, body: {} });
assert.equal(accepted.order.status, "accepted");
assert.equal(accepted.order.role, "runner");
await call("POST", `/orders/${orderID}/accept`, { token: evan.token, body: {}, expect: 409 });
await call("POST", `/orders/${orderID}/cancel`, { token: evan.token, expect: 409 });

const tracked = (await call("GET", `/orders/${orderID}`, { token: evan.token })).order;
assert.equal(tracked.runner.name, "WiYa L.");
assert.match(tracked.runner.routeNote, /heading to Burke Center/);
assert.ok(tracked.etaMinutes > 0);

// Chat
await call("POST", `/orders/${orderID}/messages`, { token: evan.token, body: { body: "I'm in 174" }, expect: 201 });
await call("POST", `/orders/${orderID}/messages`, { token: wiya.token, body: { body: "On my way!" }, expect: 201 });
const chat = await call("GET", `/orders/${orderID}/messages`, { token: evan.token });
assert.deepEqual(chat.messages.map((m) => [m.body, m.fromMe]), [["I'm in 174", true], ["On my way!", false]]);
const later = await call("GET", `/orders/${orderID}/messages?after=${encodeURIComponent(chat.messages[0].createdAt)}`, {
  token: wiya.token,
});
assert.equal(later.messages.length, 1);

// Pickup and drop-off
await call("POST", "/runner/location", { token: wiya.token, body: { lat: 42.1195, lng: -79.9834 } });
await call("POST", `/orders/${orderID}/deliver`, { token: wiya.token, expect: 409 });
assert.equal((await call("POST", `/orders/${orderID}/pickup`, { token: wiya.token })).order.status, "picked_up");
assert.ok((await call("GET", `/orders/${orderID}`, { token: evan.token })).order.runner.location);
assert.equal((await call("POST", `/orders/${orderID}/deliver`, { token: wiya.token })).order.status, "delivered");
assert.equal((await call("GET", "/orders/active", { token: evan.token })).order, null);

// Rating with an extra tip
await call("POST", `/orders/${orderID}/rating`, { token: wiya.token, body: { stars: 5 }, expect: 403 });
const rated = await call("POST", `/orders/${orderID}/rating`, {
  token: evan.token,
  body: { stars: 5, tags: ["On time", "Friendly", "bogus"], extraTipCents: 200 },
  expect: 201,
});
assert.equal(rated.tip.status, "paid");
await call("POST", `/orders/${orderID}/rating`, { token: evan.token, body: { stars: 4 }, expect: 409 });

// Earnings and cash out
const week = await call("GET", "/earnings", { token: wiya.token });
assert.equal(week.totalCents, 400);
assert.equal(week.deliveries, 1);
assert.equal(week.balanceCents, 400);
assert.equal(week.days.length, 7);
const history = await call("GET", "/deliveries", { token: wiya.token });
assert.deepEqual(
  [history.deliveries[0].amountCents, history.deliveries[0].stars, history.deliveries[0].tipCents],
  [400, 5, 300],
);
const runnerAfter = (await call("GET", `/orders/${orderID}`, { token: evan.token })).order.runner;
assert.deepEqual([runnerAfter.rating, runnerAfter.deliveries], [5, 1]);

const payout = await call("POST", "/payouts", { token: wiya.token, expect: 201 });
assert.equal(payout.payout.amountCents, 400);
assert.equal(payout.balanceCents, 0);
await call("POST", "/payouts", { token: wiya.token, expect: 409 });

// Cancel before a runner, and release after accepting
const second = await call("POST", "/orders", {
  token: evan.token,
  body: { restaurantId: "paws", orderNumber: "5000", tipCents: 0 },
  expect: 201,
});
await call("POST", `/orders/${second.order.id}/accept`, { token: wiya.token, body: {} });
await call("POST", `/orders/${second.order.id}/release`, { token: wiya.token });
assert.equal((await call("GET", `/orders/${second.order.id}`, { token: evan.token })).order.status, "matching");
assert.equal((await call("POST", `/orders/${second.order.id}/cancel`, { token: evan.token })).order.status, "cancelled");

await call("POST", "/logout", { token: evan.token });
await call("GET", "/me", { token: evan.token, expect: 401 });

console.log("✓ delivery flow passed");
