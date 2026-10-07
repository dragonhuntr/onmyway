import SwiftUI
import WebKit

enum OrderStep: Hashable { case enterNumber, findingRunner }

/// Full-screen ordering flow: Transact web ordering → order number → runner matching.
struct OrderFlow: View {
    let restaurant: Restaurant
    @State private var path: [OrderStep] = []

    var body: some View {
        NavigationStack(path: $path) {
            TransactWebView(restaurant: restaurant)
                .navigationDestination(for: OrderStep.self) { step in
                    switch step {
                    case .enterNumber: EnterOrderNumberView(restaurant: restaurant, path: $path)
                    case .findingRunner: FindingRunnerView()
                    }
                }
        }
        .tint(.ink)
    }
}

/// 02_Order_Webview — food is ordered and paid on Transact's own site.
struct TransactWebView: View {
    let restaurant: Restaurant
    @Environment(AppModel.self) private var model
    @State private var isLoading = true

    var body: some View {
        WKWebViewRepresentable(url: restaurant.orderingURL, isLoading: $isLoading)
            .overlay { if isLoading { ProgressView() } }
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(.white, for: .navigationBar)
            .toolbarBackgroundVisibility(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close", systemImage: "xmark") { model.orderingFrom = nil }
                }
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 2) {
                        Text(restaurant.name).font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                        HStack(spacing: 4) {
                            Image(.shieldSmall).resizable().frame(width: 12, height: 12)
                            Text(restaurant.orderingURL.host() ?? "").font(.caption).foregroundStyle(Color.inkSecondary)
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
            }
            .footer {
                HStack {
                    Text("Checked out on Transact?").foregroundStyle(Color.inkSecondary)
                    Spacer()
                    Text("Next: send it to a runner").foregroundStyle(Color.brandDeep)
                }
                .font(.footnote.weight(.medium))
                .padding(.horizontal, 4)

                NavigationLink("Enter order number", value: OrderStep.enterNumber)
                    .buttonStyle(.primaryCTA)
            }
    }
}

/// Minimal WKWebView wrapper that reports loading state.
struct WKWebViewRepresentable: UIViewRepresentable {
    let url: URL
    @Binding var isLoading: Bool

    func makeCoordinator() -> Coordinator { Coordinator(isLoading: $isLoading) }

    func makeUIView(context: Context) -> WKWebView {
        let view = WKWebView()
        view.navigationDelegate = context.coordinator
        view.allowsBackForwardNavigationGestures = true
        view.load(URLRequest(url: url))
        return view
    }

    func updateUIView(_ view: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate {
        @Binding var isLoading: Bool
        init(isLoading: Binding<Bool>) { _isLoading = isLoading }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { isLoading = false }
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) { isLoading = false }
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            isLoading = false
        }
    }
}

/// 03_Enter_Order_Number — hand the paid Transact order to a runner.
struct EnterOrderNumberView: View {
    let restaurant: Restaurant
    @Binding var path: [OrderStep]
    @Environment(AppModel.self) private var model
    @State private var orderNumber = ""
    @State private var nameOnOrder = ""
    @State private var readyAt = Date.now.addingTimeInterval(10 * 60)
    @State private var tip: Tip = .one
    @State private var choosingDestination = false
    @State private var isSubmitting = false
    @State private var errorMessage: String?
    @FocusState private var numberFocused: Bool

    private let deliveryFee = 100
    private var due: Int { deliveryFee + tip.rawValue }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                paidBanner
                orderNumberCard
                deliverToCard
                tipCard
                summaryCard
                paymentCard
            }
            .padding(.horizontal, 20)
            .padding(.top, 4)
            .padding(.bottom, 20)
        }
        .background(Color.canvas)
        .scrollDismissesKeyboard(.interactively)
        .navigationTitle("Send to a runner")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $choosingDestination) { DestinationPicker() }
        .onAppear {
            if nameOnOrder.isEmpty { nameOnOrder = model.session?.shortName ?? "" }
        }
        .footer {
            HStack(spacing: 8) {
                Image(.shield).resizable().frame(width: 16, height: 16)
                Text("Runner ID verified · Every order is protected")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(Color.inkSecondary)
                Spacer()
            }
            .padding(.horizontal, 4)

            if let errorMessage {
                Text(errorMessage)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.inkSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button(isSubmitting ? "Sending…" : "Send to runner · \(due.usd)") {
                Task { await submit() }
            }
            .buttonStyle(.primaryCTA)
            .disabled(orderNumber.trimmingCharacters(in: .whitespaces).isEmpty || isSubmitting)
        }
    }

    /// Creates the order, takes the delivery fee + tip through Stripe, then starts matching.
    private func submit() async {
        guard model.destination != nil else {
            choosingDestination = true
            return
        }
        isSubmitting = true
        errorMessage = nil
        defer { isSubmitting = false }
        do {
            let payment = try await model.createOrder(
                restaurant: restaurant,
                number: orderNumber.trimmingCharacters(in: .whitespaces),
                nameOnOrder: nameOnOrder,
                readyAt: readyAt,
                tip: tip
            )
            if let payment {
                switch await Payments.pay(payment) {
                case .completed:
                    try await model.confirmPayment()
                case .canceled:
                    return
                case .failed(let error):
                    errorMessage = error.localizedDescription
                    return
                }
            }
            path.append(.findingRunner)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private var paidBanner: some View {
        HStack(spacing: 12) {
            Image(.checkWhite).resizable().frame(width: 16, height: 16)
                .frame(width: 32, height: 32)
                .background(Color.brand, in: .circle)
            VStack(alignment: .leading, spacing: 2) {
                Text("Food paid on Transact").font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                Text("Add your order number and we’ll pass it to a runner heading your way.")
                    .font(.footnote).foregroundStyle(Color.inkSecondary)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(Color.brandSoft, in: .rect(cornerRadius: 16))
        .accessibilityElement(children: .combine)
    }

    private var orderNumberCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Eyebrow("TRANSACT ORDER · \(restaurant.name)")
                Spacer()
                Button("Reopen receipt") { path.removeAll() }
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Color.brand)
            }
            HStack(spacing: 6) {
                Text("#").font(.title2.weight(.semibold)).foregroundStyle(Color.inkSecondary)
                TextField("Order number", text: $orderNumber)
                    .font(.title2.bold())
                    .foregroundStyle(Color.ink)
                    .keyboardType(.numberPad)
                    .focused($numberFocused)
                    .tint(.brand)
            }
            .padding(.horizontal, 16)
            .frame(height: 56)
            .background(.white, in: .rect(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(numberFocused ? Color.brand : .hairline, lineWidth: 2))
            .onTapGesture { numberFocused = true }

            Text("It’s at the top of your Transact confirmation screen and email.")
                .font(.footnote).foregroundStyle(Color.inkSecondary)

            HStack(spacing: 10) {
                field("Name on order") {
                    TextField("Name", text: $nameOnOrder)
                        .textContentType(.name)
                }
                field("Ready around") {
                    DatePicker("Ready around", selection: $readyAt, displayedComponents: .hourAndMinute)
                        .labelsHidden()
                        .fixedSize()
                }
            }
        }
        .card()
    }

    private func field<Value: View>(_ label: String, @ViewBuilder value: () -> Value) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption.weight(.medium)).foregroundStyle(Color.inkSecondary)
            value().font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.canvas, in: .rect(cornerRadius: 12))
        .accessibilityElement(children: .combine)
    }

    private var deliverToCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Eyebrow("DELIVER TO")
                Spacer()
                Button(model.destination == nil ? "Choose" : "Change") { choosingDestination = true }
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Color.brand)
            }
            HStack(alignment: .top, spacing: 12) {
                Image(.building).resizable().frame(width: 22, height: 22)
                    .frame(width: 44, height: 44)
                    .background(Color.brandSoft, in: .rect(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 2) {
                    Text(model.destination?.full ?? "Pick a building").font(.callout.weight(.semibold)).foregroundStyle(Color.ink)
                    if let note = model.destination?.note, !note.isEmpty {
                        Text(note).font(.footnote).foregroundStyle(Color.inkSecondary)
                    }
                }
            }
            .accessibilityElement(children: .combine)
        }
        .card()
    }

    private var tipCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Eyebrow("TIP YOUR RUNNER")
            Text("100% goes to the student who walks it over.")
                .font(.footnote).foregroundStyle(Color.inkSecondary)
            HStack(spacing: 8) {
                ForEach(Tip.allCases) { option in
                    ChipToggle(title: option.label, isSelected: tip == option, fillsWidth: true) { tip = option }
                }
            }
        }
        .card()
    }

    private var summaryCard: some View {
        VStack(spacing: 10) {
            summaryRow("Food · paid on Transact") { Text("Paid").foregroundStyle(Color.brand) }
            summaryRow("Delivery fee") { Text(deliveryFee.usd) }
            Text("You save $5.99 vs. a $6.99 fee on commercial delivery apps")
                .font(.caption.weight(.medium))
                .foregroundStyle(Color.amberInk)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.amberSoft, in: .rect(cornerRadius: 8))
            summaryRow("Runner tip") { Text(tip.rawValue.usd) }
            Divider().overlay(Color.hairline)
            HStack {
                Text("Due now").font(.subheadline.weight(.semibold))
                Spacer()
                Text(due.usd).font(.title3.bold())
            }
            .foregroundStyle(Color.ink)
        }
        .card()
    }

    private func summaryRow<Value: View>(_ label: String, @ViewBuilder value: () -> Value) -> some View {
        HStack {
            Text(label).foregroundStyle(Color.inkSecondary)
            Spacer()
            value().fontWeight(.medium).foregroundStyle(Color.ink)
        }
        .font(.subheadline)
    }

    private var paymentCard: some View {
        HStack(spacing: 12) {
            Image(.walletAmber).resizable().frame(width: 20, height: 20)
                .frame(width: 40, height: 40)
                .background(Color.amberSoft, in: .rect(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 2) {
                Text("Card · chosen at checkout").font(.subheadline.weight(.medium)).foregroundStyle(Color.ink)
                Text("Held now, charged when it’s delivered. Delivery + tip only.")
                    .font(.footnote).foregroundStyle(Color.inkSecondary)
            }
            Spacer()
        }
        .card(padding: 14)
        .accessibilityElement(children: .combine)
    }
}

/// 04_Finding_Runner — match with a student already walking this way.
struct FindingRunnerView: View {
    @Environment(AppModel.self) private var model
    @State private var progress = 0.1

    var body: some View {
        ScrollView {
            CampusMap(height: 360, route: .init(image: .routeFinding, size: CGSize(width: 282, height: 212.8))) { s in
                MapItem(space: s, x: 34, y: 266.4) { MapPin(kind: .pickup, label: model.activeOrder?.restaurant.name ?? "") }
                MapItem(space: s, x: 321.5, y: 57.6) { MapPin(kind: .dropoff, label: building) }
                MapItem(space: s, x: 150, y: 150) { RunnerDot() }
                MapItem(space: s, x: 300, y: 250) { RunnerDot() }
                MapItem(space: s, x: 200, y: 300) { RunnerDot() }
            }

            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(.loadingDots).resizable().frame(width: 26, height: 6).accessibilityHidden(true)
                    Text("Matching…").font(.footnote.weight(.semibold)).foregroundStyle(Color.brandDeep)
                }
                .padding(.leading, 10)
                .padding(.trailing, 12)
                .padding(.vertical, 6)
                .background(Color.brandSoft, in: .capsule)

                Text("Looking for a runner on their way to \(building)")
                    .font(.title3.bold())
                    .foregroundStyle(Color.ink)

                ProgressView(value: progress)
                    .tint(.brand)
                    .scaleEffect(y: 1.5)
                    .accessibilityLabel("Matching progress")

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        infoPill(.footprints, model.runnersHeadingYourWay == 1
                                 ? "1 runner heading your way"
                                 : "\(model.runnersHeadingYourWay) runners heading your way")
                        infoPill(.clock, "Usually under 5 min")
                    }
                }

                if let order = model.activeOrder {
                    HStack(spacing: 12) {
                        Image(.receiptAmber).resizable().frame(width: 20, height: 20)
                            .frame(width: 40, height: 40)
                            .background(Color.amberSoft, in: .rect(cornerRadius: 10))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Order #\(order.number) · \(order.restaurant.name)")
                                .font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                            Text("\(order.nameOnOrder) · Ready at ~\(order.readyAt?.shortTime ?? "soon")")
                                .font(.footnote).foregroundStyle(Color.inkSecondary)
                        }
                    }
                    .card(padding: 14, radius: 14)
                    .accessibilityElement(children: .combine)
                }
            }
            .padding(20)
        }
        .background(Color.canvas)
        .navigationTitle("Finding your runner")
        .navigationBarTitleDisplayMode(.inline)
        .footer {
            Button("Cancel order") { Task { await model.cancelActiveOrder() } }
                .buttonStyle(.outlineCTA)
        }
        .task {
            // The model polls the order and closes this flow once a runner accepts.
            // The bar only shows that matching is under way; most matches take under 5 minutes.
            withAnimation(.easeOut(duration: 300)) { progress = 0.95 }
        }
    }

    private var building: String {
        model.activeOrder?.destination.building ?? model.destination?.building ?? "your building"
    }

    private func infoPill(_ icon: ImageResource, _ text: String) -> some View {
        HStack(spacing: 6) {
            Image(icon).resizable().frame(width: 16, height: 16)
            Text(text).font(.footnote.weight(.semibold)).foregroundStyle(Color.ink)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(.white, in: .rect(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Color.hairline))
    }
}
