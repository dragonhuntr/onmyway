import SwiftUI

enum AppTab: Hashable { case home, orders, run, earnings }

@MainActor
@Observable
final class AppModel {
    var selectedTab: AppTab = .home

    // MARK: Customer side
    var destination = Destination(building: "Burke Center", room: "Room 174", note: "Text me when you arrive.")
    var runnersHeadingYourWay = 12
    var restaurants = SampleData.restaurants
    /// Restaurant whose Transact ordering flow is currently presented.
    var orderingFrom: Restaurant?
    var activeOrder: Order?
    /// Set when the active order is delivered, to present the confirmation screen.
    var deliveredOrder: Order?

    // MARK: Runner side
    var isAvailable = true
    var requests = SampleData.requests
    var recentDeliveries = SampleData.recentDeliveries
    var cashOutBalance: Decimal = 42.60

    // MARK: Customer actions

    func sendToRunner(_ order: Order) {
        activeOrder = order
    }

    /// Called once the matching screen finds a runner: close the ordering flow and track in Orders.
    func runnerMatched() {
        guard var order = activeOrder else { return }
        order.runner = SampleData.wiYa
        order.status = .onTheWay
        activeOrder = order
        orderingFrom = nil
        selectedTab = .orders
    }

    func cancelActiveOrder() {
        activeOrder = nil
        orderingFrom = nil
    }

    func markDelivered() {
        guard var order = activeOrder else { return }
        order.status = .delivered
        activeOrder = nil
        deliveredOrder = order
    }

    func finishDelivered(orderAgain: Bool) {
        let restaurant = deliveredOrder?.restaurant
        deliveredOrder = nil
        if orderAgain, let restaurant {
            selectedTab = .home
            // Let the Delivered cover finish dismissing before presenting the ordering flow.
            Task {
                try? await Task.sleep(for: .milliseconds(600))
                orderingFrom = restaurant
            }
        }
    }

    // MARK: Runner actions

    func complete(_ request: DeliveryRequest) {
        requests.removeAll { $0.id == request.id }
        recentDeliveries.insert(
            CompletedDelivery(
                route: "\(request.pickup) → \(request.dropoff.components(separatedBy: " · ").first ?? request.dropoff)",
                detail: "Just now · \(request.customer)",
                amount: request.payout,
                rating: "Awaiting rating"
            ),
            at: 0
        )
        cashOutBalance += request.payout
    }

    func cashOut() {
        cashOutBalance = 0
    }

    func earnings(for period: EarningsPeriod) -> [DailyEarning] {
        let amounts: [Double] = switch period {
        case .thisWeek: [5.00, 7.65, 4.12, 9.42, 11.77, 2.94, 1.77]
        case .lastWeek: [3.20, 6.10, 5.40, 7.35, 8.90, 3.46, 1.75]
        }
        return zip(["M", "T", "W", "T", "F", "S", "S"], amounts).enumerated().map {
            DailyEarning(day: $0.element.0, index: $0.offset, amount: $0.element.1)
        }
    }
}

enum SampleData {
    // Penn State Behrend dining on Transact; hours from the Transact location list.
    static let restaurants: [Restaurant] = [
        Restaurant(id: "clarks", name: "Clark’s Cafe", transactID: 2367, symbol: "carrot", tile: Color(hex: 0xDCEBD3),
                   distance: "0.2 mi", eta: "8–12 min", rating: 4.8,
                   hours: .init(opens: 7 * 60 + 30, closes: 15 * 60)),
        Restaurant(id: "paws", name: "Paws", transactID: 2368, symbol: "pawprint", tile: Color(hex: 0xF3D9D2),
                   distance: "0.3 mi", eta: "10–15 min", rating: 4.7,
                   hours: .init(opens: 7 * 60 + 30, closes: 18 * 60)),
        Restaurant(id: "brunos", name: "Bruno’s", transactID: 2366, symbol: "cup.and.saucer", tile: Color(hex: 0xF6E3C4),
                   distance: "0.4 mi", eta: "5–8 min", rating: 4.6,
                   hours: .init(opens: 10 * 60 + 30, closes: nil)),
    ]

    static let wiYa = Runner(
        name: "Wi Ya", rating: 4.9, deliveries: 38, blurb: "Junior, CS",
        routeNote: "Wi Ya was already heading to Burke Center for a 10:00 lecture.",
        phone: "5555550123"
    )

    static let requests: [DeliveryRequest] = [
        DeliveryRequest(id: "r1", pickup: "Bruno’s", pickupDetail: "Pick up · Ready 9:50",
                        dropoff: "Burke Center · Room 204", dropoffDetail: "On your way",
                        detourMinutes: 2, detourMiles: 0.1, orderNumber: "4821", customer: "Evan B.",
                        customerRating: 4.9, customerOrders: 12, note: "Text me when you’re close, thanks!",
                        readyAt: "9:50", dropoffBy: "10:00", payout: 1.80),
        DeliveryRequest(id: "r2", pickup: "Clark’s Cafe", pickupDetail: "Pick up · Ready now",
                        dropoff: "Burke Center · Room 110", dropoffDetail: "On your way",
                        detourMinutes: 3, detourMiles: 0.2, orderNumber: "4830", customer: "Priya S.",
                        customerRating: 5.0, customerOrders: 7, note: "Leave it on the desk by the door.",
                        readyAt: "9:45", dropoffBy: "10:05", payout: 1.50),
        DeliveryRequest(id: "r3", pickup: "Hillside Dining Hall", pickupDetail: "Pick up · Ready 9:55",
                        dropoff: "Chem Building · Lab 3", dropoffDetail: "2 min past your stop",
                        detourMinutes: 6, detourMiles: 0.3, orderNumber: "4836", customer: "Marco L.",
                        customerRating: 4.7, customerOrders: 21, note: "I’m in the back row.",
                        readyAt: "9:55", dropoffBy: "10:15", payout: 2.10),
    ]

    static let recentDeliveries: [CompletedDelivery] = [
        CompletedDelivery(route: "Bruno’s → Burke Center", detail: "Today 9:52 AM · Evan B.", amount: 1.80, rating: "5★ · $1.00 tip"),
        CompletedDelivery(route: "Paws → Trippe Hall", detail: "Today 8:31 AM · Dev K.", amount: 0.80, rating: "Awaiting rating"),
        CompletedDelivery(route: "Clark’s Cafe → Nick", detail: "Yesterday 12:10 PM · Sam O.", amount: 2.30, rating: "5★ · $1.50 tip"),
    ]
}
