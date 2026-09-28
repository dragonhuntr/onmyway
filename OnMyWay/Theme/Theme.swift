import SwiftUI

// Color tokens from the On My Way Figma file.
extension Color {
    static let ink = Color(hex: 0x14201A)
    static let inkSecondary = Color(hex: 0x5A6660)
    static let hairline = Color(hex: 0xDCE3DE)
    static let canvas = Color(hex: 0xF4F6F3)

    static let brand = Color(hex: 0x1F6B4A)
    static let brandDeep = Color(hex: 0x144A33)
    static let brandMid = Color(hex: 0x2E7F5C)
    static let brandSoft = Color(hex: 0xE1F0E7)
    static let brandOutline = Color(hex: 0x5DA283)
    static let onBrand = Color(hex: 0xD6EADF)
    static let onBrandMuted = Color(hex: 0xBFE0CC)

    static let amber = Color(hex: 0xF5B317)
    static let amberSoft = Color(hex: 0xFFF1CC)
    static let amberInk = Color(hex: 0x5C4300)
    static let amberInkDeep = Color(hex: 0x3D2C00)

    static let mapGround = Color(hex: 0xE6EEE8)
    static let mapStreet = Color(hex: 0xF7FAF7)
    static let mapBuilding = Color(hex: 0xD4E1D7)

    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

// MARK: - Buttons

/// 52pt full-width CTA. Variants match the Figma "Button" component: Primary, Secondary, Outline.
struct CTAButtonStyle: ButtonStyle {
    enum Kind { case primary, secondary, outline }
    var kind: Kind = .primary
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.callout.weight(.semibold))
            .foregroundStyle(foreground)
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(background, in: .rect(cornerRadius: 14))
            .overlay {
                if kind == .outline {
                    RoundedRectangle(cornerRadius: 14).strokeBorder(Color.hairline, lineWidth: 1.5)
                }
            }
            .opacity(isEnabled ? (configuration.isPressed ? 0.85 : 1) : 0.5)
            .contentShape(.rect(cornerRadius: 14))
    }

    private var foreground: Color {
        switch kind {
        case .primary: .white
        case .secondary: .brandDeep
        case .outline: .ink
        }
    }

    private var background: Color {
        switch kind {
        case .primary: .brand
        case .secondary: .brandSoft
        case .outline: .white
        }
    }
}

extension ButtonStyle where Self == CTAButtonStyle {
    static var primaryCTA: CTAButtonStyle { CTAButtonStyle(kind: .primary) }
    static var secondaryCTA: CTAButtonStyle { CTAButtonStyle(kind: .secondary) }
    static var outlineCTA: CTAButtonStyle { CTAButtonStyle(kind: .outline) }
}

/// Compact filled button used inside cards ("Accept", "Cash out").
struct CompactButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
            .background(Color.brand, in: .rect(cornerRadius: 10))
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

// MARK: - Surfaces

extension View {
    /// White rounded card with a hairline border.
    func card(padding: CGFloat = 16, radius: CGFloat = 16) -> some View {
        self
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.white, in: .rect(cornerRadius: radius))
            .overlay(RoundedRectangle(cornerRadius: radius).strokeBorder(Color.hairline))
    }

    /// Bottom-pinned action area with a white background and top hairline.
    func footer<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 10, content: content)
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 8)
                .frame(maxWidth: .infinity)
                .background(.white)
                .overlay(alignment: .top) { Rectangle().fill(Color.hairline).frame(height: 1) }
        }
    }
}

/// Small tracked label used as a card header ("DELIVER TO").
struct Eyebrow: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .tracking(0.72)
            .foregroundStyle(Color.inkSecondary)
    }
}

/// Selectable capsule chip (tips, filters, feedback tags).
struct ChipToggle: View {
    let title: String
    let isSelected: Bool
    var fillsWidth = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.footnote.weight(.medium))
                .foregroundStyle(isSelected ? .white : Color.ink)
                .padding(.horizontal, 14)
                .frame(maxWidth: fillsWidth ? .infinity : nil, minHeight: fillsWidth ? 40 : 34)
                .background(isSelected ? Color.brand : .white, in: .capsule)
                .overlay(Capsule().strokeBorder(isSelected ? Color.brand : .hairline))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Rounded info pill with optional leading icon ("+2 min detour").
struct Pill: View {
    let text: String
    var icon: ImageResource?
    var foreground: Color = .brandDeep
    var background: Color = .brandSoft

    var body: some View {
        HStack(spacing: 5) {
            if let icon { Image(icon).resizable().frame(width: 14, height: 14) }
            Text(text).font(.caption.weight(.semibold))
        }
        .foregroundStyle(foreground)
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(background, in: .rect(cornerRadius: 8))
    }
}

extension Decimal {
    var usd: String { formatted(.currency(code: "USD")) }
}
