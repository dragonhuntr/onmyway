import SwiftUI

@main
struct OnMyWayApp: App {
    @State private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .preferredColorScheme(.light)
        }
    }
}

struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        if model.isRestoringSession {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.canvas)
        } else if model.session == nil {
            LoginView()
        } else {
            mainTabs
        }
    }

    private var mainTabs: some View {
        @Bindable var model = model
        return TabView(selection: $model.selectedTab) {
            Tab("Home", systemImage: "house", value: AppTab.home) {
                HomeView()
            }
            Tab("Orders", systemImage: "receipt", value: AppTab.orders) {
                OrdersTab()
            }
            Tab("Run", systemImage: "bolt", value: AppTab.run) {
                RunnerHomeView()
            }
            Tab("Earnings", systemImage: "wallet.bifold", value: AppTab.earnings) {
                EarningsView()
            }
        }
        .fullScreenCover(item: $model.orderingFrom) { restaurant in
            OrderFlow(restaurant: restaurant)
                .environment(model)
        }
        .alert(
            "Order closed",
            isPresented: Binding(get: { model.notice != nil }, set: { if !$0 { model.notice = nil } }),
            presenting: model.notice
        ) { _ in
            Button("OK") {}
        } message: { notice in
            Text(notice)
        }
    }
}

#Preview {
    RootView().environment(AppModel())
}
