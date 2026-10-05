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

- **Account:** Login (01A) or Register (01B) through the Worker, which reads and writes D1.
- **Customer:** Home → tap a restaurant → Transact web ordering → *Enter order number* → *Send to runner* → matching → tracked in the **Orders** tab → Delivered (rate + tip, or order again).
- **Runner:** **Run** tab → *Accept* an order along your route → request details → *Accept delivery* → confirm pickup → confirm drop-off → shows up in **Earnings**.

Runner matching and delivery progress are simulated with timers and sample data (`Model/AppModel.swift`) until a backend exists.

## Accounts

The app talks only to the Worker in `api/src/index.ts`, deployed at `https://onmyway-api.onmyway-wyl.workers.dev`. The Worker reads and writes the Cloudflare D1 database `onmyway`. Tables are in `api/schema.sql`: `users` and `sessions`.

- **Register** (`POST /register`) stores the account in `users`. The password is a PBKDF2 hash, not the password itself. A random token is stored in `sessions` and returned to the app.
- **Login** (`POST /login`) looks up the username, checks the password, stores a new `sessions` token, and returns it.
- The app saves that token. On the next launch it sends the token to `GET /me` and restores the account.

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
  Model/         AccountAPI, AppModel (@Observable), sample data
  Theme/         Color tokens, button styles, cards, chips
  Components/    Illustrated campus map
  Screens/       Auth, Order, Run, Earnings
  Assets.xcassets  Icons exported from Figma (vector SVG), AccentColor
api/
  src/index.ts   Worker: /register, /login, /me
  schema.sql     D1 tables: users, sessions
```
