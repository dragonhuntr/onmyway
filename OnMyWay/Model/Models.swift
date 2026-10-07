import SwiftUI

/// Signed-in person. `id` is the D1 user id, and later the Auth0 `sub`.
struct SessionUser: Equatable {
    var id: String
    var username: String
    var email: String
    var firstName: String
    var lastName: String

    /// "Evan B."
    var shortName: String {
        lastName.first.map { "\(firstName) \($0.uppercased())." } ?? firstName
    }
}

/// A campus building, as a reference inside other responses.
struct Place: Decodable, Hashable, Sendable {
    let id: String
    let name: String
}

struct Building: Decodable, Identifiable, Hashable, Sendable {
    let id: String
    let name: String
}

struct Restaurant: Decodable, Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    /// Location ID in Transact web ordering (`weborder.transactcampus.com/237/<id>`).
    let transactId: Int
    let building: Place?
    let opensMinute: Int
    let closesMinute: Int?
    let distanceMiles: Double?
    let walkMinutes: Int?

    var orderingURL: URL { URL(string: "https://weborder.transactcampus.com/237/\(transactId)")! }
    var hours: Hours { Hours(opens: opensMinute, closes: closesMinute) }

    var logo: ImageResource {
        switch id {
        case "clarks": .logoClarks
        case "paws": .logoPaws
        case "brunos": .logoBrunos
        default: .building
        }
    }

    /// Background behind the logo; matches the logo image's own background.
    var tile: Color { id == "brunos" ? Color(hex: 0x0B2C52) : .white }

    var distance: String {
        guard let distanceMiles else { return building?.name ?? "On campus" }
        return distanceMiles < 0.1 ? "Next door" : "\(distanceMiles.formatted()) mi"
    }

    var eta: String {
        walkMinutes.map { "\($0 + 3)–\($0 + 8) min" } ?? "~10 min"
    }

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

struct Destination: Decodable, Hashable, Sendable {
    var buildingId: String
    var building: String
    var room: String
    var note: String

    var short: String {
        room.isEmpty ? building : "\(building), \(room.replacingOccurrences(of: "Room", with: "Rm"))"
    }
    var full: String { room.isEmpty ? building : "\(building) · \(room)" }
}

enum Tip: Int, CaseIterable, Identifiable {
    case none = 0, half = 50, one = 100, two = 200
    var id: Int { rawValue }
    var label: String { self == .none ? "None" : rawValue.usd }
}

/// A student's order: food paid on Transact, delivery handed to a runner.
struct Order: Decodable, Identifiable, Hashable, Sendable {
    enum Status: String, Decodable, Sendable {
        case awaitingPayment = "awaiting_payment"
        case matching
        case accepted
        case pickedUp = "picked_up"
        case delivered
        case cancelled
        case expired

        var isActive: Bool { [.awaitingPayment, .matching, .accepted, .pickedUp].contains(self) }
    }

    struct OrderRestaurant: Decodable, Hashable, Sendable {
        let id: String
        let name: String
        let building: Place
    }

    struct Customer: Decodable, Hashable, Sendable {
        let id: String
        let name: String
        let orders: Int
    }

    struct Rating: Decodable, Hashable, Sendable {
        let stars: Int
        let tags: [String]
    }

    let id: String
    /// "customer" or "runner": which side of the order the signed-in user is on.
    let role: String
    let status: Status
    let number: String
    let nameOnOrder: String
    let readyAt: Date?
    let restaurant: OrderRestaurant
    let destination: Destination
    let walkMinutes: Int
    let deliveryFeeCents: Int
    let tipCents: Int
    let extraTipCents: Int
    let payoutCents: Int
    let etaMinutes: Int?
    let runner: Runner?
    let customer: Customer?
    let rating: Rating?
    let createdAt: Date
    let acceptedAt: Date?
    let pickedUpAt: Date?
    let deliveredAt: Date?
    let cancelledAt: Date?
    let cancelReason: String?

    var dueNowCents: Int { deliveryFeeCents + tipCents }
}

struct Runner: Decodable, Hashable, Sendable {
    struct Location: Decodable, Hashable, Sendable {
        let lat: Double
        let lng: Double
        let updatedAt: Date
    }

    let id: String
    let name: String
    let firstName: String
    let rating: Double?
    let deliveries: Int
    let blurb: String
    let routeNote: String?
    let location: Location?

    /// "4.9 · 38 deliveries · Junior, CS"
    var summary: String {
        [rating.map { $0.formatted() }, deliveries == 0 ? "New runner" : "\(deliveries) deliveries", blurb.isEmpty ? nil : blurb]
            .compactMap(\.self)
            .joined(separator: " · ")
    }
}

/// An order a runner can pick up along their route.
struct DeliveryRequest: Decodable, Identifiable, Hashable, Sendable {
    struct Customer: Decodable, Hashable, Sendable {
        let name: String
        let orders: Int
    }

    let id: String
    let restaurant: Order.OrderRestaurant
    let destination: Destination
    let number: String
    let nameOnOrder: String
    let readyAt: Date?
    let readyNow: Bool
    let dropoffBy: Date
    let detourMinutes: Int
    let detourMiles: Double
    let onRoute: Bool
    let payoutCents: Int
    let customer: Customer

    var readyText: String { readyNow ? "Ready now" : "Ready \(readyAt?.shortTime ?? "soon")" }
}

/// A walk a runner is already taking.
struct Trip: Decodable, Identifiable, Hashable, Sendable {
    let id: String
    let from: Place
    let to: Place
    let leaveAt: Date
    let note: String
    let walkMinutes: Int
}

struct RunnerStatus: Decodable, Sendable {
    let available: Bool
    let trip: Trip?
    let activeDelivery: Order?
    let balanceCents: Int
}

struct CompletedDelivery: Decodable, Identifiable, Hashable, Sendable {
    let id: String
    let restaurant: String
    let building: String
    let customer: String
    let deliveredAt: Date
    let amountCents: Int
    let tipCents: Int
    let stars: Int?

    var route: String { "\(restaurant) → \(building)" }
    var detail: String { "\(deliveredAt.formatted(.relative(presentation: .named))) · \(customer)" }
    var rating: String {
        guard let stars else { return "Awaiting rating" }
        return tipCents > 0 ? "\(stars)★ · \(tipCents.usd) tip" : "\(stars)★"
    }
}

enum EarningsPeriod: String, CaseIterable, Identifiable {
    case thisWeek = "This week", lastWeek = "Last week"
    var id: Self { self }
    var query: String { self == .thisWeek ? "this" : "last" }
}

struct Earnings: Decodable, Sendable {
    struct Day: Decodable, Sendable {
        let date: String
        let cents: Int
    }

    let start: String
    let end: String
    let days: [Day]
    let totalCents: Int
    let changePercent: Int?
    let deliveries: Int
    let walkingMinutes: Int
    let averageCents: Int
    let balanceCents: Int
    let payoutsEnabled: Bool
}

struct DailyEarning: Identifiable {
    let day: String
    let index: Int
    let amount: Double
    var id: Int { index }
}

struct ChatMessage: Decodable, Identifiable, Hashable, Sendable {
    let id: String
    let body: String
    let createdAt: Date
    let fromMe: Bool
}

/// What Stripe's PaymentSheet needs; returned when an order or extra tip has to be paid.
struct PaymentSheetConfig: Decodable, Sendable {
    let paymentIntentClientSecret: String
    let customerId: String
    let customerEphemeralKeySecret: String
    let publishableKey: String
}

extension Int {
    /// Cents as dollars: 150 → "$1.50".
    var usd: String { (Decimal(self) / 100).usd }
}

extension Date {
    /// "9:50 AM"
    var shortTime: String { formatted(date: .omitted, time: .shortened) }
}
