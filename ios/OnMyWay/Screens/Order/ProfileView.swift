import SwiftUI

/// Profile — account details, delivery location, runner ratings, sign out, and deleting the account.
struct ProfileView: View {
    @Environment(AppModel.self) private var model
    @State private var choosingDestination = false
    @State private var confirmingDelete = false
    @State private var deleting = false
    @State private var deleteError: String?
    /// Loaded on appear; the card stays hidden for someone who has never delivered.
    @State private var runnerStats: RunnerStats?

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

                    if let runnerStats, runnerStats.deliveries > 0 {
                        ratingsCard(runnerStats)
                    }

                    Button("Delete Account", role: .destructive) { confirmingDelete = true }
                        .font(.subheadline.weight(.semibold))
                        .disabled(deleting)
                        .padding(.top, 8)
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 20)
            }
            .background(Color.canvas)
            .navigationTitle("Profile")
            .toolbarVisibility(.visible, for: .navigationBar)
            .toolbarVisibility(.hidden, for: .tabBar)
            .task { runnerStats = try? await model.runnerStats() }
            .sheet(isPresented: $choosingDestination) { DestinationPicker() }
            .confirmationDialog("Delete your account?", isPresented: $confirmingDelete, titleVisibility: .visible) {
                Button("Delete Account", role: .destructive) { delete() }
            } message: {
                Text("Your name, email, password and saved places are erased and you’re signed out. This can’t be undone.")
            }
            .alert("Can’t delete yet", isPresented: Binding(get: { deleteError != nil }, set: { if !$0 { deleteError = nil } })) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(deleteError ?? "")
            }
            .footer {
                Button("Log Out") { model.signOut() }
                    .buttonStyle(.primaryCTA)
            }
        }
    }

    private func delete() {
        deleting = true
        Task {
            defer { deleting = false }
            do {
                try await model.deleteAccount()
            } catch {
                deleteError = error.localizedDescription
            }
        }
    }

    private func card(_ title: String, rows: [(String, String)]) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title).font(.headline.bold()).foregroundStyle(Color.brandDeep)
            ForEach(rows, id: \.0) { label, value in row(label, value) }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white, in: .rect(cornerRadius: 20))
        .contentShape(.rect(cornerRadius: 20))
    }

    /// Average stars, counts, and the tags customers picked, for someone who has delivered.
    private func ratingsCard(_ stats: RunnerStats) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Ratings").font(.headline.bold()).foregroundStyle(Color.brandDeep)
            if let rating = stats.rating {
                HStack(spacing: 8) {
                    Image(.star).resizable().frame(width: 24, height: 24)
                    Text(rating.formatted(.number.precision(.fractionLength(1))))
                        .font(.title.bold()).foregroundStyle(Color.ink)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Average \(rating.formatted()) stars")
            } else {
                Text("No ratings yet. They show up here after customers rate your deliveries.")
                    .font(.subheadline).foregroundStyle(Color.inkSecondary)
            }
            row("Ratings", "\(stats.ratings)")
            row("Deliveries", "\(stats.deliveries)")
            let tags = stats.tags.filter { $0.count > 0 }
            if !tags.isEmpty {
                FlowLayout(spacing: 8) {
                    ForEach(tags, id: \.tag) { tag in Pill(text: "\(tag.tag) · \(tag.count)") }
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white, in: .rect(cornerRadius: 20))
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.subheadline).foregroundStyle(Color.inkSecondary)
            Spacer()
            Text(value).font(.subheadline.bold()).foregroundStyle(Color.ink)
                .multilineTextAlignment(.trailing)
        }
        .accessibilityElement(children: .combine)
    }
}
