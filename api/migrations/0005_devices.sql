-- Push tokens, one per app install. Tied to the session that registered it, so
-- signing out (or the session expiring) stops pushes to that phone.
CREATE TABLE devices (
  token TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  session_token TEXT NOT NULL REFERENCES sessions(token) ON DELETE CASCADE,
  platform TEXT NOT NULL CHECK (platform IN ('ios', 'android')),
  updated_at TEXT NOT NULL
);
CREATE INDEX devices_user ON devices(user_id);
CREATE INDEX devices_session ON devices(session_token);
