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
    /// The stop `fullRoute` was requested for. The path usually ends on a road, not on the building pin.
    @State private var routedTo: CLLocationCoordinate2D?

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
            if !fetched.isEmpty {
                fullRoute = fetched
                routedTo = end
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Map from \(pickupTitle) to \(dropoffTitle)")
    }

    /// With a runner, the path is their live position to the next stop. Before anyone accepts, it joins pickup and drop-off.
    private var legStart: CLLocationCoordinate2D? { runner ?? pickup }
    private var legEnd: CLLocationCoordinate2D? { runner == nil ? dropoff : (headingToPickup ? pickup : dropoff) }

    /// A new route when the next stop changes, or when the runner has left the path by about 80 meters.
    private var needsRoute: Bool {
        guard let start = legStart, let end = legEnd, meters(start, end) >= 25 else { return false }
        guard fullRoute.count >= 2, let routedTo else { return true }
        if meters(routedTo, end) > 30 { return true }
        return closestProgress(to: start).distance > 80
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
        guard fullRoute.count >= 2, let routedTo, meters(routedTo, end) <= 30 else {
            return [start, end]
        }
        let progress = closestProgress(to: start)
        var points = [start, progress.point] + Array(fullRoute.suffix(from: progress.index + 1))
        if let last = points.last, meters(last, end) > 12 { points.append(end) }
        return points
    }

    /// Closest place on the path itself, including the stretch between two corners.
    private func closestProgress(to point: CLLocationCoordinate2D) -> (index: Int, point: CLLocationCoordinate2D, distance: Double) {
        guard fullRoute.count >= 2 else { return (0, point, .greatestFiniteMagnitude) }
        var bestIndex = 0
        var bestPoint = fullRoute[0]
        var bestDistance = Double.greatestFiniteMagnitude
        for index in 0..<(fullRoute.count - 1) {
            let projected = closestPoint(on: fullRoute[index], fullRoute[index + 1], to: point)
            let distance = meters(point, projected)
            if distance < bestDistance {
                bestDistance = distance
                bestIndex = index
                bestPoint = projected
            }
        }
        return (bestIndex, bestPoint, bestDistance)
    }

    /// Projects onto the segment in meters, so a north-south stretch is not favored over an east-west one.
    private func closestPoint(on a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D, to p: CLLocationCoordinate2D) -> CLLocationCoordinate2D {
        let cosLat = cos((a.latitude + b.latitude) * .pi / 360)
        let abx = b.latitude - a.latitude
        let aby = (b.longitude - a.longitude) * cosLat
        let apx = p.latitude - a.latitude
        let apy = (p.longitude - a.longitude) * cosLat
        let ab2 = abx * abx + aby * aby
        let t = ab2 == 0 ? 0 : min(1, max(0, (apx * abx + apy * aby) / ab2))
        return CLLocationCoordinate2D(
            latitude: a.latitude + (b.latitude - a.latitude) * t,
            longitude: a.longitude + (b.longitude - a.longitude) * t
        )
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
        guard !focus.isEmpty else {
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
func walkingRoute(from start: CLLocationCoordinate2D, to end: CLLocationCoordinate2D) async -> [CLLocationCoordinate2D] {
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

func meters(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D) -> Double {
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
