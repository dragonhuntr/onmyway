import type { Env } from "./env.ts";

export type Email = { to: string; subject: string; text: string };

/**
 * Sends `email` through Resend. Until `RESEND_API_KEY` is set, nothing is sent and the
 * message is logged instead, so codes still show in `wrangler dev` and `wrangler tail`.
 * Returns whether it was sent; never throws.
 */
export async function sendEmail(env: Env, email: Email): Promise<boolean> {
  if (!env.RESEND_API_KEY) {
    console.log(`Email to ${email.to} not sent (RESEND_API_KEY unset): ${email.subject}\n${email.text}`);
    return false;
  }
  try {
    const response = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: { authorization: `Bearer ${env.RESEND_API_KEY}`, "content-type": "application/json" },
      body: JSON.stringify({ from: env.EMAIL_FROM, to: [email.to], subject: email.subject, text: email.text }),
    });
    if (response.ok) return true;
    console.warn("Email failed", response.status, await response.text());
  } catch (error) {
    console.warn("Email failed", error);
  }
  return false;
}
