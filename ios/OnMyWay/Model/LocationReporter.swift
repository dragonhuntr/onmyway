import CoreLocation

/// Sends the runner's location to the server while a delivery is on screen.
@MainActor
final class LocationReporter: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var lastSent = Date.distantPast
    private var report: ((CLLocationCoordinate2D) -> Void)?

    func start(_ report: @escaping (CLLocationCoordinate2D) -> Void) {
        self.report = report
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        manager.distanceFilter = 10
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    func stop() {
        manager.stopUpdatingLocation()
        report = nil
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let coordinate = locations.last?.coordinate else { return }
        MainActor.assumeIsolated {
            // Every 15 seconds is enough for a walking pace.
            guard Date.now.timeIntervalSince(lastSent) > 15 else { return }
            lastSent = .now
            report?(coordinate)
        }
    }
}
