import { bindings, defineConfig, triggers } from "cf/config";
import * as entrypoint from "./src/index.ts" with { type: "cf-worker" };

export default defineConfig({
  worker: {
    name: "onmyway-api",
    entrypoint,
    compatibilityDate: "2026-10-04",
    env: {
      DB: bindings.d1({
        name: "onmyway",
        id: "2e489ff3-7c39-4481-a04e-72533217a363",
      }),
      ALLOWED_EMAIL_DOMAIN: bindings.text("psu.edu"),
      CAMPUS_TIME_ZONE: bindings.text("America/New_York"),
      DELIVERY_FEE_CENTS: bindings.text("100"),
      PAYMENTS: bindings.text("stripe"),
      STRIPE_PUBLISHABLE_KEY: bindings.secret(),
      STRIPE_SECRET_KEY: bindings.secret(),
      STRIPE_WEBHOOK_SECRET: bindings.secret(),
    },
    // Expire stale orders and idle runners.
    triggers: [triggers.scheduled({ schedule: "* * * * *" })],
  },
});
