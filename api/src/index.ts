import type { Env } from "./env.ts";
import { HttpError, Router, cors, json } from "./http.ts";
import { login, logout, me, register } from "./auth.ts";
import { home, listBuildings, setDestination } from "./places.ts";
import { activeOrder, cancelOrder, confirmPayment, createOrder, getOrder, listOrders, reportOrder } from "./orders.ts";
import {
  acceptOrder,
  confirmDropoff,
  confirmPickup,
  createTrip,
  deleteTrip,
  listRequests,
  listTrips,
  releaseOrder,
  runnerHome,
  setAvailability,
  updateLocation,
  updateTrip,
} from "./runner.ts";
import { listMessages, sendMessage } from "./chat.ts";
import { confirmTip, rateOrder } from "./ratings.ts";
import { cashOut, deliveries, earnings, payoutAccountLink, payoutAccountStatus } from "./money.ts";
import { connectRefresh, connectReturn, stripeWebhook } from "./webhooks.ts";
import { sweep } from "./cron.ts";

const router = new Router()
  // Account
  .on("POST", "/register", register)
  .on("POST", "/login", login)
  .on("POST", "/logout", logout)
  .on("GET", "/me", me)
  .on("PUT", "/me/destination", setDestination)
  // Places
  .on("GET", "/home", home)
  .on("GET", "/buildings", listBuildings)
  // Customer orders
  .on("POST", "/orders", createOrder)
  .on("GET", "/orders", listOrders)
  .on("GET", "/orders/active", activeOrder)
  .on("GET", "/orders/:id", getOrder)
  .on("POST", "/orders/:id/payment", confirmPayment)
  .on("POST", "/orders/:id/cancel", cancelOrder)
  .on("POST", "/orders/:id/rating", rateOrder)
  .on("POST", "/orders/:id/report", reportOrder)
  .on("POST", "/tips/:id/payment", confirmTip)
  // Chat
  .on("GET", "/orders/:id/messages", listMessages)
  .on("POST", "/orders/:id/messages", sendMessage)
  // Runner
  .on("GET", "/runner", runnerHome)
  .on("PUT", "/runner/status", setAvailability)
  .on("POST", "/runner/location", updateLocation)
  .on("GET", "/runner/requests", listRequests)
  .on("POST", "/orders/:id/accept", acceptOrder)
  .on("POST", "/orders/:id/pickup", confirmPickup)
  .on("POST", "/orders/:id/deliver", confirmDropoff)
  .on("POST", "/orders/:id/release", releaseOrder)
  .on("GET", "/trips", listTrips)
  .on("POST", "/trips", createTrip)
  .on("PUT", "/trips/:id", updateTrip)
  .on("DELETE", "/trips/:id", deleteTrip)
  // Earnings and payouts
  .on("GET", "/earnings", earnings)
  .on("GET", "/deliveries", deliveries)
  .on("POST", "/payouts", cashOut)
  .on("GET", "/runner/payout-account", payoutAccountStatus)
  .on("POST", "/runner/payout-account", payoutAccountLink)
  // Stripe
  .on("POST", "/stripe/webhook", stripeWebhook)
  .on("GET", "/stripe/connect/return", connectReturn)
  .on("GET", "/stripe/connect/refresh", connectRefresh);

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    if (request.method === "OPTIONS") {
      return cors(new Response(null, { status: 204 }));
    }

    const url = new URL(request.url);
    const route = router.match(request.method, url.pathname);
    if (!route) return cors(json({ error: "Not found" }, 404));
    try {
      return cors(await route.handler({ request, env, ctx, url, params: route.params }));
    } catch (error) {
      if (error instanceof HttpError) {
        return cors(json({ error: error.message, ...(error.code ? { code: error.code } : {}) }, error.status));
      }
      console.error(error);
      return cors(json({ error: "Something went wrong" }, 500));
    }
  },

  async scheduled(_controller: ScheduledController, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(sweep(env));
  },
} satisfies ExportedHandler<Env>;
