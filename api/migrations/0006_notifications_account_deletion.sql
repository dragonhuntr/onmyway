-- What the bell on Home lists: every order update pushed to a user, kept even when
-- the phone never got the push. Chat messages aren't kept; they live in the chat.
CREATE TABLE notifications (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('order', 'delivery')),
  order_id TEXT REFERENCES orders(id),
  created_at TEXT NOT NULL,
  read_at TEXT
);
CREATE INDEX notifications_user ON notifications(user_id, created_at);

-- Deleted accounts keep their row so the other side's order history and the money
-- ledger still add up, but the name, email and password are wiped.
ALTER TABLE users ADD COLUMN deleted_at TEXT;
