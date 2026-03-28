import Foundation
#if canImport(UIKit)
import UIKit
#endif

/// Provides device information for pairing, logging, and server interactions.
public final class DeviceInfoProvider {

    /// Returns the device model name (e.g., "iPhone 15 Pro").
    public     var modelName: String {
        var systemInfo = utsname()
        uname(&systemInfo)
        let machineMirror = Mirror(reflecting: systemInfo.machine)
        let identifier = machineMirror.children.reduce("") { identifier, element in
            guard let value = element.value as? Int8, value != 0 else { return identifier }
            return identifier + String(UnicodeScalar(UInt8(value)))
        }
        return mapDeviceIdentifier(identifier)
    }

    /// Returns the device name set by the user (e.g., "John's iPhone").
    public     var deviceName: String {
        #if canImport(UIKit)
        UIDevice.current.name
        #else
        ProcessInfo.processInfo.hostName
        #endif
    }

    /// Returns the iOS version string (e.g., "17.4.1").
    public     var systemVersion: String {
        #if canImport(UIKit)
        UIDevice.current.systemVersion
        #else
        ProcessInfo.processInfo.operatingSystemVersionString
        #endif
    }

    /// Returns the platform identifier for server communications.
    public     var platform: String {
        "iOS"
    }

    /// Returns the app version string (e.g., "1.0.0").
    public     var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
    }

    /// Returns the app build number (e.g., "42").
    public     var buildNumber: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "unknown"
    }

    /// Returns a unique device identifier (persisted in UserDefaults).
    /// This is NOT the UDID/IDFV; it is a randomly generated identifier for pairing.
    public     var deviceId: String {
        let key = "ssdid_device_id"
        if let existing = UserDefaults.standard.string(forKey: key) {
            return existing
        }
        let newId = UUID().uuidString
        UserDefaults.standard.set(newId, forKey: key)
        return newId
    }

    /// Returns a user-agent string for HTTP requests.
    public     var userAgent: String {
        "SsdidWallet-iOS/\(appVersion) (\(modelName); iOS \(systemVersion))"
    }

    // MARK: - Device Identifier Mapping

    private func mapDeviceIdentifier(_ identifier: String) -> String {
        // Map common device identifiers to marketing names.
        // This is a simplified mapping; a production app would use a more complete list.
        switch identifier {
        case "iPhone16,1": return "iPhone 15 Pro"
        case "iPhone16,2": return "iPhone 15 Pro Max"
        case "iPhone15,4": return "iPhone 15"
        case "iPhone15,5": return "iPhone 15 Plus"
        case "iPhone17,1": return "iPhone 16 Pro"
        case "iPhone17,2": return "iPhone 16 Pro Max"
        case "iPhone17,3": return "iPhone 16"
        case "iPhone17,4": return "iPhone 16 Plus"
        case "iPad16,3", "iPad16,4": return "iPad Pro M4"
        case "iPad16,5", "iPad16,6": return "iPad Pro M4 13-inch"
        default:
            if identifier.hasPrefix("iPhone") { return "iPhone" }
            if identifier.hasPrefix("iPad") { return "iPad" }
            if identifier == "x86_64" || identifier == "arm64" { return "Simulator" }
            return identifier
        }
    }
}
