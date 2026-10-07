-- Everything after login: places, trips, orders, chat,
-- ratings, the money ledger, and payouts.
-- Money is integer cents. Timestamps are ISO 8601 UTC strings.

ALTER TABLE users ADD COLUMN stripe_customer_id TEXT;
ALTER TABLE users ADD COLUMN stripe_account_id TEXT;
ALTER TABLE users ADD COLUMN payouts_enabled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN blurb TEXT NOT NULL DEFAULT '';
-- Default drop-off for new orders.
ALTER TABLE users ADD COLUMN dropoff_building_id TEXT;
ALTER TABLE users ADD COLUMN dropoff_room TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN dropoff_note TEXT NOT NULL DEFAULT '';
-- Runner presence.
ALTER TABLE users ADD COLUMN runner_available INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN runner_lat REAL;
ALTER TABLE users ADD COLUMN runner_lng REAL;
ALTER TABLE users ADD COLUMN runner_seen_at TEXT;

ALTER TABLE sessions ADD COLUMN expires_at TEXT;
UPDATE sessions SET expires_at = strftime('%Y-%m-%dT%H:%M:%fZ', created_at, '+30 days');

CREATE TABLE buildings (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  lat REAL NOT NULL,
  lng REAL NOT NULL
);

CREATE TABLE restaurants (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  transact_id INTEGER NOT NULL,
  building_id TEXT NOT NULL REFERENCES buildings(id),
  -- Minutes after midnight, campus time. closes_min NULL = closing time unknown.
  opens_min INTEGER NOT NULL,
  closes_min INTEGER,
  sort INTEGER NOT NULL DEFAULT 0
);

-- A walk a runner is already taking.
CREATE TABLE trips (
  id TEXT PRIMARY KEY,
  runner_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  from_building_id TEXT NOT NULL REFERENCES buildings(id),
  to_building_id TEXT NOT NULL REFERENCES buildings(id),
  leave_at TEXT NOT NULL,
  note TEXT NOT NULL DEFAULT '',
  created_at TEXT NOT NULL,
  cancelled_at TEXT
);
CREATE INDEX trips_runner ON trips(runner_id, leave_at);
CREATE INDEX trips_destination ON trips(to_building_id, leave_at);

CREATE TABLE orders (
  id TEXT PRIMARY KEY,
  customer_id TEXT NOT NULL REFERENCES users(id),
  restaurant_id TEXT NOT NULL REFERENCES restaurants(id),
  order_number TEXT NOT NULL,
  name_on_order TEXT NOT NULL,
  ready_at TEXT,
  dropoff_building_id TEXT NOT NULL REFERENCES buildings(id),
  dropoff_room TEXT NOT NULL DEFAULT '',
  dropoff_note TEXT NOT NULL DEFAULT '',
  delivery_fee_cents INTEGER NOT NULL,
  tip_cents INTEGER NOT NULL DEFAULT 0,
  status TEXT NOT NULL CHECK (status IN
    ('awaiting_payment', 'matching', 'accepted', 'picked_up', 'delivered', 'cancelled', 'expired')),
  runner_id TEXT REFERENCES users(id),
  trip_id TEXT REFERENCES trips(id),
  payment_intent_id TEXT,
  created_at TEXT NOT NULL,
  matching_at TEXT,
  accepted_at TEXT,
  picked_up_at TEXT,
  delivered_at TEXT,
  cancelled_at TEXT,
  cancel_reason TEXT
);
CREATE INDEX orders_status ON orders(status, created_at);
CREATE INDEX orders_customer ON orders(customer_id, created_at);
CREATE INDEX orders_runner ON orders(runner_id, delivered_at);
CREATE UNIQUE INDEX orders_payment_intent ON orders(payment_intent_id);

CREATE TABLE messages (
  id TEXT PRIMARY KEY,
  order_id TEXT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  sender_id TEXT NOT NULL REFERENCES users(id),
  body TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE INDEX messages_order ON messages(order_id, created_at);

-- Customer's rating of the runner, one per order.
CREATE TABLE ratings (
  order_id TEXT PRIMARY KEY REFERENCES orders(id),
  customer_id TEXT NOT NULL REFERENCES users(id),
  runner_id TEXT NOT NULL REFERENCES users(id),
  stars INTEGER NOT NULL CHECK (stars BETWEEN 1 AND 5),
  tags TEXT NOT NULL DEFAULT '[]',
  created_at TEXT NOT NULL
);
CREATE INDEX ratings_runner ON ratings(runner_id);

-- Extra tips added on the Delivered screen, each its own charge.
CREATE TABLE tips (
  id TEXT PRIMARY KEY,
  order_id TEXT NOT NULL REFERENCES orders(id),
  customer_id TEXT NOT NULL REFERENCES users(id),
  runner_id TEXT NOT NULL REFERENCES users(id),
  amount_cents INTEGER NOT NULL,
  payment_intent_id TEXT UNIQUE,
  status TEXT NOT NULL CHECK (status IN ('pending', 'paid', 'failed')),
  created_at TEXT NOT NULL
);
CREATE INDEX tips_order ON tips(order_id);

-- Runner money. Balance and earnings are sums over this table.
CREATE TABLE ledger (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  order_id TEXT REFERENCES orders(id),
  tip_id TEXT REFERENCES tips(id),
  payout_id TEXT,
  kind TEXT NOT NULL CHECK (kind IN ('delivery', 'tip', 'payout', 'payout_reversal')),
  amount_cents INTEGER NOT NULL,
  created_at TEXT NOT NULL
);
CREATE INDEX ledger_user ON ledger(user_id, created_at);
CREATE UNIQUE INDEX ledger_delivery_once ON ledger(order_id, kind) WHERE kind = 'delivery';
CREATE UNIQUE INDEX ledger_tip_once ON ledger(tip_id) WHERE tip_id IS NOT NULL;

CREATE TABLE payouts (
  id TEXT PRIMARY KEY,
  runner_id TEXT NOT NULL REFERENCES users(id),
  amount_cents INTEGER NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('pending', 'paid', 'failed')),
  stripe_transfer_id TEXT,
  failure TEXT,
  created_at TEXT NOT NULL
);
CREATE INDEX payouts_runner ON payouts(runner_id, created_at);

CREATE TABLE reports (
  id TEXT PRIMARY KEY,
  order_id TEXT NOT NULL REFERENCES orders(id),
  reporter_id TEXT NOT NULL REFERENCES users(id),
  reason TEXT NOT NULL,
  details TEXT NOT NULL DEFAULT '',
  created_at TEXT NOT NULL
);

-- Stripe webhook events already handled, so retries are no-ops.
CREATE TABLE stripe_events (
  id TEXT PRIMARY KEY,
  type TEXT NOT NULL,
  created_at TEXT NOT NULL
);
