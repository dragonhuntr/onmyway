-- Email verification: an account proves it owns its @psu.edu address with a 6-digit code
-- emailed at sign-up. Until then it can sign in, but ordering and running are refused.
-- Existing accounts start unverified too, so every account on the platform has proven its email.
ALTER TABLE users ADD COLUMN email_verified_at TEXT;

-- One pending code per account, same shape and limits as password_resets.
CREATE TABLE email_verifications (
  user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  code_hash TEXT NOT NULL,
  code_salt TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL
);
