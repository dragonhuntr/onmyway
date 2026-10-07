# On My Way

Campus food delivery by students who are already walking your way. Order and pay on Transact as usual, then hand the order number to a runner heading to your building for a $1 fee.

SwiftUI iOS app built from the [On My Way Figma file](https://www.figma.com/design/dF8DvdlVrzKBv9NKuzNReT/On-My-Way).

## Screens

| # | Screen | File |
|---|--------|------|
| 01A | Login | `Screens/Auth/AuthView.swift` |
| 01B | Register | `Screens/Auth/AuthView.swift` |
| 01 | Home | `Screens/Order/HomeView.swift` |
| 02 | Order web view (Transact, `WKWebView`) | `Screens/Order/OrderFlow.swift` |
| 03 | Enter order number | `Screens/Order/OrderFlow.swift` |
| 04 | Finding runner | `Screens/Order/OrderFlow.swift` |
| 05 | Order tracking | `Screens/Order/OrdersTab.swift` |
| 06 | Delivered | `Screens/Order/OrdersTab.swift` |
| 07 | Runner home | `Screens/Run/RunnerViews.swift` |
| 08 | Delivery request | `Screens/Run/RunnerViews.swift` |
| 09 | Active delivery | `Screens/Run/RunnerViews.swift` |
| 10 | Earnings | `Screens/Earnings/EarningsView.swift` |

## Flows

- **Account:** Login (01A) or Register (01B) with a `@psu.edu` email, through the Worker, which reads and writes D1.
- **Customer:** Home → tap a restaurant → Transact web ordering → *Enter order number* → *Send to runner* → Stripe PaymentSheet (delivery fee + tip, held until drop-off) → matching → tracked in the **Orders** tab, with in-app chat → Delivered (rate + extra tip, or order again).
- **Runner:** **Run** tab → add the walk you're taking → orders along it, sorted by detour → request details → *Accept delivery* → confirm pickup → confirm drop-off (location shared while delivering) → shows up in **Earnings** → *Cash out* through Stripe Connect.

The app polls the Worker: the active order every 3 seconds, the runner feed every 5, chat every 3.

## Backend

The app talks only to the Worker in `api/src/index.ts`, deployed at `https://onmyway-api.onmyway-wyl.workers.dev`. The Worker reads and writes the Cloudflare D1 database `onmyway`. Tables are created by the migrations in `api/migrations/`.

- **Register** (`POST /register`) stores the account in `users`. The password is a PBKDF2 hash, not the password itself. A random token is stored in `sessions` and returned to the app.
- **Login** (`POST /login`) looks up the username, checks the password, stores a new `sessions` token, and returns it.
- The app saves that token. On the next launch it sends the token to `GET /me` and restores the account. Tokens last 30 days; `POST /logout` ends one.

An order moves `awaiting_payment → matching → accepted → picked_up → delivered`, or ends `cancelled` / `expired`.

- **Paying:** `POST /orders` creates a Stripe PaymentIntent for fee + tip with manual capture and returns PaymentSheet details. After the sheet, `POST /orders/:id/payment` checks Stripe and opens the order to runners. The `payment_intent.*` webhook does the same if the app is closed.
- **Matching:** runners pull `GET /runner/requests`, scored by detour against their trip. `POST /orders/:id/accept` only succeeds for the first runner.
- **Delivering:** `pickup` and `deliver` move the order on. Drop-off captures the payment and credits the runner fee + tip in `ledger`. Extra tips from the Delivered screen are separate charges.
- **Earnings:** `GET /earnings` and `GET /deliveries` add up `ledger`. `POST /payouts` transfers the balance to the runner's Stripe Connect account and sends them to onboarding first.
- **Cron:** every minute, orders nobody accepted within 20 minutes expire, and the card hold is released.

### Running it locally

```sh
cd api
npm install
echo PAYMENTS=off > .dev.vars          # skip Stripe; orders go straight to matching
npx wrangler d1 migrations apply onmyway --local
npx wrangler dev --local --port 8799 --test-scheduled
node test/flow.mjs http://localhost:8799   # end-to-end flow test
```

To point the app at it, add the environment variable `API_URL=http://localhost:8799` to the Xcode scheme.

### Deploying

Stripe runs in test mode. Set the secrets, apply migrations, then deploy:

```sh
npx wrangler secret put STRIPE_SECRET_KEY        # sk_test_…
npx wrangler secret put STRIPE_PUBLISHABLE_KEY   # pk_test_…
npx wrangler secret put STRIPE_WEBHOOK_SECRET    # whsec_… from the webhook below
npx wrangler d1 migrations apply onmyway --remote
npm run deploy
```

In the Stripe dashboard, add a webhook to `<worker>/stripe/webhook` for `payment_intent.amount_capturable_updated`, `payment_intent.succeeded`, `payment_intent.payment_failed`, and `account.updated`. Enable Connect (Express accounts) for runner payouts. Transfers need available platform balance; in test mode, pay with card `4000 0000 0000 0077` to make funds available right away.

Building coordinates in `api/migrations/0003_behrend_places.sql` are approximate. Correct them before trusting detour times.

## Requirements

- Xcode 26+
- iOS 18.0+

## Run

```sh
open OnMyWay.xcodeproj
```

The Xcode project is generated from `project.yml` with [XcodeGen](https://github.com/yonaskolb/XcodeGen). After adding or removing files, run `xcodegen generate`.

## Structure

```
OnMyWay/
  App/           App entry and root TabView
  Model/         API client, AppModel (@Observable), Stripe PaymentSheet, location reporting
  Theme/         Color tokens, button styles, cards, chips
  Components/    Illustrated campus map
  Screens/       Auth, Order, Run, Earnings, Shared (chat, destination + trip pickers)
  Assets.xcassets  Icons exported from Figma (vector SVG), AccentColor
api/
  src/           Worker: routes in index.ts; auth, places, orders, runner, chat, ratings, money, stripe, webhooks, cron
  migrations/    D1 schema and Behrend buildings + dining
  test/flow.mjs  End-to-end flow test against a running Worker
```
