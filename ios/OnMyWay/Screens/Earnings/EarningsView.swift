import SwiftUI
import Charts

/// 10_Earnings — weekly runner earnings, stats, cash out, and recent deliveries.
///
/// Rebuilt from the Figma layer structure (the design-context export for this frame was not
/// available), so colors and icons follow the other screens' tokens.
struct EarningsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL
    @State private var period: EarningsPeriod = .thisWeek
    @State private var isCashingOut = false
    @State private var errorMessage: String?

    private var summary: Earnings? { model.earnings[period] }
    private var days: [DailyEarning] { model.earnings(for: period) }
    private var total: Double { Double(summary?.totalCents ?? 0) / 100 }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    weeklyTotal
                    stats
                    cashOut
                    recent
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
            }
            .background(Color.canvas)
            .navigationTitle("Earnings")
            .refreshable { await load() }
            .task(id: period) { await load() }
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Picker("Period", selection: $period) {
                            ForEach(EarningsPeriod.allCases) { Text($0.rawValue).tag($0) }
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "clock")
                            Text(period.rawValue)
                            Image(systemName: "chevron.down").font(.caption2.weight(.semibold))
                        }
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(Color.ink)
                    }
                }
            }
        }
    }

    private func load() async {
        async let earnings: Void = model.loadEarnings(period)
        async let deliveries: Void = model.loadDeliveries()
        _ = await (earnings, deliveries)
    }

    /// Opens Stripe onboarding the first time; after that, pays the balance out.
    private func cashOut() async {
        isCashingOut = true
        errorMessage = nil
        defer { isCashingOut = false }
        do {
            if let onboarding = try await model.cashOut() {
                openURL(onboarding)
            } else {
                await model.loadEarnings(period)
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// "SEP 7 – SEP 13"
    private var weekLabel: String {
        guard let summary,
              let start = try? Date(summary.start, strategy: .iso8601.year().month().day()),
              let end = try? Date(summary.end, strategy: .iso8601.year().month().day())
        else { return period.rawValue.uppercased() }
        // The server sends plain dates, parsed as UTC midnight; format them in UTC too.
        let style = Date.FormatStyle(timeZone: .gmt).month(.abbreviated).day()
        return "\(start.formatted(style)) – \(end.formatted(style))".uppercased()
    }

    private var weeklyTotal: some View {
        VStack(alignment: .leading, spacing: 14) {
            Eyebrow(weekLabel)
            HStack(alignment: .lastTextBaseline, spacing: 10) {
                Text(total, format: .currency(code: "USD"))
                    .font(.largeTitle.bold())
                    .foregroundStyle(Color.ink)
                    .contentTransition(.numericText())
                if let change = summary?.changePercent {
                    HStack(spacing: 5) {
                        Image(systemName: change >= 0 ? "chart.line.uptrend.xyaxis" : "chart.line.downtrend.xyaxis")
                            .font(.caption2.weight(.semibold))
                        Text("\(change >= 0 ? "+" : "")\(change)% vs week before").font(.caption.weight(.semibold))
                    }
                    .foregroundStyle(Color.brandDeep)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.brandSoft, in: .capsule)
                }
            }
            Chart(days) { day in
                BarMark(
                    x: .value("Day", "\(day.index)"),
                    y: .value("Earned", day.amount),
                    width: 28
                )
                .clipShape(.rect(cornerRadius: 6))
                .foregroundStyle(day.amount > 0 && day.amount == days.map(\.amount).max() ? Color.brand : Color.brandOutline.opacity(0.55))
                .accessibilityLabel(Calendar.current.weekdaySymbols[(day.index + 1) % 7])
                .accessibilityValue(day.amount.formatted(.currency(code: "USD")))
            }
            .chartXAxis {
                AxisMarks { value in
                    AxisValueLabel {
                        if let i = value.as(String.self).flatMap(Int.init) { Text(days[i].day) }
                    }
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(Color.inkSecondary)
                }
            }
            .chartYAxis(.hidden)
            .frame(height: 99)
            .animation(.easeInOut, value: period)
        }
        .card(padding: 18, radius: 18)
    }

    private var stats: some View {
        HStack(spacing: 10) {
            stat("\(summary?.deliveries ?? 0)", "deliveries")
            stat(Duration.seconds((summary?.walkingMinutes ?? 0) * 60).formatted(.units(allowed: [.hours, .minutes], width: .narrow)), "walking")
            stat((summary?.averageCents ?? 0).usd, "avg per trip")
        }
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.title3.bold()).foregroundStyle(Color.ink)
            Text(label).font(.caption).foregroundStyle(Color.inkSecondary)
        }
        .card(padding: 12, radius: 14)
        .accessibilityElement(children: .combine)
    }

    private var cashOut: some View {
        HStack(spacing: 12) {
            Image(.walletAmber).resizable().frame(width: 22, height: 22)
                .frame(width: 44, height: 44)
                .background(Color.amberSoft, in: .rect(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 2) {
                Text("Available to cash out").font(.caption).foregroundStyle(Color.inkSecondary)
                Text(model.cashOutBalanceCents.usd).font(.title3.bold()).foregroundStyle(Color.ink)
                    .contentTransition(.numericText())
                Text(errorMessage ?? (summary?.payoutsEnabled == false ? "Set up payouts with Stripe to cash out" : "To your bank via Stripe · no fee"))
                    .font(.caption2).foregroundStyle(Color.inkSecondary)
            }
            .accessibilityElement(children: .combine)
            Spacer(minLength: 0)
            Button("Cash out") { Task { await cashOut() } }
                .buttonStyle(CompactButtonStyle())
                .disabled(model.cashOutBalanceCents == 0 || isCashingOut)
                .opacity(model.cashOutBalanceCents == 0 ? 0.5 : 1)
        }
        .card(padding: 14)
    }

    private var recent: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Recent deliveries").font(.headline).foregroundStyle(Color.ink)
                Spacer()
            }
            .padding(.top, 4)
            if model.recentDeliveries.isEmpty {
                Text("Deliveries you complete show up here.")
                    .font(.footnote).foregroundStyle(Color.inkSecondary)
            }
            ForEach(model.recentDeliveries) { delivery in
                HStack(spacing: 12) {
                    Image(.footprintsGreen).resizable().frame(width: 20, height: 20)
                        .frame(width: 40, height: 40)
                        .background(Color.brandSoft, in: .rect(cornerRadius: 10))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(delivery.route).font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                        Text(delivery.detail).font(.caption).foregroundStyle(Color.inkSecondary)
                    }
                    Spacer(minLength: 0)
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(delivery.amountCents.usd).font(.subheadline.bold()).foregroundStyle(Color.ink)
                        Text(delivery.rating).font(.caption2).foregroundStyle(Color.inkSecondary)
                    }
                }
                .card(padding: 12)
                .accessibilityElement(children: .combine)
            }
        }
    }
}

#Preview {
    EarningsView().environment(AppModel())
}
