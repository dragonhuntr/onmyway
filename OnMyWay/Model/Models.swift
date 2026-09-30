import SwiftUI

struct Restaurant: Identifiable, Hashable {
    let id: String
    let name: String
    /// Location ID in Transact web ordering (`weborder.transactcampus.com/237/<id>`).
    let transactID: Int
    let logo: ImageResource
    /// Background behind the logo; matches the logo image's own background.
    let tile: Color
    let distance: String
    let eta: String
    let rating: Double
    let hours: Hours

    var orderingURL: URL { URL(string: "https://weborder.transactcampus.com/237/\(transactID)")! }

    /// Daily opening hours, as minutes after midnight. `closes` is nil when unknown.
    struct Hours: Hashable {
        let opens: Int
        let closes: Int?

        func isOpen(at date: Date = .now) -> Bool {
            let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
            let minute = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
            return minute >= opens && minute < (closes ?? 24 * 60)
        }

        func status(at date: Date = .now) -> String {
            if isOpen(at: date) {
                guard let closes else { return "Open now" }
                return "Open · \(Self.format(opens)) – \(Self.format(closes))"
            }
            let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
            let minute = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
            return minute < opens ? "Opens at \(Self.format(opens))" : "Closed · Opens \(Self.format(opens))"
        }

        private static func format(_ minutes: Int) -> String {
            let date = Calendar.current.date(bySettingHour: minutes / 60, minute: minutes % 60, second: 0, of: .now)!
            return minutes % 60 == 0
                ? date.formatted(.dateTime.hour())
                : date.formatted(.dateTime.hour().minute())
        }
    }
}

struct Destination: Hashable {
    var building: String
    var room: String
    var note: String

    var short: String { "\(building), \(room.replacingOccurrences(of: "Room", with: "Rm"))" }
    var full: String { "\(building) · \(room)" }
}

enum Tip: Decimal, CaseIterable, Identifiable {
    case none = 0, half = 0.5, one = 1, two = 2
    var id: Decimal { rawValue }
    var label: String { self == .none ? "None" : rawValue.usd }
}

struct Runner: Hashable {
    let name: String
    let rating: Double
    let deliveries: Int
    let blurb: String
    let routeNote: String
    let phone: String
}

/// A student's order: food paid on Transact, delivery handed to a runner.
struct Order: Identifiable, Hashable {
    enum Status: Int, Comparable {
        case matching, pickedUp, onTheWay, delivered
        static func < (a: Status, b: Status) -> Bool { a.rawValue < b.rawValue }
    }

    let id = UUID()
    var restaurant: Restaurant
    var number: String
    var nameOnOrder: String
    var readyAround: String
    var destination: Destination
    var tip: Tip
    var deliveryFee: Decimal = 1
    var status: Status = .matching
    var runner: Runner?
    var etaMinutes = 6

    var dueNow: Decimal { deliveryFee + tip.rawValue }
}

/// An order a runner can pick up along their route.
struct DeliveryRequest: Identifiable, Hashable {
    let id: String
    let pickup: String
    let pickupDetail: String
    let dropoff: String
    let dropoffDetail: String
    let detourMinutes: Int
    let detourMiles: Double
    let orderNumber: String
    let customer: String
    let customerRating: Double
    let customerOrders: Int
    let note: String
    let readyAt: String
    let dropoffBy: String
    let payout: Decimal
}

struct CompletedDelivery: Identifiable, Hashable {
    let id = UUID()
    let route: String
    let detail: String
    let amount: Decimal
    let rating: String
}

enum EarningsPeriod: String, CaseIterable, Identifiable {
    case thisWeek = "This week", lastWeek = "Last week"
    var id: Self { self }
}

struct DailyEarning: Identifiable {
    let day: String
    let index: Int
    let amount: Double
    var id: Int { index }
}
