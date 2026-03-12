import SwiftUI

// MARK: - Font Styles

extension Font {
    static let ssdidTitle = Font.system(size: 28, weight: .bold)
    static let ssdidHeadline = Font.system(size: 20, weight: .semibold)
    static let ssdidBody = Font.system(size: 16, weight: .regular)
    static let ssdidCaption = Font.system(size: 12, weight: .medium)
    static let ssdidMono = Font.system(size: 14, weight: .regular, design: .monospaced)
}

// MARK: - App Theme Modifier

struct SsdidTheme: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(Color.bgPrimary)
            .preferredColorScheme(.dark)
    }
}

extension View {
    func ssdidTheme() -> some View {
        modifier(SsdidTheme())
    }
}

// MARK: - Card Style Modifier

struct SsdidCardStyle: ViewModifier {
    var hasBorder: Bool = false

    func body(content: Content) -> some View {
        content
            .padding(16)
            .background(Color.bgCard)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(hasBorder ? Color.borderStrong : Color.clear, lineWidth: 1)
            )
    }
}

extension View {
    func ssdidCard(border: Bool = false) -> some View {
        modifier(SsdidCardStyle(hasBorder: border))
    }
}

// MARK: - Button Styles

struct SsdidPrimaryButtonStyle: ButtonStyle {
    var isEnabled: Bool = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.ssdidBody.weight(.semibold))
            .foregroundStyle(isEnabled ? Color.white : Color.textTertiary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(isEnabled ? Color.ssdidAccent : Color.bgElevated)
            .cornerRadius(12)
            .opacity(configuration.isPressed ? 0.8 : 1.0)
    }
}

struct SsdidSecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.ssdidBody.weight(.semibold))
            .foregroundStyle(Color.ssdidAccent)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(Color.accentDim)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.ssdidAccent.opacity(0.3), lineWidth: 1)
            )
            .opacity(configuration.isPressed ? 0.8 : 1.0)
    }
}

struct SsdidDangerButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.ssdidBody.weight(.semibold))
            .foregroundStyle(Color.danger)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(Color.dangerDim)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.danger.opacity(0.3), lineWidth: 1)
            )
            .opacity(configuration.isPressed ? 0.8 : 1.0)
    }
}

extension ButtonStyle where Self == SsdidPrimaryButtonStyle {
    static var ssdidPrimary: SsdidPrimaryButtonStyle { SsdidPrimaryButtonStyle() }

    static func ssdidPrimary(enabled: Bool) -> SsdidPrimaryButtonStyle {
        SsdidPrimaryButtonStyle(isEnabled: enabled)
    }
}

extension ButtonStyle where Self == SsdidSecondaryButtonStyle {
    static var ssdidSecondary: SsdidSecondaryButtonStyle { SsdidSecondaryButtonStyle() }
}

extension ButtonStyle where Self == SsdidDangerButtonStyle {
    static var ssdidDanger: SsdidDangerButtonStyle { SsdidDangerButtonStyle() }
}
