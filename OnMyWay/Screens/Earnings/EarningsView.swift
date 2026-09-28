import SwiftUI
import Charts

/// 10_Earnings — weekly runner earnings, stats, cash out, and recent deliveries.
///
/// Rebuilt from the Figma layer structure (the design-context export for this frame was not
/// available), so colors and icons follow the other screens' tokens.
struct EarningsView: View {
    @Environment(AppModel.self) private var model
    @State private var period: EarningsPeriod = .thisWeek

    private var days: [DailyEarning] { model.earnings(for: period) }
    private var total: Double { days.map(\.amount).reduce(0, +) }

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

    private var weeklyTotal: some View {
        VStack(alignment: .leading, spacing: 14) {
            Eyebrow(period == .thisWeek ? "SEP 7 – SEP 13" : "AUG 31 – SEP 6")
            HStack(alignment: .lastTextBaseline, spacing: 10) {
                Text(total, format: .currency(code: "USD"))
                    .font(.largeTitle.bold())
                    .foregroundStyle(Color.ink)
                    .contentTransition(.numericText())
                if period == .thisWeek {
                    HStack(spacing: 5) {
                        Image(systemName: "chart.line.uptrend.xyaxis").font(.caption2.weight(.semibold))
                        Text("+18% vs last week").font(.caption.weight(.semibold))
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
                .foregroundStyle(day.amount == days.map(\.amount).max() ? Color.brand : Color.brandOutline.opacity(0.55))
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
            stat(period == .thisWeek ? "24" : "20", "deliveries")
            stat(period == .thisWeek ? "1h 40m" : "1h 22m", "walking")
            stat((total / (period == .thisWeek ? 24 : 20)).formatted(.currency(code: "USD")), "avg per trip")
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
                Text(model.cashOutBalance.usd).font(.title3.bold()).foregroundStyle(Color.ink)
                    .contentTransition(.numericText())
                Text("Instant to Bank Account · no fee").font(.caption2).foregroundStyle(Color.inkSecondary)
            }
            .accessibilityElement(children: .combine)
            Spacer(minLength: 0)
            Button("Cash out") { withAnimation { model.cashOut() } }
                .buttonStyle(CompactButtonStyle())
                .disabled(model.cashOutBalance == 0)
                .opacity(model.cashOutBalance == 0 ? 0.5 : 1)
        }
        .card(padding: 14)
    }

    private var recent: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Recent deliveries").font(.headline).foregroundStyle(Color.ink)
                Spacer()
                Button("See all", action: { /* TODO: delivery history */ })
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Color.brand)
            }
            .padding(.top, 4)
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
                        Text(delivery.amount.usd).font(.subheadline.bold()).foregroundStyle(Color.ink)
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
