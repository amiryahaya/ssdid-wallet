import Foundation

/// Provides bundle TTL configuration for the orchestrator.
protocol TtlProvider {
    var bundleTtlSeconds: TimeInterval { get }
}

/// Default TTL provider: 7-day bundle freshness window.
struct DefaultTtlProvider: TtlProvider {
    let bundleTtlSeconds: TimeInterval = 7 * 86400
}

/// Orchestrates credential verification by attempting online first,
/// then falling back to offline (cached bundle) verification when
/// network errors or server failures occur.
final class VerificationOrchestrator {
    private let onlineVerifier: Verifier
    private let offlineVerifier: OfflineVerifier
    private let bundleStore: BundleStore
    private let ttlProvider: TtlProvider

    init(
        onlineVerifier: Verifier,
        offlineVerifier: OfflineVerifier,
        bundleStore: BundleStore,
        ttlProvider: TtlProvider = DefaultTtlProvider()
    ) {
        self.onlineVerifier = onlineVerifier
        self.offlineVerifier = offlineVerifier
        self.bundleStore = bundleStore
        self.ttlProvider = ttlProvider
    }

    /// Verify a credential online, falling back to offline if a network/server error occurs.
    func verify(credential: VerifiableCredential) async -> UnifiedVerificationResult {
        do {
            let verified = try await onlineVerifier.verifyCredential(credential: credential)
            if verified {
                return UnifiedVerificationResult(
                    status: .verified,
                    checks: [
                        VerificationCheck(type: .signature, status: .pass, message: "Signature verified online"),
                        VerificationCheck(type: .expiry, status: .pass, message: "Credential is not expired"),
                        VerificationCheck(type: .revocation, status: .pass, message: "Revocation status confirmed online")
                    ],
                    source: .online
                )
            } else {
                return UnifiedVerificationResult(
                    status: .failed,
                    checks: [
                        VerificationCheck(type: .signature, status: .fail, message: "Signature verification failed")
                    ],
                    source: .online
                )
            }
        } catch {
            if isNetworkError(error) {
                return await fallbackToOffline(credential: credential)
            } else {
                return UnifiedVerificationResult(
                    status: .failed,
                    checks: [
                        VerificationCheck(type: .signature, status: .fail, message: error.localizedDescription)
                    ],
                    source: .online
                )
            }
        }
    }

    // MARK: - Offline Fallback

    private func fallbackToOffline(credential: VerifiableCredential) async -> UnifiedVerificationResult {
        let offlineResult = await offlineVerifier.verifyCredential(credential)

        // Compute bundle age from the cached bundle's fetchedAt timestamp
        let bundleAge: TimeInterval? = await computeBundleAge(credential: credential)

        // Expiry check directly from the credential (not relying on string from offlineResult.error)
        let expiryFailed: Bool = {
            guard let expDateStr = credential.expirationDate,
                  let expDate = ISO8601DateFormatter().date(from: expDateStr) else { return false }
            return expDate < Date()
        }()

        // Build individual checks
        var checks: [VerificationCheck] = []

        // Expiry check
        checks.append(VerificationCheck(
            type: .expiry,
            status: expiryFailed ? .fail : .pass,
            message: expiryFailed ? "Credential has expired" : "Credential is within validity period"
        ))

        // Signature check
        checks.append(VerificationCheck(
            type: .signature,
            status: offlineResult.signatureValid ? .pass : .fail,
            message: offlineResult.signatureValid
                ? "Signature verified using cached bundle"
                : (offlineResult.error ?? "Signature verification failed")
        ))

        // Revocation check
        let revocationCheckStatus: CheckStatus
        let revocationMessage: String
        switch offlineResult.revocationStatus {
        case .valid:
            revocationCheckStatus = .pass
            revocationMessage = "Not revoked (cached status list)"
        case .revoked:
            revocationCheckStatus = .fail
            revocationMessage = "Credential has been revoked"
        case .unknown:
            revocationCheckStatus = .unknown
            revocationMessage = "Revocation status unknown (no cached status list)"
        }
        checks.append(VerificationCheck(
            type: .revocation,
            status: revocationCheckStatus,
            message: revocationMessage
        ))

        // Bundle freshness check
        checks.append(VerificationCheck(
            type: .bundleFreshness,
            status: offlineResult.bundleFresh ? .pass : .fail,
            message: offlineResult.bundleFresh
                ? "Verification bundle is fresh"
                : "Verification bundle is stale or missing"
        ))

        // Determine overall status using the same precedence as Android
        let status = determineStatus(offlineResult: offlineResult, expiryFailed: expiryFailed)

        return UnifiedVerificationResult(
            status: status,
            checks: checks,
            source: .offline,
            bundleAge: bundleAge
        )
    }

    /// Maps OfflineVerificationResult fields to a VerificationStatus,
    /// matching the Android fallback-mapping precedence.
    private func determineStatus(
        offlineResult: OfflineVerificationResult,
        expiryFailed: Bool
    ) -> VerificationStatus {
        if offlineResult.error != nil && !offlineResult.signatureValid {
            // Hard error (missing bundle, serialisation failure, etc.)
            return .failed
        }
        if expiryFailed {
            return .failed
        }
        if !offlineResult.signatureValid {
            return .failed
        }
        if offlineResult.revocationStatus == .revoked {
            return .failed
        }
        if !offlineResult.bundleFresh {
            return .degraded
        }
        if offlineResult.revocationStatus == .unknown {
            return .degraded
        }
        return .verifiedOffline
    }

    private func computeBundleAge(credential: VerifiableCredential) async -> TimeInterval? {
        let issuerDid = Did.fromKeyId(credential.proof.verificationMethod)
        guard let bundle = await bundleStore.getBundle(issuerDid: issuerDid.value),
              let fetchedDate = ISO8601DateFormatter().date(from: bundle.fetchedAt) else {
            return nil
        }
        return Date().timeIntervalSince(fetchedDate)
    }

    // MARK: - Network Error Detection

    private func isNetworkError(_ error: Error) -> Bool {
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain { return true }
        if case HttpError.networkError = error { return true }
        if case HttpError.timeout = error { return true }
        if case HttpError.requestFailed(let statusCode, _) = error,
           (500...599).contains(statusCode) { return true }
        return false
    }
}
