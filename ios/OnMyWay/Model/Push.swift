import SwiftUI
import UserNotifications

/// Push notifications through APNs. They need the Push Notifications capability, which takes a paid
/// Apple Developer account, plus the APNs key on the Worker. Until then registration fails quietly.
@MainActor
final class PushDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    /// Set once the app's model exists, so tokens and taps reach it.
    weak var model: AppModel? {
        didSet {
            if let pendingKind, model != nil {
                self.pendingKind = nil
                model?.openFromPush(pendingKind)
            }
        }
    }
    /// A notification tapped before the model was set.
    private var pendingKind: String?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        Task { await model?.registerDevice(token) }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: any Error) {
        print("Push registration failed: \(error.localizedDescription)")
    }

    /// Shows banners while the app is open too.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let kind = response.notification.request.content.userInfo["kind"] as? String
        await MainActor.run {
            if let model { model.openFromPush(kind) } else { pendingKind = kind }
        }
    }
}
