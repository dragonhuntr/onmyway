import CoreLocation

/// Sends the runner's location to the server while a delivery is on screen.
@MainActor
final class LocationReporter: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var lastSent = Date.distantPast
    private var report: ((CLLocationCoordinate2D) -> Void)?
    /// Called with false when location is off, so the delivery screen can offer Settings.
    var onAccess: ((Bool) -> Void)?

    func start(_ report: @escaping (CLLocationCoordinate2D) -> Void) {
        self.report = report
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        manager.distanceFilter = 10
        apply(manager.authorizationStatus)
    }

    private func apply(_ status: CLAuthorizationStatus) {
        switch status {
        case .authorizedAlways, .authorizedWhenInUse:
            onAccess?(true)
            manager.startUpdatingLocation()
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .denied, .restricted:
            onAccess?(false)
        @unknown default:
            break
        }
    }

    func stop() {
        manager.stopUpdatingLocation()
        report = nil
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        MainActor.assumeIsolated { apply(status) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let coordinate = locations.last?.coordinate else { return }
        MainActor.assumeIsolated {
            // Every 5 seconds is enough to show a walker moving on the customer's map.
            guard Date.now.timeIntervalSince(lastSent) > 5 else { return }
            lastSent = .now
            report?(coordinate)
        }
    }
}
