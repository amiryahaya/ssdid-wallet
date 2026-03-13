import SwiftUI

struct SsdidButton: View {
    enum Style {
        case primary
        case secondary
        case danger
    }

    let title: String
    var style: Style = .primary
    var isEnabled: Bool = true
    var isLoading: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Group {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: tintColor))
                } else {
                    Text(title)
                }
            }
        }
        .buttonStyle(buttonStyle)
        .disabled(!isEnabled || isLoading)
    }

    private var tintColor: Color {
        switch style {
        case .primary: return .white
        case .secondary: return .ssdidAccent
        case .danger: return .danger
        }
    }

    private var buttonStyle: some ButtonStyle {
        switch style {
        case .primary:
            return AnyButtonStyle(SsdidPrimaryButtonStyle(isEnabled: isEnabled))
        case .secondary:
            return AnyButtonStyle(SsdidSecondaryButtonStyle())
        case .danger:
            return AnyButtonStyle(SsdidDangerButtonStyle())
        }
    }
}

// MARK: - Type-Erased ButtonStyle

private struct AnyButtonStyle: ButtonStyle {
    private let _makeBody: (Configuration) -> AnyView

    init<S: ButtonStyle>(_ style: S) {
        _makeBody = { configuration in
            AnyView(style.makeBody(configuration: configuration))
        }
    }

    func makeBody(configuration: Configuration) -> some View {
        _makeBody(configuration)
    }
}

#Preview {
    VStack(spacing: 16) {
        SsdidButton(title: "Primary Action", style: .primary) {}
        SsdidButton(title: "Secondary Action", style: .secondary) {}
        SsdidButton(title: "Delete", style: .danger) {}
        SsdidButton(title: "Disabled", style: .primary, isEnabled: false) {}
        SsdidButton(title: "Loading...", style: .primary, isLoading: true) {}
    }
    .padding()
    .background(Color.bgPrimary)
}
