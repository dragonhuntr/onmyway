import SwiftUI

enum RunRoute: Hashable {
    case request(DeliveryRequest)
    case active(Order)
}

enum RunFilter: String, CaseIterable, Identifiable {
    case onMyRoute = "On my route", nearby = "Nearby", readyNow = "Ready now"
    var id: Self { self }

    var query: String {
        switch self {
        case .onMyRoute: "on_my_route"
        case .nearby: "nearby"
        case .readyNow: "ready_now"
        }
    }
}

/// 07_Runner_Home — earn on walks you're already taking.
struct RunnerHomeView: View {
    @Environment(AppModel.self) private var model
    @State private var path: [RunRoute] = []
    @State private var filter: RunFilter = .onMyRoute
    @State private var editingTrip: Trip?
    @State private var addingTrip = false

    private var visible: [DeliveryRequest] { model.requests }

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    header
                    if let delivery = model.activeDelivery {
                        resumeBanner(delivery)
                    }
                    if let trip = model.trip { nextTrip(trip) } else { noTrip }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 14)

                filters
                orders
            }
            .background(Color.canvas)
            .navigationTitle("Run")
            .toolbarVisibility(.hidden, for: .navigationBar)
            .navigationDestination(for: RunRoute.self) { route in
                switch route {
                case .request(let request):
                    DeliveryRequestView(request: request) { order in path = [.active(order)] }
                case .active(let order):
                    ActiveDeliveryView(order: order) { path.removeAll() }
                }
            }
            .sheet(item: $editingTrip) { TripEditor(trip: $0) }
            .sheet(isPresented: $addingTrip) { TripEditor(trip: nil) }
            .refreshable { await refresh() }
            .task(id: filter) {
                // Poll the feed while this screen is showing.
                while !Task.isCancelled {
                    await refresh()
                    try? await Task.sleep(for: .seconds(5))
                }
            }
        }
    }

    private func refresh() async {
        await model.refreshRunner()
        if model.isAvailable { await model.refreshRequests(filter: filter.query) }
    }

    private func resumeBanner(_ delivery: Order) -> some View {
        Button { path = [.active(delivery)] } label: {
            HStack(spacing: 12) {
                Image(.bagWhite16).resizable().frame(width: 16, height: 16)
                    .frame(width: 32, height: 32)
                    .background(Color.amber, in: .circle)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Delivery in progress").font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                    Text("#\(delivery.number) · \(delivery.restaurant.name) → \(delivery.destination.building)")
                        .font(.caption).foregroundStyle(Color.inkSecondary)
                }
                Spacer(minLength: 0)
                Image(.chevronRight).resizable().frame(width: 20, height: 20)
            }
            .card(padding: 12)
        }
        .buttonStyle(.plain)
    }

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Run").font(.title.bold()).foregroundStyle(Color.ink)
                    .accessibilityAddTraits(.isHeader)
                Text("Earn on walks you’re already taking")
                    .font(.footnote).foregroundStyle(Color.inkSecondary)
            }
            Spacer()
            Toggle(isOn: Binding(get: { model.isAvailable }, set: { on in Task { await model.setAvailable(on) } })) {
                Text("Available").font(.footnote.weight(.semibold)).foregroundStyle(Color.brandDeep)
            }
            .fixedSize()
            .tint(.brand)
        }
        .padding(.top, 8)
    }

    private var noTrip: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("YOUR NEXT TRIP")
                .font(.caption2.weight(.semibold)).tracking(0.88)
                .foregroundStyle(Color.onBrandMuted)
            Text("Add a walk you’re already taking")
                .font(.title3.bold()).foregroundStyle(.white)
            Text("We’ll show orders that fit your route, sorted by detour.")
                .font(.footnote).foregroundStyle(Color.onBrand)
            Button { addingTrip = true } label: {
                Text("+ Add a trip")
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color.brandMid, in: .rect(cornerRadius: 10))
            }
            .buttonStyle(.plain)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(.white)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.brand, in: .rect(cornerRadius: 18))
    }

    private func nextTrip(_ trip: Trip) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("YOUR NEXT TRIP")
                    .font(.caption2.weight(.semibold)).tracking(0.88)
                    .foregroundStyle(Color.onBrandMuted)
                Spacer()
                HStack(spacing: 5) {
                    Image(.clockWhite).resizable().frame(width: 14, height: 14)
                    Text("Leaving \(trip.leaveAt.shortTime)").font(.caption.weight(.semibold)).foregroundStyle(.white)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(Color.brandMid, in: .rect(cornerRadius: 8))
            }
            HStack(spacing: 10) {
                Text(trip.from.name)
                Image(.arrowRightWhite).resizable().frame(width: 20, height: 20).accessibilityLabel("to")
                Text(trip.to.name)
            }
            .font(.title3.bold())
            .foregroundStyle(.white)
            .accessibilityElement(children: .combine)

            Text(trip.note.isEmpty ? "\(trip.walkMinutes) min walk" : "\(trip.walkMinutes) min walk · \(trip.note)")
                .font(.footnote).foregroundStyle(Color.onBrand)

            HStack(spacing: 8) {
                Button { editingTrip = trip } label: {
                    HStack(spacing: 6) {
                        Image(.routeWhite).resizable().frame(width: 16, height: 16)
                        Text("Edit route")
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color.brandMid, in: .rect(cornerRadius: 10))
                }
                Button { addingTrip = true } label: {
                    Text("+ Add another trip")
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Color.brandOutline))
                }
            }
            .buttonStyle(.plain)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(.white)
        }
        .padding(18)
        .background(Color.brand, in: .rect(cornerRadius: 18))
    }

    private var filters: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(RunFilter.allCases) { option in
                    ChipToggle(title: option.rawValue, isSelected: filter == option) { filter = option }
                }
            }
            .padding(.horizontal, 20)
        }
        .padding(.bottom, 12)
    }

    @ViewBuilder private var orders: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Orders along your route").font(.headline).foregroundStyle(Color.ink)
                Spacer()
                if model.isAvailable, !visible.isEmpty {
                    Pill(text: "\(visible.count) new", foreground: .amberInk, background: .amberSoft)
                }
            }
            if !model.isAvailable {
                ContentUnavailableView("You’re offline", systemImage: "moon.zzz",
                                       description: Text("Turn on Available to see orders along your route."))
            } else if visible.isEmpty {
                ContentUnavailableView("No orders right now", systemImage: "figure.walk",
                                       description: Text("We’ll show new orders as they come in."))
            } else {
                ForEach(visible) { request in
                    RequestCard(request: request) { path.append(.request(request)) }
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 20)
    }
}

struct RequestCard: View {
    let request: DeliveryRequest
    let accept: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top, spacing: 10) {
                    Image(.stopMarkerPickup).resizable().frame(width: 12, height: 34)
                    stop(request.restaurant.name, "Pick up · \(request.readyText)").padding(.bottom, 8)
                }
                HStack(alignment: .top, spacing: 10) {
                    Image(.stopMarkerDropoff).resizable().frame(width: 12, height: 12)
                    stop(request.destination.full, request.onRoute ? "On your way" : "\(request.detourMiles.formatted()) mi off your route")
                }
            }
            .accessibilityElement(children: .combine)
            Divider().overlay(Color.hairline)
            HStack {
                Pill(text: "+\(request.detourMinutes) min detour", icon: .footprintsGreen)
                Pill(text: request.payoutCents.usd, foreground: .amberInk, background: .amberSoft)
                Spacer()
                Button("Accept", action: accept).buttonStyle(CompactButtonStyle())
            }
        }
        .card(padding: 14)
    }

    private func stop(_ title: String, _ detail: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
            Text(detail).font(.caption).foregroundStyle(Color.inkSecondary)
        }
    }
}

/// 08_Delivery_Request — pickup/drop-off detail before accepting.
struct DeliveryRequestView: View {
    let request: DeliveryRequest
    let accepted: (Order) -> Void
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var isAccepting = false
    @State private var errorMessage: String?

    private var customerFirstName: String { request.customer.name.components(separatedBy: " ").first ?? "" }

    var body: some View {
        ScrollView {
            CampusMap(height: 190, route: .init(image: .routeRequest, size: CGSize(width: 282, height: 114.2))) { s in
                MapItem(space: s, x: 49, y: 140.6) { MapPin(kind: .pickup, label: "Pick up") }
                MapItem(space: s, x: 330, y: 30.4) { MapPin(kind: .dropoff, label: "Drop off") }
                MapItem(space: s, x: 150, y: 49) { MapLabel(text: "Your route", filled: true) }
            }

            VStack(spacing: 12) {
                HStack(spacing: 5) {
                    Image(.footprintsDark).resizable().frame(width: 14.17, height: 11.33)
                    Text("+\(request.detourMinutes) min detour · \(request.detourMiles.formatted()) mi off your route")
                        .font(.caption.weight(.semibold)).foregroundStyle(Color.ink)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(.white, in: .rect(cornerRadius: 8))

                VStack(spacing: 0) {
                    StopRow(kind: .pickup, title: "Pick up · \(request.restaurant.name)",
                            detail: "\(request.readyText) · Give order #\(request.number) at pickup")
                    StopRow(kind: .dropoff, title: "Drop off · \(request.destination.full)",
                            detail: "By \(request.dropoffBy.shortTime) · Meet \(customerFirstName)",
                            isLast: true)
                }
                .card()

                VStack(alignment: .leading, spacing: 8) {
                    Eyebrow("PICK UP ON TRANSACT")
                    Text("Order number  \(Text("#\(request.number)").fontWeight(.semibold))")
                        .font(.subheadline.weight(.medium))
                    Text("Name on order  \(Text(request.nameOnOrder).fontWeight(.semibold))")
                        .font(.subheadline.weight(.medium))
                    if !request.destination.note.isEmpty {
                        Text("“\(request.destination.note)”")
                            .font(.footnote).foregroundStyle(Color.inkSecondary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 8)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.canvas, in: .rect(cornerRadius: 8))
                    }
                }
                .foregroundStyle(Color.ink)
                .card()

                HStack(spacing: 12) {
                    Image(.customerAvatar).resizable().scaledToFill()
                        .frame(width: 44, height: 44)
                        .clipShape(.circle)
                        .accessibilityLabel("Photo of \(request.customer.name)")
                    VStack(alignment: .leading, spacing: 2) {
                        Text(request.customer.name).font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                        Text(request.customer.orders == 1 ? "1 order" : "\(request.customer.orders) orders")
                            .font(.caption).foregroundStyle(Color.inkSecondary)
                    }
                    Spacer(minLength: 0)
                    Pill(text: "Penn State", icon: .shieldGreen)
                }
                .card(padding: 12)
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 20)
        }
        .background(Color.canvas)
        .navigationTitle("Delivery request")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarVisibility(.hidden, for: .tabBar)
        .footer {
            if let errorMessage {
                Text(errorMessage).font(.footnote.weight(.semibold)).foregroundStyle(Color.inkSecondary)
            }
            HStack(spacing: 10) {
                Button("Skip") { dismiss() }
                    .buttonStyle(.outlineCTA)
                    .frame(width: 104)
                Button(isAccepting ? "Accepting…" : "Accept delivery · \(request.payoutCents.usd)") {
                    Task { await accept() }
                }
                .buttonStyle(.primaryCTA)
                .disabled(isAccepting)
            }
        }
    }

    private func accept() async {
        isAccepting = true
        defer { isAccepting = false }
        do {
            accepted(try await model.accept(request))
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

struct StopRow: View {
    let kind: MapPin.Kind
    let title: String
    let detail: String
    var isLast = false

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(spacing: 0) {
                Image(kind == .pickup ? .bagWhite16 : .pinWhite16).resizable().frame(width: 16, height: 16)
                    .frame(width: 32, height: 32)
                    .background(kind == .pickup ? Color.amber : .brand, in: .circle)
                if !isLast {
                    RoundedRectangle(cornerRadius: 1).fill(Color.hairline).frame(width: 2, height: 28)
                }
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                Text(detail).font(.footnote).foregroundStyle(Color.inkSecondary)
            }
            .padding(.top, 4)
            .padding(.bottom, isLast ? 0 : 14)
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }
}

/// 09_Active_Delivery — step-by-step pickup and hand-off.
///
/// Rebuilt from the Figma layer structure (the design-context export for this frame was not
/// available), so colors and icons follow the other screens' tokens.
struct ActiveDeliveryView: View {
    let done: () -> Void
    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL
    @State private var order: Order
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var chatting = false
    @State private var reporting = false
    @State private var location = LocationReporter()

    init(order: Order, done: @escaping () -> Void) {
        _order = State(initialValue: order)
        self.done = done
    }

    private var atPickup: Bool { order.status == .accepted }
    private var customerName: String { order.customer?.name ?? order.nameOnOrder }

    var body: some View {
        ScrollView {
            stepProgress
            // The sheet overlaps the map by its corner radius.
            VStack(spacing: -24) {
                CampusMap(height: 224, route: .init(image: .routeRequest, size: CGSize(width: 282, height: 120))) { s in
                    MapItem(space: s, x: 34, y: 148) { MapPin(kind: .pickup, label: order.restaurant.name) }
                    MapItem(space: s, x: 321.5, y: 32) { MapPin(kind: .dropoff, label: order.destination.building) }
                    MapItem(space: s, x: atPickup ? 102 : 240, y: atPickup ? 108 : 66) {
                        RunnerDot().accessibilityHidden(false).accessibilityLabel("You")
                    }
                }
                .animation(.easeInOut, value: order.status)

                VStack(alignment: .leading, spacing: 12) {
                    destinationHeader
                    if atPickup { orderNumberCard }
                    customerRow
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 20)
                .background(.white)
                .clipShape(.rect(topLeadingRadius: 24, topTrailingRadius: 24))
            }
        }
        .background(alignment: .top) { Color.canvas.frame(height: 300) }
        .background(.white)
        .navigationTitle(atPickup ? "Head to pickup" : "Deliver order")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarVisibility(.hidden, for: .tabBar)
        .sheet(isPresented: $chatting) { ChatView(orderID: order.id, title: customerName) }
        .confirmationDialog("What’s wrong?", isPresented: $reporting, titleVisibility: .visible) {
            ForEach(["Order isn’t ready", "Wrong order number", "Can’t find the customer", "Other"], id: \.self) { reason in
                Button(reason) { Task { try? await model.report(order.id, reason: reason, details: "") } }
            }
            if atPickup {
                Button("Hand back this order", role: .destructive) { Task { await release() } }
            }
        }
        .onAppear {
            location.start { coordinate in
                Task { await model.reportLocation(latitude: coordinate.latitude, longitude: coordinate.longitude) }
            }
        }
        .onDisappear { location.stop() }
        .footer {
            if let errorMessage {
                Text(errorMessage).font(.footnote.weight(.semibold)).foregroundStyle(Color.inkSecondary)
            }
            Button(atPickup ? "Confirm pickup" : "Confirm drop-off") { Task { await advance() } }
                .buttonStyle(.primaryCTA)
                .disabled(isSaving)
            Button { reporting = true } label: {
                Label("Something’s wrong with this order", systemImage: "xmark")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(Color.inkSecondary)
            }
            .buttonStyle(.plain)
            .frame(minHeight: 24)
        }
    }

    private func advance() async {
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }
        do {
            if atPickup {
                let updated = try await model.confirmPickup(order)
                withAnimation { order = updated }
            } else {
                try await model.confirmDropoff(order)
                done()
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func release() async {
        do {
            try await model.release(order)
            done()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private var stepProgress: some View {
        HStack(spacing: 6) {
            ForEach(0..<3, id: \.self) { step in
                Capsule()
                    .fill(step < (atPickup ? 1 : 2) ? Color.brand : .hairline)
                    .frame(height: 6)
            }
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
        .accessibilityElement()
        .accessibilityLabel("Step \(atPickup ? 1 : 2) of 3")
    }

    private var destinationHeader: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(atPickup ? order.restaurant.name : order.destination.full)
                    .font(.title3.bold()).foregroundStyle(Color.ink)
                Text(atPickup
                     ? "\(order.restaurant.building.name) · Ready at \(order.readyAt?.shortTime ?? "any minute")"
                     : "\(order.walkMinutes) min walk from pickup")
                    .font(.footnote).foregroundStyle(Color.inkSecondary)
            }
            Spacer()
            Button { openDirections() } label: {
                Image(.navigation).resizable().frame(width: 22, height: 22)
                    .frame(width: 44, height: 44)
                    .background(.white, in: .circle)
                    .overlay(Circle().strokeBorder(Color.hairline))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Directions")
        }
    }

    /// Walking directions in Apple Maps to the next stop's building.
    private func openDirections() {
        let building = atPickup ? order.restaurant.building.name : order.destination.building
        var components = URLComponents(string: "maps://")!
        components.queryItems = [
            URLQueryItem(name: "daddr", value: "\(building), Penn State Behrend, Erie, PA"),
            URLQueryItem(name: "dirflg", value: "w"),
        ]
        if let url = components.url { openURL(url) }
    }

    private var orderNumberCard: some View {
        VStack(spacing: 4) {
            HStack(spacing: 6) {
                Image(systemName: "receipt").font(.caption)
                Text("GIVE THIS ORDER NUMBER AT PICKUP").font(.caption.weight(.semibold)).tracking(0.72)
            }
            .foregroundStyle(Color.brandDeep)
            Text("#\(order.number)").font(.largeTitle.bold()).foregroundStyle(Color.ink)
            Text("Name on order · \(order.nameOnOrder)").font(.footnote).foregroundStyle(Color.inkSecondary)
        }
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .background(Color.brandSoft, in: .rect(cornerRadius: 16))
        .accessibilityElement(children: .combine)
    }

    private var customerRow: some View {
        HStack(spacing: 12) {
            Image(.customerAvatar).resizable().scaledToFill()
                .frame(width: 44, height: 44)
                .clipShape(.circle)
                .accessibilityLabel("Photo of \(customerName)")
            VStack(alignment: .leading, spacing: 1) {
                Text(order.destination.room.isEmpty ? customerName : "\(customerName) · \(order.destination.room)")
                    .font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                if !order.destination.note.isEmpty {
                    Text("“\(order.destination.note)”").font(.caption).foregroundStyle(Color.inkSecondary)
                }
            }
            Spacer(minLength: 0)
            Button { chatting = true } label: {
                Image(systemName: "message")
                    .font(.body)
                    .foregroundStyle(Color.brand)
                    .frame(width: 44, height: 44)
                    .background(Color.brandSoft, in: .circle)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Message \(customerName)")
        }
    }
}
