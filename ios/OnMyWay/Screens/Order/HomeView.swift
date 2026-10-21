import SwiftUI

/// 01_Home — pick a dining hall; students already walking your way deliver it for $1.
struct HomeView: View {
    @Environment(AppModel.self) private var model
    @State private var query = ""
    @State private var choosingDestination = false
    @State private var showingNotifications = false

    private var results: [Restaurant] {
        guard !query.isEmpty else { return model.restaurants }
        return model.restaurants.filter { $0.name.localizedStandardContains(query) }
    }

    var body: some View {
        NavigationStack {
            ScrollViewReader { scroller in
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        header
                        searchField
                        if query.isEmpty { runnerBanner }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)

                    if results.isEmpty {
                        ContentUnavailableView.search(text: query).padding(.top, 40)
                    } else {
                        if results.contains(where: { $0.hours.isOpen() }) {
                            openNow { withAnimation { scroller.scrollTo(allDiningID, anchor: .top) } }
                        }
                        popular.id(allDiningID)
                    }
                }
            }
            .background(Color.canvas)
            .toolbarVisibility(.hidden, for: .navigationBar)
            .refreshable { await reload() }
            .task { await reload() }
            .sheet(isPresented: $choosingDestination) { DestinationPicker() }
            .sheet(isPresented: $showingNotifications) { NotificationsView() }
        }
    }

    private func reload() async {
        async let home: Void = model.loadHome()
        async let inbox: Void = model.loadNotifications()
        _ = await (home, inbox)
    }

    private var header: some View {
        HStack {
            Button { choosingDestination = true } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Deliver to")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(Color.inkSecondary)
                    HStack(spacing: 6) {
                        Image(.pinLocation).resizable().frame(width: 18, height: 18)
                        Text(model.destination?.short ?? "Choose a building")
                            .font(.callout.weight(.semibold))
                            .foregroundStyle(Color.ink)
                        Image(.chevronRight).resizable().frame(width: 16, height: 16)
                    }
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Deliver to \(model.destination?.short ?? "no building chosen")")
            .accessibilityHint("Change delivery location")

            Spacer()

            Button { showingNotifications = true } label: {
                Image(.bell).resizable().frame(width: 22, height: 22)
                    .frame(width: 44, height: 44)
                    .background(.white, in: .circle)
                    .overlay(Circle().strokeBorder(Color.hairline))
                    .overlay(alignment: .topTrailing) {
                        if model.unreadNotifications > 0 {
                            Circle().fill(Color.amber)
                                .frame(width: 12, height: 12)
                                .overlay(Circle().strokeBorder(.white, lineWidth: 2))
                                .offset(x: -2, y: 2)
                        }
                    }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Notifications")
            .accessibilityValue(model.unreadNotifications > 0 ? "\(model.unreadNotifications) unread" : "")

            NavigationLink {
                ProfileView()
            } label: {
                Text(model.session?.firstName.prefix(1).uppercased() ?? "")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(Color.brandDeep)
                    .frame(width: 44, height: 44)
                    .background(Color.amberSoft, in: .circle)
            }
            .accessibilityLabel("Profile")
        }
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(.search).resizable().frame(width: 20, height: 20).accessibilityHidden(true)
            TextField("Search dining halls & cafés", text: $query)
                .font(.subheadline)
                .foregroundStyle(Color.ink)
                .submitLabel(.search)
        }
        .padding(.horizontal, 16)
        .frame(height: 50)
        .background(.white, in: .rect(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.hairline))
    }

    private var runnerBanner: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text("\(model.runnersHeadingYourWay) runners heading your way")
                    .font(.title3.bold())
                    .foregroundStyle(.white)
                Text("Students already walking to \(model.destination?.building ?? "your building") can bring your order in ~10 min for a $1 fee.")
                    .font(.footnote)
                    .foregroundStyle(Color.onBrand)
            }
            Spacer(minLength: 0)
            Image(.footprintsWhite).resizable().frame(width: 28, height: 28)
                .frame(width: 56, height: 56)
                .background(Color.brandMid, in: .circle)
                .accessibilityHidden(true)
        }
        .padding(18)
        .background(Color.brand, in: .rect(cornerRadius: 18))
        .accessibilityElement(children: .combine)
    }

    private let allDiningID = "allDining"

    /// `seeAll` scrolls down to the full list.
    private func openNow(seeAll: @escaping () -> Void) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Open now on campus").font(.headline).foregroundStyle(Color.ink)
                Spacer()
                Button("See all", action: seeAll)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Color.brand)
            }
            .padding(.horizontal, 20)

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 12) {
                    ForEach(results.filter { $0.hours.isOpen() }) { restaurant in
                        Button { model.orderingFrom = restaurant } label: {
                            RestaurantCard(restaurant: restaurant)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 20)
            }
        }
        .padding(.top, 16)
    }

    private var popular: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("All dining").font(.headline).foregroundStyle(Color.ink)
            ForEach(results) { restaurant in
                Button { model.orderingFrom = restaurant } label: {
                    RestaurantRow(restaurant: restaurant)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(20)
    }
}

struct FoodTile: View {
    let restaurant: Restaurant
    let size: CGSize

    var body: some View {
        Image(restaurant.logo)
            .resizable()
            .scaledToFit()
            .frame(width: size.width, height: size.height)
            .background(restaurant.tile)
            .clipShape(.rect(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.hairline))
            .accessibilityHidden(true)
    }
}

struct RestaurantCard: View {
    let restaurant: Restaurant

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            FoodTile(restaurant: restaurant, size: CGSize(width: 148, height: 104))
            VStack(alignment: .leading, spacing: 3) {
                Text(restaurant.name).font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                Text("$1 delivery · \(restaurant.eta)").font(.caption).foregroundStyle(Color.inkSecondary)
                if let building = restaurant.building {
                    Text(building.name).font(.caption.weight(.medium)).foregroundStyle(Color.ink)
                }
            }
        }
        .padding(10)
        .frame(width: 168, alignment: .leading)
        .background(.white, in: .rect(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.hairline))
        .accessibilityElement(children: .combine)
    }
}

struct RestaurantRow: View {
    let restaurant: Restaurant

    var body: some View {
        HStack(spacing: 12) {
            FoodTile(restaurant: restaurant, size: CGSize(width: 60, height: 60))
            VStack(alignment: .leading, spacing: 3) {
                Text(restaurant.name).font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                Text("\(restaurant.distance) · \(restaurant.eta)").font(.caption).foregroundStyle(Color.inkSecondary)
                let isOpen = restaurant.hours.isOpen()
                Text(restaurant.hours.status())
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(isOpen ? Color.brandDeep : Color.inkSecondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(isOpen ? Color.brandSoft : Color.hairline, in: .rect(cornerRadius: 6))
            }
            Spacer(minLength: 0)
            Image(.chevronRight).resizable().frame(width: 20, height: 20).accessibilityHidden(true)
        }
        .card(padding: 12)
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    HomeView().environment(AppModel())
}
