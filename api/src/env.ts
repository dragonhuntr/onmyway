export interface Env {
  DB: D1Database;
  /** Only addresses at this domain can register, e.g. "psu.edu". */
  ALLOWED_EMAIL_DOMAIN: string;
  /** IANA zone for opening hours and the earnings week, e.g. "America/New_York". */
  CAMPUS_TIME_ZONE: string;
  DELIVERY_FEE_CENTS: string;
  /** "stripe" charges through Stripe; "off" skips payment entirely (local development). */
  PAYMENTS: string;
  STRIPE_SECRET_KEY?: string;
  STRIPE_PUBLISHABLE_KEY?: string;
  STRIPE_WEBHOOK_SECRET?: string;
}

export type User = {
  id: string;
  username: string;
  email: string;
  first_name: string;
  last_name: string;
  stripe_customer_id: string | null;
  stripe_account_id: string | null;
  payouts_enabled: number;
  blurb: string;
  dropoff_building_id: string | null;
  dropoff_room: string;
  dropoff_note: string;
  runner_available: number;
  runner_lat: number | null;
  runner_lng: number | null;
  runner_seen_at: string | null;
};

export function paymentsEnabled(env: Env): boolean {
  return env.PAYMENTS === "stripe";
}

export function deliveryFeeCents(env: Env): number {
  return Number(env.DELIVERY_FEE_CENTS) || 100;
}
