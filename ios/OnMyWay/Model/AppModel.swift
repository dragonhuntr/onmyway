import SwiftUI

enum AppTab: Hashable { case home, orders, run, earnings }

private let tokenDefaultsKey = "accountToken"

extension API.Account {
    var sessionUser: SessionUser {
        SessionUser(id: id, username: username, email: email, firstName: firstName, lastName: lastName)
    }
}

@MainActor
@Observable
final class AppModel {
    var selectedTab: AppTab = .home

    /// Signed-in account from Cloudflare D1. Nil shows the login screen.
    var session: SessionUser?
    /// True while a saved token is being checked on launch.
    var isRestoringSession = UserDefaults.standard.string(forKey: tokenDefaultsKey) != nil
    private var token = UserDefaults.standard.string(forKey: tokenDefaultsKey)

    /// Message to show in an alert, e.g. when an order expires.
    var notice: String?

    // MARK: Customer side
    var destination: Destination?
    var buildings: [Building] = []
    var runnersHeadingYourWay = 0
    var restaurants: [Restaurant] = []
    /// Restaurant whose Transact ordering flow is currently presented.
    var orderingFrom: Restaurant?
    var activeOrder: Order?
    /// Set when the active order is delivered, to present the confirmation screen.
    var deliveredOrder: Order?
    private var orderWatch: Task<Void, Never>?

    // MARK: Runner side
    var isAvailable = false
    var trip: Trip?
    var activeDelivery: Order?
    var requests: [DeliveryRequest] = []
    var recentDeliveries: [CompletedDelivery] = []
    var cashOutBalanceCents = 0
    var earnings: [EarningsPeriod: Earnings] = [:]

    init() {
        if token != nil {
            Task { await restoreSession() }
        }
    }

    // MARK: Auth

    func restoreSession() async {
        guard isRestoringSession else { return }
        defer { isRestoringSession = false }
        guard let token else { return }
        do {
            let account = try await API.currentAccount(token: token)
            session = account.sessionUser
            await loadSignedIn()
        } catch {
            clearToken()
        }
    }

    func logIn(username: String, password: String) async throws {
        let account = try await API.login(username: username, password: password)
        await store(account)
    }

    func register(
        email: String,
        firstName: String,
        lastName: String,
        username: String,
        password: String
    ) async throws {
        let account = try await API.register(
            email: email,
            firstName: firstName,
            lastName: lastName,
            username: username,
            password: password
        )
        await store(account)
    }

    func signOut() {
        let token = token
        Task { let _: Empty? = try? await API.send("POST", "logout", token: token) }
        orderWatch?.cancel()
        session = nil
        activeOrder = nil
        deliveredOrder = nil
        activeDelivery = nil
        clearToken()
    }

    private func store(_ account: API.Account) async {
        token = account.token
        UserDefaults.standard.set(account.token, forKey: tokenDefaultsKey)
        session = account.sessionUser
        await loadSignedIn()
    }

    private func clearToken() {
        token = nil
        UserDefaults.standard.removeObject(forKey: tokenDefaultsKey)
    }

    private func loadSignedIn() async {
        async let home: Void = loadHome()
        async let order: Void = loadActiveOrder()
        async let runner: Void = refreshRunner()
        _ = await (home, order, runner)
    }

    /// Calls the API as the signed-in user; a 401 signs out.
    private func call<Response: Decodable & Sendable>(
        _ method: String,
        _ path: String,
        body: [String: any Sendable]? = nil,
        query: [String: String] = [:]
    ) async throws -> Response {
        do {
            return try await API.send(method, path, body: body, query: query, token: token)
        } catch let error as APIError where error.status == 401 {
            signOut()
            throw error
        }
    }

    // MARK: Places

    private struct HomeResponse: Decodable, Sendable {
        let destination: Destination?
        let runnersHeadingYourWay: Int
        let restaurants: [Restaurant]
    }

    func loadHome() async {
        guard let home: HomeResponse = try? await call("GET", "home") else { return }
        destination = home.destination
        runnersHeadingYourWay = home.runnersHeadingYourWay
        restaurants = home.restaurants
    }

    func loadBuildings() async {
        guard buildings.isEmpty else { return }
        struct Response: Decodable, Sendable { let buildings: [Building] }
        if let response: Response = try? await call("GET", "buildings") {
            buildings = response.buildings
        }
    }

    func setDestination(buildingID: String, room: String, note: String) async throws {
        struct Response: Decodable, Sendable { let destination: Destination }
        let response: Response = try await call("PUT", "me/destination", body: [
            "buildingId": buildingID, "room": room, "note": note,
        ])
        destination = response.destination
        await loadHome()
    }

    // MARK: Customer actions

    private struct OrderResponse: Decodable, Sendable {
        let order: Order
        let payment: PaymentSheetConfig?
    }

    /// Creates the order. Returns a PaymentSheet config when the delivery fee + tip still has to be paid.
    func createOrder(
        restaurant: Restaurant,
        number: String,
        nameOnOrder: String,
        readyAt: Date,
        tip: Tip
    ) async throws -> PaymentSheetConfig? {
        let response: OrderResponse = try await call("POST", "orders", body: [
            "restaurantId": restaurant.id,
            "orderNumber": number,
            "nameOnOrder": nameOnOrder,
            "readyAt": API.iso(readyAt),
            "tipCents": tip.rawValue,
        ])
        activeOrder = response.order
        if response.payment == nil { watchActiveOrder() }
        return response.payment
    }

    /// PaymentSheet finished; the server checks with Stripe and opens the order to runners.
    func confirmPayment() async throws {
        guard let order = activeOrder else { return }
        let response: OrderResponse = try await call("POST", "orders/\(order.id)/payment")
        apply(response.order)
        watchActiveOrder()
    }

    func loadActiveOrder() async {
        struct Response: Decodable, Sendable { let order: Order? }
        guard let response: Response = try? await call("GET", "orders/active") else { return }
        activeOrder = response.order
        if let order = response.order, order.status != .awaitingPayment {
            if order.status != .matching { selectedTab = .orders }
            watchActiveOrder()
        }
    }

    /// Polls the active order until it's delivered, cancelled, or expired.
    private func watchActiveOrder() {
        orderWatch?.cancel()
        orderWatch = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(3))
                guard let self, let id = self.activeOrder?.id else { return }
                if let response: OrderResponse = try? await self.call("GET", "orders/\(id)") {
                    self.apply(response.order)
                }
            }
        }
    }

    private func apply(_ order: Order) {
        let wasWaiting = activeOrder.map { $0.status == .matching || $0.status == .awaitingPayment } ?? false
        switch order.status {
        case .accepted, .pickedUp:
            activeOrder = order
            if wasWaiting {
                // A runner took it: close the ordering flow and track in Orders.
                orderingFrom = nil
                selectedTab = .orders
            }
        case .delivered:
            orderWatch?.cancel()
            activeOrder = nil
            orderingFrom = nil
            deliveredOrder = order
        case .cancelled, .expired:
            orderWatch?.cancel()
            activeOrder = nil
            orderingFrom = nil
            notice = order.cancelReason.map { "\($0). Your card wasn’t charged." }
        case .awaitingPayment, .matching:
            activeOrder = order
        }
    }

    func cancelActiveOrder() async {
        guard let order = activeOrder else {
            orderingFrom = nil
            return
        }
        do {
            let response: OrderResponse = try await call("POST", "orders/\(order.id)/cancel")
            apply(response.order)
        } catch {
            notice = error.localizedDescription
        }
    }

    private struct RatingResponse: Decodable, Sendable {
        struct TipInfo: Decodable, Sendable { let id: String }
        let tip: TipInfo?
        let payment: PaymentSheetConfig?
    }

    /// Rates the runner. Returns the tip id and PaymentSheet config when an extra tip needs paying.
    func rate(_ order: Order, stars: Int, tags: [String], extraTipCents: Int) async throws -> (String, PaymentSheetConfig)? {
        let response: RatingResponse = try await call("POST", "orders/\(order.id)/rating", body: [
            "stars": stars, "tags": tags, "extraTipCents": extraTipCents,
        ])
        if let tip = response.tip, let payment = response.payment { return (tip.id, payment) }
        return nil
    }

    func confirmTip(_ tipID: String) async throws {
        let _: Empty = try await call("POST", "tips/\(tipID)/payment")
    }

    func finishDelivered(orderAgain: Bool) {
        let restaurant = restaurants.first { $0.id == deliveredOrder?.restaurant.id }
        deliveredOrder = nil
        Task { await loadHome() }
        if orderAgain, let restaurant {
            selectedTab = .home
            // Let the Delivered cover finish dismissing before presenting the ordering flow.
            Task {
                try? await Task.sleep(for: .milliseconds(600))
                orderingFrom = restaurant
            }
        }
    }

    // MARK: Chat

    func messages(for orderID: String, after: Date?) async throws -> [ChatMessage] {
        struct Response: Decodable, Sendable { let messages: [ChatMessage] }
        let response: Response = try await call(
            "GET", "orders/\(orderID)/messages",
            query: after.map { ["after": API.iso($0)] } ?? [:]
        )
        return response.messages
    }

    func send(_ body: String, to orderID: String) async throws -> ChatMessage {
        struct Response: Decodable, Sendable { let message: ChatMessage }
        let response: Response = try await call("POST", "orders/\(orderID)/messages", body: ["body": body])
        return response.message
    }

    func report(_ orderID: String, reason: String, details: String) async throws {
        let _: Empty = try await call("POST", "orders/\(orderID)/report", body: ["reason": reason, "details": details])
    }

    // MARK: Runner actions

    func refreshRunner() async {
        guard let status: RunnerStatus = try? await call("GET", "runner") else { return }
        isAvailable = status.available
        trip = status.trip
        activeDelivery = status.activeDelivery
        cashOutBalanceCents = status.balanceCents
    }

    func setAvailable(_ available: Bool) async {
        isAvailable = available
        struct Response: Decodable, Sendable { let available: Bool }
        if let response: Response = try? await call("PUT", "runner/status", body: ["available": available]) {
            isAvailable = response.available
        }
    }

    func refreshRequests(filter: String) async {
        struct Response: Decodable, Sendable {
            let trip: Trip?
            let requests: [DeliveryRequest]
        }
        guard let response: Response = try? await call("GET", "runner/requests", query: ["filter": filter]) else { return }
        trip = response.trip
        requests = response.requests
    }

    func accept(_ request: DeliveryRequest) async throws -> Order {
        let response: OrderResponse = try await call("POST", "orders/\(request.id)/accept", body: [:])
        requests.removeAll { $0.id == request.id }
        activeDelivery = response.order
        return response.order
    }

    func confirmPickup(_ order: Order) async throws -> Order {
        let response: OrderResponse = try await call("POST", "orders/\(order.id)/pickup")
        activeDelivery = response.order
        return response.order
    }

    func confirmDropoff(_ order: Order) async throws {
        let _: OrderResponse = try await call("POST", "orders/\(order.id)/deliver")
        activeDelivery = nil
        // The server credits the runner right after capture; give it a moment.
        try? await Task.sleep(for: .seconds(1))
        await refreshRunner()
        await loadEarnings(.thisWeek)
        await loadDeliveries()
    }

    /// Hands an accepted order back to other runners (before pickup only).
    func release(_ order: Order) async throws {
        let _: Empty = try await call("POST", "orders/\(order.id)/release")
        activeDelivery = nil
    }

    func reportLocation(latitude: Double, longitude: Double) async {
        let _: Empty? = try? await call("POST", "runner/location", body: ["lat": latitude, "lng": longitude])
    }

    func saveTrip(id: String?, from: String, to: String, leaveAt: Date, note: String) async throws {
        struct Response: Decodable, Sendable { let trip: Trip }
        let body: [String: any Sendable] = [
            "fromBuildingId": from, "toBuildingId": to, "leaveAt": API.iso(leaveAt), "note": note,
        ]
        let response: Response = if let id {
            try await call("PUT", "trips/\(id)", body: body)
        } else {
            try await call("POST", "trips", body: body)
        }
        trip = response.trip
    }

    func deleteTrip(_ trip: Trip) async throws {
        let _: Empty = try await call("DELETE", "trips/\(trip.id)")
        await refreshRunner()
    }

    // MARK: Earnings

    func loadEarnings(_ period: EarningsPeriod) async {
        guard let response: Earnings = try? await call("GET", "earnings", query: ["week": period.query]) else { return }
        earnings[period] = response
        cashOutBalanceCents = response.balanceCents
    }

    func loadDeliveries() async {
        struct Response: Decodable, Sendable { let deliveries: [CompletedDelivery] }
        if let response: Response = try? await call("GET", "deliveries") {
            recentDeliveries = response.deliveries
        }
    }

    /// Cashes out the balance. Returns a Stripe onboarding URL instead when payouts aren't set up yet.
    func cashOut() async throws -> URL? {
        struct Response: Decodable, Sendable { let balanceCents: Int }
        struct Link: Decodable, Sendable { let url: URL }
        do {
            let response: Response = try await call("POST", "payouts")
            cashOutBalanceCents = response.balanceCents
            return nil
        } catch let error as APIError where error.code == "payout_account_missing" || error.code == "payout_account_incomplete" {
            let link: Link = try await call("POST", "runner/payout-account")
            return link.url
        }
    }

    func earnings(for period: EarningsPeriod) -> [DailyEarning] {
        let days = earnings[period]?.days ?? []
        return days.enumerated().map { index, day in
            DailyEarning(day: ["M", "T", "W", "T", "F", "S", "S"][index % 7], index: index, amount: Double(day.cents) / 100)
        }
    }
}
