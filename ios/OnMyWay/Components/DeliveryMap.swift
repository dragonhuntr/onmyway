import MapKit
import SwiftUI

/// Real campus map for a delivery: pickup, drop-off, and the runner between them.
struct DeliveryMap: View {
    let height: CGFloat
    let pickup: CLLocationCoordinate2D?
    let pickupTitle: String
    let dropoff: CLLocationCoordinate2D?
    let dropoffTitle: String
    var buildings: [Building] = []
    var runner: CLLocationCoordinate2D?
    var runnerTitle: String = "Runner"
    /// While heading to the restaurant, the camera frames pickup and the runner. After pickup, drop-off and the runner.
    var headingToPickup = true

    @State private var position: MapCameraPosition = .automatic

    var body: some View {
        Map(position: $position) {
            if let pickup {
                Marker(pickupTitle, systemImage: "bag.fill", coordinate: pickup)
                    .tint(.orange)
            }
            if let dropoff {
                Marker(dropoffTitle, systemImage: "mappin", coordinate: dropoff)
                    .tint(.green)
            }
            ForEach(buildingLabels) { label in
                Annotation(label.name, coordinate: label.coordinate, anchor: .center) {
                    Text(label.name)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(Color.ink)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(.white, in: .rect(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).strokeBorder(Color.hairline))
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
            if let start = legStart, let end = legEnd {
                MapPolyline(coordinates: [start, end])
                    .stroke(Color.brand, style: StrokeStyle(lineWidth: 4, lineCap: .round, dash: [8, 6]))
            }
        }
        .mapStyle(.standard(elevation: .flat, pointsOfInterest: .excludingAll))
        .mapControls {}
        .frame(height: height)
        .onAppear { position = .region(region) }
        .onChange(of: cameraKey) { _, _ in position = .region(region) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Map from \(pickupTitle) to \(dropoffTitle)")
    }

    /// One chip per spot. The pickup and drop-off pins already name those buildings.
    private var buildingLabels: [BuildingLabel] {
        struct Key: Hashable { let lat: Int; let lng: Int }
        var names: [Key: [String]] = [:]
        var coordinates: [Key: CLLocationCoordinate2D] = [:]
        for building in buildings {
            guard let lat = building.lat, let lng = building.lng else { continue }
            if coveredByPin(lat: lat, lng: lng, name: building.name) { continue }
            let key = Key(lat: Int(lat * 10_000), lng: Int(lng * 10_000))
            names[key, default: []].append(building.name)
            coordinates[key] = CLLocationCoordinate2D(latitude: lat, longitude: lng)
        }
        return names.compactMap { key, titles in
            guard let coordinate = coordinates[key] else { return nil }
            let unique = Array(Set(titles)).sorted()
            return BuildingLabel(id: "\(key.lat),\(key.lng)", name: unique.joined(separator: "\n"), coordinate: coordinate)
        }
    }

    private func coveredByPin(lat: Double, lng: Double, name: String) -> Bool {
        let spots: [(CLLocationCoordinate2D?, String)] = [(pickup, pickupTitle), (dropoff, dropoffTitle)]
        return spots.contains { pin, title in
            guard let pin, title == name else { return false }
            return abs(pin.latitude - lat) < 0.0002 && abs(pin.longitude - lng) < 0.0002
        }
    }

    private var legStart: CLLocationCoordinate2D? { headingToPickup ? runner ?? pickup : runner ?? dropoff }
    private var legEnd: CLLocationCoordinate2D? { headingToPickup ? pickup : dropoff }

    /// Refit when the stop changes, or when the runner has moved about 30 meters.
    private var cameraKey: String {
        func bucket(_ coordinate: CLLocationCoordinate2D?) -> String {
            guard let coordinate else { return "-" }
            return "\(Int(coordinate.latitude * 3000)):\(Int(coordinate.longitude * 3000))"
        }
        return "\(headingToPickup)|\(bucket(pickup))|\(bucket(dropoff))|\(bucket(runner))"
    }

    private var region: MKCoordinateRegion {
        let focus = [headingToPickup ? pickup : dropoff, runner, pickup, dropoff].compactMap { $0 }
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

private struct BuildingLabel: Identifiable {
    let id: String
    let name: String
    let coordinate: CLLocationCoordinate2D
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
