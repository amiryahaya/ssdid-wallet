import Foundation

/// Supported languages for the SSDID Wallet.
enum AppLanguage: String, CaseIterable {
    case english = "en"
    case malay = "ms"
    case chinese = "zh"

    var displayName: String {
        switch self {
        case .english: return "English"
        case .malay: return "Bahasa Melayu"
        case .chinese: return "中文"
        }
    }
}

/// Manages locale and language preferences.
final class LocalizationManager {

    private let defaults: UserDefaults
    private static let languageKey = "ssdid_app_language"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    /// Returns the currently selected language.
    var currentLanguage: AppLanguage {
        get {
            let raw = defaults.string(forKey: Self.languageKey) ?? "en"
            return AppLanguage(rawValue: raw) ?? .english
        }
        set {
            defaults.set(newValue.rawValue, forKey: Self.languageKey)
        }
    }

    /// Returns a localized string for the given key.
    /// Falls back to the key itself if no localization is found.
    func localizedString(_ key: String) -> String {
        let language = currentLanguage.rawValue

        guard let bundlePath = Bundle.main.path(forResource: language, ofType: "lproj"),
              let bundle = Bundle(path: bundlePath) else {
            return NSLocalizedString(key, comment: "")
        }

        return bundle.localizedString(forKey: key, value: nil, table: nil)
    }

    /// Returns the locale identifier for the current language.
    var currentLocale: Locale {
        Locale(identifier: currentLanguage.rawValue)
    }

    /// Detects and sets the initial language based on device locale.
    func detectInitialLanguage() {
        // Only set if not previously configured
        guard defaults.string(forKey: Self.languageKey) == nil else { return }

        let preferredLanguage = Locale.preferredLanguages.first ?? "en"
        if preferredLanguage.hasPrefix("ms") {
            currentLanguage = .malay
        } else if preferredLanguage.hasPrefix("zh") {
            currentLanguage = .chinese
        } else {
            currentLanguage = .english
        }
    }
}
