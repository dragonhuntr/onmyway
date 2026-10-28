-- Forgot password: one pending 6-digit code per account, emailed to the user. Only a
-- PBKDF2 hash of the code is kept. A code lasts 15 minutes and allows 5 wrong tries.
CREATE TABLE password_resets (
  user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  code_hash TEXT NOT NULL,
  code_salt TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL
);
