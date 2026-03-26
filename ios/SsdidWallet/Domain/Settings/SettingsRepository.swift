import Foundation
import Combine

/// Protocol for managing application settings.
protocol SettingsRepository {
    func biometricEnabled() -> AnyPublisher<Bool, Never>
    func setBiometricEnabled(_ enabled: Bool)

    func autoLockMinutes() -> AnyPublisher<Int, Never>
    func setAutoLockMinutes(_ minutes: Int)

    func defaultAlgorithm() -> AnyPublisher<String, Never>
    func setDefaultAlgorithm(_ algorithm: String)

    func language() -> AnyPublisher<String, Never>
    func setLanguage(_ language: String)

    func bundleTtlDays() -> AnyPublisher<Int, Never>
    func setBundleTtlDays(_ days: Int)
}

/// UserDefaults-based implementation of SettingsRepository.
final class UserDefaultsSettingsRepository: SettingsRepository {

    private let defaults: UserDefaults

    private enum Keys {
        static let biometricEnabled = "ssdid_biometric_enabled"
        static let autoLockMinutes = "ssdid_auto_lock_minutes"
        static let defaultAlgorithm = "ssdid_default_algorithm"
        static let language = "ssdid_language"
        static let bundleTtlDays = "ssdid_bundle_ttl_days"
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        registerDefaults()
        migrateToMandatoryBiometric()
    }

    private func registerDefaults() {
        defaults.register(defaults: [
            Keys.biometricEnabled: true,
            Keys.autoLockMinutes: 5,
            Keys.defaultAlgorithm: Algorithm.ED25519.rawValue,
            Keys.language: "en",
            Keys.bundleTtlDays: 7
        ])
    }

    /// Migration: force biometric_enabled = true for existing users who had disabled it.
    private func migrateToMandatoryBiometric() {
        if defaults.object(forKey: Keys.biometricEnabled) != nil {
            defaults.set(true, forKey: Keys.biometricEnabled)
        }
    }

    // MARK: - Biometric

    func biometricEnabled() -> AnyPublisher<Bool, Never> {
        defaults.publisher(for: \.ssdidBiometricEnabled)
            .eraseToAnyPublisher()
    }

    func setBiometricEnabled(_ enabled: Bool) {
        defaults.set(enabled, forKey: Keys.biometricEnabled)
    }

    // MARK: - Auto Lock

    func autoLockMinutes() -> AnyPublisher<Int, Never> {
        defaults.publisher(for: \.ssdidAutoLockMinutes)
            .eraseToAnyPublisher()
    }

    func setAutoLockMinutes(_ minutes: Int) {
        defaults.set(minutes, forKey: Keys.autoLockMinutes)
    }

    // MARK: - Default Algorithm

    func defaultAlgorithm() -> AnyPublisher<String, Never> {
        defaults.publisher(for: \.ssdidDefaultAlgorithm)
            .eraseToAnyPublisher()
    }

    func setDefaultAlgorithm(_ algorithm: String) {
        defaults.set(algorithm, forKey: Keys.defaultAlgorithm)
    }

    // MARK: - Language

    func language() -> AnyPublisher<String, Never> {
        defaults.publisher(for: \.ssdidLanguage)
            .eraseToAnyPublisher()
    }

    func setLanguage(_ language: String) {
        defaults.set(language, forKey: Keys.language)
    }

    // MARK: - Bundle TTL

    func bundleTtlDays() -> AnyPublisher<Int, Never> {
        defaults.publisher(for: \.ssdidBundleTtlDays)
            .eraseToAnyPublisher()
    }

    func setBundleTtlDays(_ days: Int) {
        defaults.set(days, forKey: Keys.bundleTtlDays)
    }
}

// MARK: - UserDefaults KVO Keys

private extension UserDefaults {
    @objc var ssdidBiometricEnabled: Bool {
        bool(forKey: "ssdid_biometric_enabled")
    }

    @objc var ssdidAutoLockMinutes: Int {
        integer(forKey: "ssdid_auto_lock_minutes")
    }

    @objc var ssdidDefaultAlgorithm: String {
        string(forKey: "ssdid_default_algorithm") ?? Algorithm.ED25519.rawValue
    }

    @objc var ssdidLanguage: String {
        string(forKey: "ssdid_language") ?? "en"
    }

    @objc var ssdidBundleTtlDays: Int {
        integer(forKey: "ssdid_bundle_ttl_days")
    }
}
