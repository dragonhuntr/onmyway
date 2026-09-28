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
        TabView(selection: $model.selectedTab) {
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
    }
}

#Preview {
    RootView().environment(AppModel())
}
