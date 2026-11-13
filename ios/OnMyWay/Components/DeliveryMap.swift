import MapKit
import SwiftUI

/// Real campus map for a delivery: pickup, drop-off, the runner, and the walking path between them.
struct DeliveryMap: View {
    let height: CGFloat
    let pickup: CLLocationCoordinate2D?
    let pickupTitle: String
    let dropoff: CLLocationCoordinate2D?
    let dropoffTitle: String
    var runner: CLLocationCoordinate2D?
    var runnerTitle: String = "Runner"
    /// While heading to the restaurant, the camera frames pickup and the runner. After pickup, drop-off and the runner.
    var headingToPickup = true

    @State private var position: MapCameraPosition = .automatic
    /// Full walking route for this leg. The drawn line is the part still ahead of the runner.
    @State private var fullRoute: [CLLocationCoordinate2D] = []

    var body: some View {
        Map(position: $position) {
            if let pickup {
                Annotation(pickupTitle, coordinate: pickup, anchor: .center) {
                    Image(systemName: "bag.fill")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 28, height: 28)
                        .background(Color.orange, in: .circle)
                        .overlay(Circle().strokeBorder(.white, lineWidth: 2))
                }
            }
            if let dropoff {
                Annotation(dropoffTitle, coordinate: dropoff, anchor: .center) {
                    Image(systemName: "mappin")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(.white)
                        .frame(width: 28, height: 28)
                        .background(Color.green, in: .circle)
                        .overlay(Circle().strokeBorder(.white, lineWidth: 2))
                }
            }
            if let runner {
                Annotation(runnerTitle, coordinate: runner, anchor: .center) {
                    Image(systemName: "figure.walk.circle.fill")
                        .font(.title)
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(.white, Color.brand)
                        .accessibilityLabel(runnerTitle)
                }
            }
            if line.count >= 2 {
                MapPolyline(coordinates: line)
                    .stroke(Color.brand, style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
            }
        }
        .mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll))
        .mapControls {}
        .frame(height: height)
        .onAppear { position = .region(region) }
        .onChange(of: cameraKey) { _, _ in position = .region(region) }
        .onChange(of: fullRoute.count) { _, count in
            if count > 1 { position = .region(region) }
        }
        .task(id: routeRequestKey) {
            guard needsRoute, let start = legStart, let end = legEnd else { return }
            let fetched = await walkingRoute(from: start, to: end)
            if !fetched.isEmpty { fullRoute = fetched }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Map from \(pickupTitle) to \(dropoffTitle)")
    }

    /// With a runner, the path is their live position to the next stop. Before anyone accepts, it joins pickup and drop-off.
    private var legStart: CLLocationCoordinate2D? { runner ?? pickup }
    private var legEnd: CLLocationCoordinate2D? { runner == nil ? dropoff : (headingToPickup ? pickup : dropoff) }

    /// A new route when the next stop changes, or when the runner has left the path by about 45 meters.
    private var needsRoute: Bool {
        guard let start = legStart, let end = legEnd, meters(start, end) >= 25 else { return false }
        guard fullRoute.count >= 2, let last = fullRoute.last else { return true }
        if meters(last, end) > 40 { return true }
        return nearestMeters(start) > 45
    }

    /// Stable while the runner stays on the path, so a location update trims the line without another request.
    private var routeRequestKey: String {
        let end = bucket(legEnd)
        if !needsRoute { return "\(end)|on" }
        return "\(end)|off|\(bucket(legStart))"
    }

    /// Walking path still ahead. It begins at the live position, so it shortens as the runner gets closer.
    private var line: [CLLocationCoordinate2D] {
        guard let start = legStart, let end = legEnd else { return [] }
        guard fullRoute.count >= 2, let last = fullRoute.last, meters(last, end) <= 40 else {
            return [start, end]
        }
        return [start] + Array(fullRoute.suffix(from: nearestIndex(to: start)))
    }

    private func nearestIndex(to point: CLLocationCoordinate2D) -> Int {
        var best = 0
        var bestDistance = Double.greatestFiniteMagnitude
        for (index, coordinate) in fullRoute.enumerated() {
            let distance = meters(point, coordinate)
            if distance < bestDistance {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    private func nearestMeters(_ point: CLLocationCoordinate2D) -> Double {
        fullRoute.map { meters(point, $0) }.min() ?? .greatestFiniteMagnitude
    }

    private func bucket(_ coordinate: CLLocationCoordinate2D?) -> String {
        guard let coordinate else { return "-" }
        return "\(Int(coordinate.latitude * 2_000)):\(Int(coordinate.longitude * 2_000))"
    }

    /// Refit when the stop changes, or when the runner has moved about 30 meters.
    private var cameraKey: String {
        func bucket(_ coordinate: CLLocationCoordinate2D?) -> String {
            guard let coordinate else { return "-" }
            return "\(Int(coordinate.latitude * 3000)):\(Int(coordinate.longitude * 3000))"
        }
        return "\(headingToPickup)|\(bucket(pickup))|\(bucket(dropoff))|\(bucket(runner))"
    }

    private var region: MKCoordinateRegion {
        let focus = line.count >= 2 ? line : [headingToPickup ? pickup : dropoff, runner, pickup, dropoff].compactMap { $0 }
        guard let first = focus.first else {
            return MKCoordinateRegion(
                center: CLLocationCoordinate2D(latitude: 42.1192, longitude: -79.9825),
                span: MKCoordinateSpan(latitudeDelta: 0.012, longitudeDelta: 0.012)
            )
        }
        let lats = focus.map(\.latitude)
        let lngs = focus.map(\.longitude)
        let latPad = max(0.003, (lats.max()! - lats.min()!) * 1.8)
        let lngPad = max(0.003, (lngs.max()! - lngs.min()!) * 1.8)
        return MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: (lats.min()! + lats.max()!) / 2, longitude: (lngs.min()! + lngs.max()!) / 2),
            span: MKCoordinateSpan(latitudeDelta: latPad, longitudeDelta: lngPad)
        )
    }
}

/// Walking directions along campus paths. Empty when the request fails, so the map keeps a straight line.
private func walkingRoute(from start: CLLocationCoordinate2D, to end: CLLocationCoordinate2D) async -> [CLLocationCoordinate2D] {
    let request = MKDirections.Request()
    request.source = MKMapItem(placemark: MKPlacemark(coordinate: start))
    request.destination = MKMapItem(placemark: MKPlacemark(coordinate: end))
    request.transportType = .walking
    do {
        let response = try await MKDirections(request: request).calculate()
        guard let polyline = response.routes.first?.polyline, polyline.pointCount >= 2 else { return [] }
        var coordinates = Array(repeating: kCLLocationCoordinate2DInvalid, count: polyline.pointCount)
        polyline.getCoordinates(&coordinates, range: NSRange(location: 0, length: polyline.pointCount))
        return coordinates
    } catch {
        return []
    }
}

private func meters(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D) -> Double {
    let lat = (a.latitude - b.latitude) * 111_320
    let lng = (a.longitude - b.longitude) * 111_320 * cos((a.latitude + b.latitude) * .pi / 360)
    return hypot(lat, lng)
}

extension Place {
    var coordinate: CLLocationCoordinate2D? {
        guard let lat, let lng else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }
}

extension Destination {
    var coordinate: CLLocationCoordinate2D? {
        guard let lat, let lng else { return nil }
        return CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }
}

extension Runner.Location {
    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: lat, longitude: lng) }
}
