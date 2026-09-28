# On My Way

Campus food delivery by students who are already walking your way. Order and pay on Transact as usual, then hand the order number to a runner heading to your building for a $1 fee.

SwiftUI iOS app built from the [On My Way Figma file](https://www.figma.com/design/dF8DvdlVrzKBv9NKuzNReT/On-My-Way).

## Screens

| # | Screen | File |
|---|--------|------|
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

- **Customer:** Home → tap a restaurant → Transact web ordering → *Enter order number* → *Send to runner* → matching → tracked in the **Orders** tab → Delivered (rate + tip, or order again).
- **Runner:** **Run** tab → *Accept* an order along your route → request details → *Accept delivery* → confirm pickup → confirm drop-off → shows up in **Earnings**.

Runner matching and delivery progress are simulated with timers and sample data (`Model/AppModel.swift`) until a backend exists.

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
  Model/         Data types, AppModel (@Observable), sample data
  Theme/         Color tokens, button styles, cards, chips
  Components/    Illustrated campus map
  Screens/       Order, Run, Earnings
  Assets.xcassets  Icons exported from Figma (vector SVG), AccentColor
```
