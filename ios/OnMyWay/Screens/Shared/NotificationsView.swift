import SwiftUI

/// The bell on Home — order updates, newest first. Tapping one opens Orders (customers) or Run (runners).
struct NotificationsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if model.notifications.isEmpty {
                    ContentUnavailableView(
                        "No notifications yet",
                        systemImage: "bell",
                        description: Text("Updates on your orders show up here.")
                    )
                } else {
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(model.notifications) { notification in
                                Button {
                                    dismiss()
                                    model.openFromPush(notification.kind)
                                } label: {
                                    row(notification)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(20)
                    }
                }
            }
            .background(Color.canvas)
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .refreshable { await model.loadNotifications() }
            .task {
                await model.loadNotifications()
                await model.markNotificationsRead()
            }
        }
    }

    private func row(_ notification: AppNotification) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Circle()
                .fill(notification.read ? Color.clear : Color.brand)
                .frame(width: 8, height: 8)
                .padding(.top, 6)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .firstTextBaseline) {
                    Text(notification.title).font(.subheadline.weight(.semibold)).foregroundStyle(Color.ink)
                    Spacer()
                    Text(notification.createdAt, format: .relative(presentation: .named))
                        .font(.caption).foregroundStyle(Color.inkSecondary)
                }
                Text(notification.body).font(.footnote).foregroundStyle(Color.inkSecondary)
                    .multilineTextAlignment(.leading)
            }
        }
        .card(padding: 14)
        .accessibilityElement(children: .combine)
        .accessibilityValue(notification.read ? "" : "Unread")
    }
}

#Preview {
    NotificationsView().environment(AppModel())
}
