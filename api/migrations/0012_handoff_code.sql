-- Handoff code: a 4-digit code the customer reads to the runner at the door. The runner enters it to
-- confirm drop-off, so a delivery (and the card capture) can't be marked done without meeting the customer.
-- Set when a runner accepts the order. Orders accepted before this migration have none and skip the check.
ALTER TABLE orders ADD COLUMN handoff_code TEXT;
-- Wrong entries since the last lockout; 5 in a row locks entry until handoff_locked_until.
ALTER TABLE orders ADD COLUMN handoff_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN handoff_locked_until TEXT;
