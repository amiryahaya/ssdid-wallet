import Foundation
import LocalAuthentication

/// Result of a biometric authentication attempt.
enum BiometricResult {
    case success
    case cancelled
    case error(String)
}

/// Biometric type available on the device.
enum BiometricType {
    case none
    case touchID
    case faceID
    case opticID
}

/// Provides biometric authentication using LocalAuthentication framework.
final class BiometricAuthenticator: @unchecked Sendable {

    private let context: LAContext

    init(context: LAContext = LAContext()) {
        self.context = context
    }

    /// Returns true if biometric authentication is available.
    func canAuthenticate() -> Bool {
        var error: NSError?
        return context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    }

    /// Returns the type of biometric authentication available.
    func biometricType() -> BiometricType {
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            return .none
        }

        switch context.biometryType {
        case .touchID:
            return .touchID
        case .faceID:
            return .faceID
        case .opticID:
            return .opticID
        default:
            return .none
        }
    }

    /// Performs biometric authentication with the given reason string.
    func authenticate(reason: String = "Authenticate to access your SSDID Wallet") async -> BiometricResult {
        let context = LAContext()
        context.localizedCancelTitle = "Cancel"

        do {
            let success = try await context.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: reason
            )
            return success ? .success : .error("Authentication returned false")
        } catch let error as LAError {
            switch error.code {
            case .userCancel, .appCancel, .systemCancel:
                return .cancelled
            case .biometryNotAvailable:
                return .error("Biometric authentication is not available")
            case .biometryNotEnrolled:
                return .error("No biometric data is enrolled")
            case .biometryLockout:
                return .error("Biometric authentication is locked out due to too many failed attempts")
            case .authenticationFailed:
                return .error("Biometric authentication failed")
            default:
                return .error(error.localizedDescription)
            }
        } catch {
            return .error(error.localizedDescription)
        }
    }

    /// Performs device owner authentication (biometric or passcode fallback).
    func authenticateWithPasscodeFallback(reason: String = "Authenticate to access your SSDID Wallet") async -> BiometricResult {
        let context = LAContext()

        do {
            let success = try await context.evaluatePolicy(
                .deviceOwnerAuthentication,
                localizedReason: reason
            )
            return success ? .success : .error("Authentication returned false")
        } catch let error as LAError {
            switch error.code {
            case .userCancel, .appCancel, .systemCancel:
                return .cancelled
            default:
                return .error(error.localizedDescription)
            }
        } catch {
            return .error(error.localizedDescription)
        }
    }
}
