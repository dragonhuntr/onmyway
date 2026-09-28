import SwiftUI

enum RunRoute: Hashable {
    case request(DeliveryRequest)
    case active(DeliveryRequest)
}

enum RunFilter: String, CaseIterable, Identifiable {
    case onMyRoute = "On my route", nearby = "Nearby", readyNow = "Ready now"
    var id: Self { self }

    func includes(_ request: DeliveryRequest) -> Bool {
        switch self {
        case .onMyRoute: true
        case .nearby: request.detourMiles <= 0.2
        case .readyNow: request.pickupDetail.hasSuffix("Ready now")
        }
    }
}

/// 07_Runner_Home — earn on walks you're already taking.
struct RunnerHomeView: View {
    @Environment(AppModel.self) private var model
    @State private var path: [RunRoute] = []
    @State private var filter: RunFilter = .onMyRoute

    private var visible: [DeliveryRequest] { model.requests.filter(filter.includes) }

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    header
                    nextTrip
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
                    DeliveryRequestView(request: request) { path = [.active(request)] }
                case .active(let request):
                    ActiveDeliveryView(request: request) {
                        model.complete(request)
                        path.removeAll()
                    }
                }
            }
        }
    }

    private var header: some View {
        @Bindable var model = model
        return HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Run").font(.title.bold()).foregroundStyle(Color.ink)
                    .accessibilityAddTraits(.isHeader)
                Text("Earn on walks you’re already taking")
                    .font(.footnote).foregroundStyle(Color.inkSecondary)
            }
            Spacer()
            Toggle(isOn: $model.isAvailable) {
                Text("Available").font(.footnote.weight(.semibold)).foregroundStyle(Color.brandDeep)
            }
            .fixedSize()
            .tint(.brand)
        }
        .padding(.top, 8)
    }

    private var nextTrip: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("YOUR NEXT TRIP")
                    .font(.caption2.weight(.semibold)).tracking(0.88)
                    .foregroundStyle(Color.onBrandMuted)
                Spacer()
                HStack(spacing: 5) {
                    Image(.clockWhite).resizable().frame(width: 14, height: 14)
                    Text("Leaving 9:45 AM").font(.caption.weight(.semibold)).foregroundStyle(.white)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(Color.brandMid, in: .rect(cornerRadius: 8))
            }
            HStack(spacing: 10) {
                Text("Bruno’s")
                Image(.arrowRightWhite).resizable().frame(width: 20, height: 20).accessibilityLabel("to")
                Text("Burke Center")
            }
            .font(.title3.bold())
            .foregroundStyle(.white)
            .accessibilityElement(children: .combine)

            Text("12 min walk · Class in Room 174 at 10:00")
                .font(.footnote).foregroundStyle(Color.onBrand)

            HStack(spacing: 8) {
                Button(action: { /* TODO: route editor */ }) {
                    HStack(spacing: 6) {
                        Image(.routeWhite).resizable().frame(width: 16, height: 16)
                        Text("Edit route")
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color.brandMid, in: .rect(cornerRadius: 10))
                }
                Button(action: { /* TODO: add trip */ }) {
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
                Button(action: { /* TODO: filter sheet */ }) {
                    Image(.sliders).resizable().frame(width: 16, height: 16)
                        .frame(width: 36, height: 36)
                        .background(.white, in: .circle)
                        .overlay(Circle().strokeBorder(Color.hairline))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Filters")
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
                    stop(request.pickup, request.pickupDetail).padding(.bottom, 8)
                }
                HStack(alignment: .top, spacing: 10) {
                    Image(.stopMarkerDropoff).resizable().frame(width: 12, height: 12)
                    stop(request.dropoff, request.dropoffDetail)
                }
            }
            .accessibilityElement(children: .combine)
            Divider().overlay(Color.hairline)
            HStack {
                Pill(text: "+\(request.detourMinutes) min detour", icon: .footprintsGreen)
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
    let accept: () -> Void
    @Environment(\.dismiss) private var dismiss

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
                    StopRow(kind: .pickup, title: "Pick up · \(request.pickup)",
                            detail: "Ready ~\(request.readyAt) · Give order #\(request.orderNumber) at pickup")
                    StopRow(kind: .dropoff, title: "Drop off · \(request.dropoff)",
                            detail: "By \(request.dropoffBy) · Meet \(request.customer.components(separatedBy: " ").first ?? "")",
                            isLast: true)
                }
                .card()

                VStack(alignment: .leading, spacing: 8) {
                    Eyebrow("PICK UP ON TRANSACT")
                    Text("Order number  \(Text("#\(request.orderNumber)").fontWeight(.semibold))")
                        .font(.subheadline.weight(.medium))
                    Text("Name on order  \(Text(request.customer).fontWeight(.semibold))")
                        .font(.subheadline.weight(.medium))
                    Text("“\(request.note)”")
                        .font(.footnote).foregroundStyle(Color.inkSecondary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.canvas, in: .rect(cornerRadius: 8))
                }
                .foregroundStyle(Color.ink)
                .card()

                HStack(spacing: 12) {
                    Image(.customerAvatar).resizable().scaledToFill()
                        .frame(width: 44, height: 44)
                        .clipShape(.circle)
                        .accessibilityLabel("Photo of \(request.customer)")
                    VStack(alignment: .leading, spacing: 2) {
                        Text(request.customer).font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                        HStack(spacing: 4) {
                            Image(.star13).resizable().frame(width: 13, height: 13)
                            Text("\(request.customerRating.formatted()) · \(request.customerOrders) orders")
                                .font(.caption).foregroundStyle(Color.inkSecondary)
                        }
                    }
                    Spacer(minLength: 0)
                    Pill(text: "Verified student", icon: .shieldGreen)
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
            HStack(spacing: 10) {
                Button("Skip") { dismiss() }
                    .buttonStyle(.outlineCTA)
                    .frame(width: 104)
                Button("Accept delivery", action: accept)
                    .buttonStyle(.primaryCTA)
            }
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
    enum Stage { case pickup, dropoff }

    let request: DeliveryRequest
    let complete: () -> Void
    @State private var stage: Stage = .pickup
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            stepProgress
            // The sheet overlaps the map by its corner radius.
            VStack(spacing: -24) {
                CampusMap(height: 224, route: .init(image: .routeRequest, size: CGSize(width: 282, height: 120))) { s in
                    MapItem(space: s, x: 34, y: 148) { MapPin(kind: .pickup, label: request.pickup) }
                    MapItem(space: s, x: 321.5, y: 32) {
                        MapPin(kind: .dropoff, label: request.dropoff.components(separatedBy: " · ").first ?? "")
                    }
                    MapItem(space: s, x: stage == .pickup ? 102 : 240, y: stage == .pickup ? 108 : 66) {
                        RunnerDot().accessibilityHidden(false).accessibilityLabel("You")
                    }
                }
                .animation(.easeInOut, value: stage)

                VStack(alignment: .leading, spacing: 12) {
                    destinationHeader
                    if stage == .pickup { orderNumberCard }
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
        .navigationTitle(stage == .pickup ? "Head to pickup" : "Deliver order")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarVisibility(.hidden, for: .tabBar)
        .footer {
            Button(stage == .pickup ? "Confirm pickup" : "Confirm drop-off") {
                if stage == .pickup { withAnimation { stage = .dropoff } } else { complete() }
            }
            .buttonStyle(.primaryCTA)
            Button(action: { /* TODO: report an issue */ }) {
                Label("Something’s wrong with this order", systemImage: "xmark")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(Color.inkSecondary)
            }
            .buttonStyle(.plain)
            .frame(minHeight: 24)
        }
    }

    private var stepProgress: some View {
        HStack(spacing: 6) {
            ForEach(0..<3, id: \.self) { step in
                Capsule()
                    .fill(step < (stage == .pickup ? 1 : 2) ? Color.brand : .hairline)
                    .frame(height: 6)
            }
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
        .accessibilityElement()
        .accessibilityLabel("Step \(stage == .pickup ? 1 : 2) of 3")
    }

    private var destinationHeader: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(stage == .pickup ? request.pickup : request.dropoff)
                    .font(.title3.bold()).foregroundStyle(Color.ink)
                Text(stage == .pickup
                     ? "Counter 3 · Order ready at \(request.readyAt) · 2 min away"
                     : "Deliver by \(request.dropoffBy) · 6 min away")
                    .font(.footnote).foregroundStyle(Color.inkSecondary)
            }
            Spacer()
            Button(action: { /* TODO: open walking directions */ }) {
                Image(.navigation).resizable().frame(width: 22, height: 22)
                    .frame(width: 44, height: 44)
                    .background(.white, in: .circle)
                    .overlay(Circle().strokeBorder(Color.hairline))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Directions")
        }
    }

    private var orderNumberCard: some View {
        VStack(spacing: 4) {
            HStack(spacing: 6) {
                Image(systemName: "receipt").font(.caption)
                Text("GIVE THIS ORDER NUMBER AT PICKUP").font(.caption.weight(.semibold)).tracking(0.72)
            }
            .foregroundStyle(Color.brandDeep)
            Text("#\(request.orderNumber)").font(.largeTitle.bold()).foregroundStyle(Color.ink)
            Text("Name on order · \(request.customer)").font(.footnote).foregroundStyle(Color.inkSecondary)
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
                .accessibilityLabel("Photo of \(request.customer)")
            VStack(alignment: .leading, spacing: 1) {
                Text("\(request.customer) · \(request.dropoff.components(separatedBy: " · ").last ?? "")")
                    .font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                Text("“\(request.note)”").font(.caption).foregroundStyle(Color.inkSecondary)
            }
            Spacer(minLength: 0)
            circleButton("message", label: "Message \(request.customer)") { openURL(URL(string: "sms:5555550199")!) }
            circleButton("phone", label: "Call \(request.customer)") { openURL(URL(string: "tel:5555550199")!) }
        }
    }

    private func circleButton(_ symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.body)
                .foregroundStyle(Color.brand)
                .frame(width: 44, height: 44)
                .background(Color.brandSoft, in: .circle)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}
