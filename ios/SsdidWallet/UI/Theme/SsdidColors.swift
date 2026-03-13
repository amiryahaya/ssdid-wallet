import SwiftUI

extension Color {

    // MARK: - Backgrounds

    static let bgPrimary = Color(hex: 0x0A0C10)
    static let bgSecondary = Color(hex: 0x12151C)
    static let bgTertiary = Color(hex: 0x1A1E28)
    static let bgCard = Color(hex: 0x161A24)
    static let bgElevated = Color(hex: 0x1E2230)

    // MARK: - Text

    static let textPrimary = Color(hex: 0xE8EAF0)
    static let textSecondary = Color(hex: 0x8A8F9E)
    static let textTertiary = Color(hex: 0x5A5F6E)

    // MARK: - Accent

    static let ssdidAccent = Color(hex: 0x4A9EFF)
    static let accentDim = Color(hex: 0x4A9EFF, alpha: 0.12)

    // MARK: - Semantic

    static let success = Color(hex: 0x34D399)
    static let successDim = Color(hex: 0x34D399, alpha: 0.12)

    static let warning = Color(hex: 0xFBBF24)
    static let warningDim = Color(hex: 0xFBBF24, alpha: 0.12)

    static let danger = Color(hex: 0xF87171)
    static let dangerDim = Color(hex: 0xF87171, alpha: 0.12)

    // MARK: - Crypto

    static let pqc = Color(hex: 0xA78BFA)
    static let pqcDim = Color(hex: 0xA78BFA, alpha: 0.12)

    static let classical = Color(hex: 0x38BDF8)
    static let classicalDim = Color(hex: 0x38BDF8, alpha: 0.12)

    // MARK: - Border

    static let ssdidBorder = Color.white.opacity(0.06)
    static let borderStrong = Color.white.opacity(0.10)

    // MARK: - Hex Initializer

    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }
}
