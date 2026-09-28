import SwiftUI

struct OrdersTab: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            Group {
                if let order = model.activeOrder, order.runner != nil {
                    OrderTrackingView(order: order)
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
    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL

    private var runner: Runner { order.runner ?? SampleData.wiYa }

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
        .task(id: order.id) {
            // Simulated hand-off until live runner location exists.
            try? await Task.sleep(for: .seconds(12))
            guard !Task.isCancelled else { return }
            model.markDelivered()
        }
    }

    private var map: some View {
        CampusMap(height: 310, route: .init(image: .routeTracking, size: CGSize(width: 282, height: 183.8))) { s in
            MapItem(space: s, x: 34, y: 229.4) { MapPin(kind: .pickup, label: order.restaurant.name) }
            MapItem(space: s, x: 321.5, y: 49.6) { MapPin(kind: .dropoff, label: order.destination.building) }
            MapItem(space: s, x: 204.9, y: 111.6) {
                RunnerDot().accessibilityHidden(false).accessibilityLabel("\(runner.name), \(order.etaMinutes) minutes away")
            }
            MapItem(space: s, x: 204.9, y: 76) { MapLabel(text: "\(runner.name) · \(order.etaMinutes) min", filled: true) }
            MapItem(space: s, x: 355, y: 38) {
                Button(action: { /* TODO: recenter on live location */ }) {
                    Image(.navigation).resizable().frame(width: 20, height: 20)
                        .frame(width: 44, height: 44)
                        .background(.white, in: .circle)
                        .overlay(Circle().strokeBorder(Color.hairline))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Recenter map")
            }
        }
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
                    Text("\(runner.name) is on the way").font(.title3.bold()).foregroundStyle(Color.ink)
                    HStack(spacing: 4) {
                        Image(.star).resizable().frame(width: 14, height: 14)
                        Text("\(runner.rating.formatted()) · \(runner.deliveries) deliveries · \(runner.blurb)")
                            .font(.footnote).foregroundStyle(Color.inkSecondary)
                    }
                }
                Spacer(minLength: 0)
                VStack(spacing: 0) {
                    Text("\(order.etaMinutes) min").font(.callout.bold())
                    Text("ETA").font(.system(size: 10, weight: .semibold)).tracking(0.8)
                }
                .foregroundStyle(Color.brandDeep)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Color.brandSoft, in: .rect(cornerRadius: 12))
                .accessibilityElement(children: .combine)
            }

            HStack(spacing: 10) {
                Image(.footprintsAmber).resizable().frame(width: 18, height: 18)
                Text(runner.routeNote).font(.footnote).foregroundStyle(Color.amberInk)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Color.amberSoft, in: .rect(cornerRadius: 12))

            timeline

            HStack(spacing: 10) {
                Button("Message \(runner.name)") { openURL(URL(string: "sms:\(runner.phone)")!) }
                    .buttonStyle(.secondaryCTA)
                Button("Call") { openURL(URL(string: "tel:\(runner.phone)")!) }
                    .buttonStyle(.outlineCTA)
            }
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
            TimelineStep(title: "Order #\(order.number) sent to \(runner.name)", subtitle: "9:41 AM", state: .done)
            TimelineStep(title: "Picked up at \(order.restaurant.name)", subtitle: "9:47 AM", state: .done)
            TimelineStep(title: "On the way · \(order.destination.building)", subtitle: "Arriving ~9:53", state: .current)
            Button { model.markDelivered() } label: {
                TimelineStep(title: "Delivered", subtitle: nil, state: .upcoming, isLast: true)
            }
            .buttonStyle(.plain)
            .accessibilityHint("Mark as delivered")
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
    @State private var rating = 4
    @State private var tags: Set<String> = ["On time", "Friendly"]
    @State private var extraTip: String?

    private let feedback = ["On time", "Friendly", "Careful with food"]
    private let extraTips = ["+$1", "+$2", "+$3", "Custom"]
    private var runnerName: String { order.runner?.name ?? "your runner" }

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
                    Text("\(runnerName) handed off your order at \(order.destination.building), \(order.destination.room) at 9:52 AM.")
                        .font(.subheadline)
                        .foregroundStyle(Color.inkSecondary)
                        .multilineTextAlignment(.center)
                }

                HStack(spacing: 12) {
                    Image(.dollar).resizable().frame(width: 20, height: 20)
                        .frame(width: 40, height: 40)
                        .background(Color.amber, in: .circle)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("You paid \(order.deliveryFee.usd) for delivery")
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
            Button("Done") { model.finishDelivered(orderAgain: false) }
                .buttonStyle(.primaryCTA)
            Button("Order again") { model.finishDelivered(orderAgain: true) }
                .buttonStyle(.secondaryCTA)
        }
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
                Text("Add to your \(order.tip.rawValue.usd) tip?").font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                Spacer()
                Text("100% to \(runnerName)").font(.caption.weight(.medium)).foregroundStyle(Color.inkSecondary)
            }
            HStack(spacing: 8) {
                ForEach(extraTips, id: \.self) { tip in
                    ChipToggle(title: tip, isSelected: extraTip == tip, fillsWidth: true) {
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
