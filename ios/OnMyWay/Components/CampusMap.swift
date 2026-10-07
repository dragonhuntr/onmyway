import SwiftUI

/// Illustrated campus map shared by the finding / tracking / runner screens.
///
/// Layout is authored on the 393pt-wide Figma artboard; overlay items are placed in those
/// design coordinates and scaled horizontally to the actual width.
struct CampusMap<Overlay: View>: View {
    let height: CGFloat
    var route: Route?
    @ViewBuilder var overlay: (MapSpace) -> Overlay

    struct Route {
        let image: ImageResource
        let size: CGSize
    }

    var body: some View {
        GeometryReader { geo in
            let space = MapSpace(scaleX: geo.size.width / MapSpace.designWidth, height: height)
            ZStack(alignment: .topLeading) {
                Color.mapGround
                blocks(space)
                if let route {
                    // Route SVGs carry a 2pt stroke bleed on every side.
                    Image(route.image)
                        .resizable()
                        .frame(width: route.size.width * space.scaleX, height: route.size.height)
                        .offset(x: -2 * space.scaleX, y: -2)
                }
                overlay(space)
            }
        }
        .frame(height: height)
        .clipped()
        .accessibilityElement(children: .contain)
    }

    private func blocks(_ s: MapSpace) -> some View {
        let street1 = height * 0.3, street2 = height * 0.66
        let rows: [(y: CGFloat, h: CGFloat, cols: [(x: CGFloat, w: CGFloat)])] = [
            (20, street1 - 40, [(20, 60), (124, 122), (290, 40)]),
            (street1 + 28, street2 - street1 - 44, [(20, 60), (124, 122), (290, 40)]),
            (street2 + 32, height - street2 - 32, [(124, 122), (290, 40)]),
        ]
        return ZStack(alignment: .topLeading) {
            street(x: 0, y: street1, w: MapSpace.designWidth, h: 12, s)
            street(x: 0, y: street2, w: MapSpace.designWidth, h: 16, s)
            street(x: 96, y: 0, w: 12, h: height, s)
            street(x: 262, y: 0, w: 12, h: height, s)
            street(x: 340, y: 0, w: 8, h: height, s)
            ForEach(rows.indices, id: \.self) { r in
                ForEach(rows[r].cols.indices, id: \.self) { c in
                    let col = rows[r].cols[c]
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color.mapBuilding)
                        .frame(width: col.w * s.scaleX, height: max(rows[r].h, 0))
                        .offset(x: col.x * s.scaleX, y: rows[r].y)
                }
            }
        }
        .accessibilityHidden(true)
    }

    private func street(x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat, _ s: MapSpace) -> some View {
        Rectangle().fill(Color.mapStreet)
            .frame(width: w * s.scaleX, height: h)
            .offset(x: x * s.scaleX, y: y)
    }
}

extension CampusMap where Overlay == EmptyView {
    init(height: CGFloat, route: Route? = nil) {
        self.init(height: height, route: route) { _ in EmptyView() }
    }
}

struct MapSpace {
    static let designWidth: CGFloat = 393
    let scaleX: CGFloat
    let height: CGFloat

    func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * scaleX, y: y) }
}

/// Place a view centred on a design-space point.
struct MapItem<Content: View>: View {
    let space: MapSpace
    let x: CGFloat
    let y: CGFloat
    @ViewBuilder var content: Content

    var body: some View {
        content
            .fixedSize()
            .position(space.point(x, y))
    }
}

/// Circular pin with a label underneath (pickup bag / destination pin).
struct MapPin: View {
    enum Kind { case pickup, dropoff }
    let kind: Kind
    let label: String

    var body: some View {
        ZStack {
            Circle().fill(kind == .pickup ? Color.amber : .brand)
            Circle().strokeBorder(.white, lineWidth: 3)
            Image(kind == .pickup ? .bagWhite : .pinWhite).resizable().frame(width: 14, height: 14)
        }
        .frame(width: 30, height: 30)
        .overlay(alignment: .top) {
            MapLabel(text: label).fixedSize().offset(y: 34)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(kind == .pickup ? "Pickup, \(label)" : "Drop off, \(label)")
    }
}

struct MapLabel: View {
    let text: String
    var filled = false

    var body: some View {
        Text(text)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(filled ? .white : Color.ink)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(filled ? Color.brand : .white, in: .rect(cornerRadius: 8))
            .overlay {
                if !filled { RoundedRectangle(cornerRadius: 8).strokeBorder(Color.hairline) }
            }
    }
}

struct RunnerDot: View {
    var body: some View {
        Image(.runner).resizable().frame(width: 44, height: 44).accessibilityHidden(true)
    }
}
