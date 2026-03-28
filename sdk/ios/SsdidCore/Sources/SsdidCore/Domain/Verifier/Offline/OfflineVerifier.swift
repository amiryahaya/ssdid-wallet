import Foundation

/// Verifies credentials offline using cached verification bundles.
/// Falls back gracefully when bundles are stale or missing.
public final class OfflineVerifier {
    private let classicalProvider: CryptoProvider
    private let pqcProvider: CryptoProvider
    private let bundleStore: BundleStore
    private let ttlProvider: TtlProvider

    public     init(
        classicalProvider: CryptoProvider,
        pqcProvider: CryptoProvider,
        bundleStore: BundleStore,
        ttlProvider: TtlProvider = TtlProvider()
    ) {
        self.classicalProvider = classicalProvider
        self.pqcProvider = pqcProvider
        self.bundleStore = bundleStore
        self.ttlProvider = ttlProvider
    }

    /// Verify a credential using cached bundles.
    public     func verifyCredential(_ credential: VerifiableCredential) async -> OfflineVerificationResult {
        // Check expiration
        if let exp = credential.expirationDate,
           let expDate = ISO8601DateFormatter().date(from: exp),
           Date() > expDate {
            return OfflineVerificationResult(
                signatureValid: false,
                revocationStatus: .unknown,
                bundleFresh: false,
                error: "Credential expired at \(exp)"
            )
        }

        let issuerDid = Did.fromKeyId(credential.proof.verificationMethod)
        guard let bundle = await bundleStore.getBundle(issuerDid: issuerDid.value) else {
            return OfflineVerificationResult(
                signatureValid: false,
                revocationStatus: .unknown,
                bundleFresh: false,
                error: "No cached bundle for issuer \(issuerDid.value)"
            )
        }

        let bundleFresh = !ttlProvider.isExpired(fetchedAt: bundle.fetchedAt)

        // Verify signature using cached DID Document
        let signatureValid: Bool
        do {
            guard let vm = bundle.didDocument.verificationMethod.first(where: {
                $0.id == credential.proof.verificationMethod
            }) else {
                return OfflineVerificationResult(
                    signatureValid: false,
                    revocationStatus: .unknown,
                    bundleFresh: bundleFresh,
                    error: "Key not found in cached DID Document"
                )
            }

            let publicKey = try Multibase.decode(vm.publicKeyMultibase)
            guard let algorithm = Algorithm.fromW3cType(vm.type) else {
                return OfflineVerificationResult(
                    signatureValid: false,
                    revocationStatus: .unknown,
                    bundleFresh: bundleFresh,
                    error: "Unknown verification method type: \(vm.type)"
                )
            }
            let provider = algorithm.isPostQuantum ? pqcProvider : classicalProvider
            let signature = try Multibase.decode(credential.proof.proofValue)
            let signedData = canonicalizeWithoutProof(credential)
            signatureValid = try provider.verify(
                algorithm: algorithm,
                publicKey: publicKey,
                signature: signature,
                data: signedData
            )
        } catch {
            return OfflineVerificationResult(
                signatureValid: false,
                revocationStatus: .unknown,
                bundleFresh: bundleFresh,
                error: "Signature verification failed: \(error.localizedDescription)"
            )
        }

        // Check revocation using cached status list
        let revocationStatus = checkRevocationOffline(credential: credential, bundle: bundle)

        return OfflineVerificationResult(
            signatureValid: signatureValid,
            revocationStatus: revocationStatus,
            bundleFresh: bundleFresh
        )
    }

    private func checkRevocationOffline(
        credential: VerifiableCredential,
        bundle: VerificationBundle
    ) -> RevocationStatus {
        guard let status = credential.credentialStatus else { return .valid }
        guard let statusList = bundle.statusList else { return .unknown }

        // S7: Validate that the credential's statusListCredential URL matches the cached bundle id
        guard status.statusListCredential == statusList.id else { return .unknown }

        // S2: Require a proof on the status list credential before trusting its bitstring
        guard statusList.proof != nil else { return .unknown }

        guard let index = Int(status.statusListIndex) else { return .unknown }

        do {
            if try BitstringParser.isRevoked(encodedList: statusList.credentialSubject.encodedList, index: index) {
                return .revoked
            } else {
                return .valid
            }
        } catch {
            return .unknown
        }
    }

    private func canonicalizeWithoutProof(_ credential: VerifiableCredential) -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        guard let jsonData = try? encoder.encode(credential),
              var dict = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any] else {
            return Data()
        }
        dict.removeValue(forKey: "proof")
        let canonical = JsonUtils.canonicalJson(dict)
        return Data(canonical.utf8)
    }
}

public struct OfflineVerificationResult {
    public let signatureValid: Bool
    public let revocationStatus: RevocationStatus
    public let bundleFresh: Bool
    public var error: String? = nil

    public init(signatureValid: Bool, revocationStatus: RevocationStatus, bundleFresh: Bool, error: String? = nil) {
        self.signatureValid = signatureValid
        self.revocationStatus = revocationStatus
        self.bundleFresh = bundleFresh
        self.error = error
    }

    public var isValid: Bool {
        signatureValid && revocationStatus != .revoked && error == nil
    }
}
