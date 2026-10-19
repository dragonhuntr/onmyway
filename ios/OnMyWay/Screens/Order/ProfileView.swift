import SwiftUI

/// Profile — account details, delivery location, and sign out.
struct ProfileView: View {
    @Environment(AppModel.self) private var model
    @State private var choosingDestination = false

    var body: some View {
        if let session = model.session {
            ScrollView {
                VStack(spacing: 16) {
                    VStack(spacing: 4) {
                        Text(session.firstName.prefix(1).uppercased())
                            .font(.largeTitle.bold())
                            .foregroundStyle(Color.brandDeep)
                            .frame(width: 96, height: 96)
                            .background(Color.amberSoft, in: .circle)
                            .padding(.bottom, 10)
                            .accessibilityHidden(true)
                        Text("\(session.firstName) \(session.lastName)")
                            .font(.title.bold()).foregroundStyle(Color.ink)
                        Text("@\(session.username)")
                            .font(.subheadline).foregroundStyle(Color.inkSecondary)
                    }
                    .padding(.bottom, 4)

                    card("Account", rows: [
                        ("Name", "\(session.firstName) \(session.lastName)"),
                        ("Username", "@\(session.username)"),
                        ("Email", session.email),
                    ])

                    Button { choosingDestination = true } label: {
                        card("Delivery", rows: [("Deliver to", model.destination?.full ?? "Not set")])
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("Change delivery location")
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 20)
            }
            .background(Color.canvas)
            .navigationTitle("Profile")
            .toolbarVisibility(.visible, for: .navigationBar)
            .toolbarVisibility(.hidden, for: .tabBar)
            .sheet(isPresented: $choosingDestination) { DestinationPicker() }
            .footer {
                Button("Log Out") { model.signOut() }
                    .buttonStyle(.primaryCTA)
            }
        }
    }

    private func card(_ title: String, rows: [(String, String)]) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title).font(.headline.bold()).foregroundStyle(Color.brandDeep)
            ForEach(rows, id: \.0) { label, value in
                HStack {
                    Text(label).font(.subheadline).foregroundStyle(Color.inkSecondary)
                    Spacer()
                    Text(value).font(.subheadline.bold()).foregroundStyle(Color.ink)
                        .multilineTextAlignment(.trailing)
                }
                .accessibilityElement(children: .combine)
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white, in: .rect(cornerRadius: 20))
        .contentShape(.rect(cornerRadius: 20))
    }
}
