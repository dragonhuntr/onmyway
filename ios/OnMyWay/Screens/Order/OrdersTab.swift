import SwiftUI

struct OrdersTab: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            Group {
                if let order = model.activeOrder, let runner = order.runner {
                    OrderTrackingView(order: order, runner: runner)
                } else if let order = model.activeOrder, order.status == .matching, model.orderingFrom == nil {
                    FindingRunnerView()
                } else {
                    ContentUnavailableView {
                        Label("No active orders", systemImage: "receipt")
                    } description: {
                        Text("Order on Transact, then send it to a runner heading your way.")
                    } actions: {
                        Button("Browse dining halls") { model.selectedTab = .home }
                            .buttonStyle(.borderedProminent)
                    }
                    .background(Color.canvas)
                    .navigationTitle("Orders")
                }
            }
        }
        .fullScreenCover(item: $model.deliveredOrder) { order in
            DeliveredView(order: order).environment(model)
        }
    }
}

/// 05_Order_Tracking — live map plus runner details and delivery timeline.
struct OrderTrackingView: View {
    let order: Order
    let runner: Runner
    @State private var chatting = false

    private var eta: Int { order.etaMinutes ?? order.walkMinutes }
    /// Where the runner dot sits on the illustrated map: near pickup until picked up, then halfway.
    private var runnerPoint: (x: CGFloat, y: CGFloat) { order.status == .pickedUp ? (204.9, 111.6) : (70, 200) }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                map
                sheet
            }
        }
        .background(Color.canvas)
        .scrollBounceBehavior(.basedOnSize)
        .toolbarVisibility(.hidden, for: .navigationBar)
        .sheet(isPresented: $chatting) { ChatView(orderID: order.id, title: runner.name) }
    }

    private var map: some View {
        CampusMap(height: 310, route: .init(image: .routeTracking, size: CGSize(width: 282, height: 183.8))) { s in
            MapItem(space: s, x: 34, y: 229.4) { MapPin(kind: .pickup, label: order.restaurant.name) }
            MapItem(space: s, x: 321.5, y: 49.6) { MapPin(kind: .dropoff, label: order.destination.building) }
            MapItem(space: s, x: runnerPoint.x, y: runnerPoint.y) {
                RunnerDot().accessibilityHidden(false).accessibilityLabel("\(runner.name), \(eta) minutes away")
            }
            MapItem(space: s, x: runnerPoint.x, y: runnerPoint.y - 35.6) {
                MapLabel(text: "\(runner.firstName) · \(eta) min", filled: true)
            }
        }
        .animation(.easeInOut, value: order.status)
    }

    private var sheet: some View {
        VStack(alignment: .leading, spacing: 14) {
            Capsule().fill(Color.hairline).frame(width: 36, height: 5).frame(maxWidth: .infinity)

            HStack(spacing: 12) {
                Image(.runnerAvatar).resizable().scaledToFill()
                    .frame(width: 48, height: 48)
                    .clipShape(.circle)
                    .accessibilityLabel("Photo of \(runner.name), your runner")
                VStack(alignment: .leading, spacing: 2) {
                    Text(order.status == .pickedUp ? "\(runner.firstName) is on the way" : "\(runner.firstName) is getting your food")
                        .font(.title3.bold()).foregroundStyle(Color.ink)
                    HStack(spacing: 4) {
                        if runner.rating != nil { Image(.star).resizable().frame(width: 14, height: 14) }
                        Text(runner.summary)
                            .font(.footnote).foregroundStyle(Color.inkSecondary)
                    }
                }
                Spacer(minLength: 0)
                VStack(spacing: 0) {
                    Text("\(eta) min").font(.callout.bold())
                    Text("ETA").font(.system(size: 10, weight: .semibold)).tracking(0.8)
                }
                .foregroundStyle(Color.brandDeep)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Color.brandSoft, in: .rect(cornerRadius: 12))
                .accessibilityElement(children: .combine)
            }

            if let routeNote = runner.routeNote {
                HStack(spacing: 10) {
                    Image(.footprintsAmber).resizable().frame(width: 18, height: 18)
                    Text(routeNote).font(.footnote).foregroundStyle(Color.amberInk)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color.amberSoft, in: .rect(cornerRadius: 12))
            }

            timeline

            Button("Message \(runner.firstName)") { chatting = true }
                .buttonStyle(.secondaryCTA)
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
        .padding(.bottom, 20)
        .background(.white)
        .clipShape(.rect(topLeadingRadius: 24, topTrailingRadius: 24))
        .shadow(color: Color(hex: 0x14211A).opacity(0.12), radius: 8, y: -4)
    }

    private var timeline: some View {
        VStack(alignment: .leading, spacing: 0) {
            let pickedUp = order.status == .pickedUp
            let arriving = Date.now.addingTimeInterval(TimeInterval(eta * 60)).shortTime
            TimelineStep(title: "Order #\(order.number) sent to \(runner.firstName)",
                         subtitle: order.acceptedAt?.shortTime, state: .done)
            TimelineStep(title: pickedUp ? "Picked up at \(order.restaurant.name)" : "Picking up at \(order.restaurant.name)",
                         subtitle: order.pickedUpAt?.shortTime ?? order.readyAt.map { "Ready ~\($0.shortTime)" },
                         state: pickedUp ? .done : .current)
            TimelineStep(title: "On the way · \(order.destination.building)",
                         subtitle: pickedUp ? "Arriving ~\(arriving)" : nil,
                         state: pickedUp ? .current : .upcoming)
            TimelineStep(title: "Delivered", subtitle: nil, state: .upcoming, isLast: true)
        }
    }
}

struct TimelineStep: View {
    enum State { case done, current, upcoming }
    let title: String
    let subtitle: String?
    let state: State
    var isLast = false

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(spacing: 0) {
                marker
                if !isLast {
                    RoundedRectangle(cornerRadius: 1)
                        .fill(state == .done ? Color.brand : .hairline)
                        .frame(width: 2, height: 22)
                }
            }
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.subheadline.weight(state == .current ? .semibold : .medium))
                    .foregroundStyle(state == .upcoming ? Color.inkSecondary : .ink)
                if let subtitle {
                    Text(subtitle).font(.caption).foregroundStyle(Color.inkSecondary)
                }
            }
            .padding(.bottom, 8)
            Spacer(minLength: 0)
        }
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
        .accessibilityValue(state == .done ? "Complete" : state == .current ? "In progress" : "Not started")
    }

    @ViewBuilder private var marker: some View {
        switch state {
        case .done:
            Image(.checkSmallWhite).resizable().frame(width: 12, height: 12)
                .frame(width: 20, height: 20)
                .background(Color.brand, in: .circle)
        case .current:
            Image(.timelineCurrent).resizable().frame(width: 20, height: 20)
        case .upcoming:
            Circle().strokeBorder(Color.hairline, lineWidth: 2.5).background(.white, in: .circle)
                .frame(width: 20, height: 20)
        }
    }
}

/// 06_Delivered — confirmation, savings, rating, and optional extra tip.
struct DeliveredView: View {
    let order: Order
    @Environment(AppModel.self) private var model
    @State private var rating = 5
    @State private var tags: Set<String> = []
    @State private var extraTip: Int?
    @State private var isSubmitting = false
    @State private var errorMessage: String?

    private let feedback = ["On time", "Friendly", "Careful with food"]
    private let extraTips = [100, 200, 300]
    private var runnerName: String { order.runner?.firstName ?? "your runner" }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                VStack(spacing: 10) {
                    Image(.checkLargeWhite).resizable().frame(width: 40, height: 40)
                        .frame(width: 64, height: 64)
                        .background(Color.brand, in: .circle)
                        .padding(12)
                        .background(Color.brandSoft, in: .circle)
                        .accessibilityHidden(true)
                    Text("Delivered!").font(.title.bold()).foregroundStyle(Color.ink)
                    Text("\(runnerName) handed off your order at \(order.destination.full)\(order.deliveredAt.map { " at \($0.shortTime)" } ?? "").")
                        .font(.subheadline)
                        .foregroundStyle(Color.inkSecondary)
                        .multilineTextAlignment(.center)
                }

                HStack(spacing: 12) {
                    Image(.dollar).resizable().frame(width: 20, height: 20)
                        .frame(width: 40, height: 40)
                        .background(Color.amber, in: .circle)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("You paid \(order.deliveryFeeCents.usd) for delivery")
                            .font(.subheadline.weight(.semibold)).foregroundStyle(Color.amberInkDeep)
                        Text("$5.99 less than the cheapest commercial app.")
                            .font(.footnote).foregroundStyle(Color.amberInk)
                    }
                    Spacer(minLength: 0)
                }
                .padding(14)
                .background(Color.amberSoft, in: .rect(cornerRadius: 14))
                .accessibilityElement(children: .combine)

                rateCard
                tipCard
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 20)
        }
        .background(Color.canvas)
        .footer {
            if let errorMessage {
                Text(errorMessage).font(.footnote.weight(.semibold)).foregroundStyle(Color.inkSecondary)
            }
            Button("Done") { Task { await finish(orderAgain: false) } }
                .buttonStyle(.primaryCTA)
                .disabled(isSubmitting)
            Button("Order again") { Task { await finish(orderAgain: true) } }
                .buttonStyle(.secondaryCTA)
                .disabled(isSubmitting)
        }
    }

    /// Sends the rating (and pays any extra tip), then closes.
    private func finish(orderAgain: Bool) async {
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }
        do {
            if order.rating == nil, let (tipID, payment) = try await model.rate(
                order, stars: rating, tags: Array(tags), extraTipCents: extraTip ?? 0
            ) {
                if case .failed(let error) = await Payments.pay(payment) {
                    errorMessage = error.localizedDescription
                    return
                }
                try? await model.confirmTip(tipID)
            }
        } catch let error as APIError where error.code == "already_rated" {
            // Rated on an earlier tap; just close.
        } catch {
            errorMessage = error.localizedDescription
            return
        }
        model.finishDelivered(orderAgain: orderAgain)
    }

    private var rateCard: some View {
        VStack(spacing: 10) {
            Text("How was \(runnerName)?").font(.headline).foregroundStyle(Color.ink)
            HStack(spacing: 6) {
                ForEach(1...5, id: \.self) { star in
                    Button { rating = star } label: {
                        Image(star <= rating ? .starFilled : .starEmpty).resizable()
                            .frame(width: 34, height: 34)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(star) star\(star == 1 ? "" : "s")")
                    .accessibilityAddTraits(star == rating ? .isSelected : [])
                }
            }
            FlowLayout(spacing: 8) {
                ForEach(feedback, id: \.self) { tag in
                    ChipToggle(title: tag, isSelected: tags.contains(tag)) {
                        if tags.contains(tag) { tags.remove(tag) } else { tags.insert(tag) }
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .background(.white, in: .rect(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.hairline))
    }

    private var tipCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Add to your \(order.tipCents.usd) tip?").font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                Spacer()
                Text("100% to \(runnerName)").font(.caption.weight(.medium)).foregroundStyle(Color.inkSecondary)
            }
            HStack(spacing: 8) {
                ForEach(extraTips, id: \.self) { tip in
                    ChipToggle(title: "+\(tip.usd)", isSelected: extraTip == tip, fillsWidth: true) {
                        extraTip = extraTip == tip ? nil : tip
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.white, in: .rect(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.hairline))
    }
}

/// Centered wrapping layout for tag chips.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = rows(width: proposal.width ?? .infinity, subviews: subviews)
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0))
        return CGSize(width: proposal.width ?? rows.map(\.width).max() ?? 0, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in rows(width: bounds.width, subviews: subviews) {
            var x = bounds.midX - row.width / 2
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: .unspecified)
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row { var indices: [Int] = []; var width: CGFloat = 0; var height: CGFloat = 0 }

    private func rows(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows = [Row()]
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let needed = rows[rows.count - 1].indices.isEmpty ? size.width : rows[rows.count - 1].width + spacing + size.width
            if needed > width, !rows[rows.count - 1].indices.isEmpty {
                rows.append(Row())
            }
            var row = rows[rows.count - 1]
            row.width = row.indices.isEmpty ? size.width : row.width + spacing + size.width
            row.height = max(row.height, size.height)
            row.indices.append(index)
            rows[rows.count - 1] = row
        }
        return rows
    }
}
