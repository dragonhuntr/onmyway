import UIKit
@preconcurrency import StripePaymentSheet

enum Payments {
    /// Presents Stripe's PaymentSheet over the current screen and waits for the result.
    @MainActor
    static func pay(_ config: PaymentSheetConfig) async -> PaymentSheetResult {
        STPAPIClient.shared.publishableKey = config.publishableKey
        var configuration = PaymentSheet.Configuration()
        configuration.merchantDisplayName = "On My Way"
        configuration.customer = .init(id: config.customerId, ephemeralKeySecret: config.customerEphemeralKeySecret)
        let sheet = PaymentSheet(paymentIntentClientSecret: config.paymentIntentClientSecret, configuration: configuration)

        guard let presenter = topViewController() else {
            return .failed(error: APIError(message: "Couldn’t open checkout. Try again."))
        }
        return await withCheckedContinuation { continuation in
            sheet.present(from: presenter) { continuation.resume(returning: $0) }
        }
    }

    @MainActor
    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
        var controller = window?.rootViewController
        while let presented = controller?.presentedViewController { controller = presented }
        return controller
    }
}
