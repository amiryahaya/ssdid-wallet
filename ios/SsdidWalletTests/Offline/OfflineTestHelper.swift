@testable import SsdidCore
import Foundation
import CryptoKit
@testable import SsdidWallet

enum OfflineTestHelper {

    nonisolated(unsafe) private static let classicalProvider = ClassicalProvider()

    static func createKeyPair() throws -> (publicKeyMultibase: String, privateKey: Data) {
        let keyPair = try classicalProvider.generateKeyPair(algorithm: .ED25519)
        let multibase = Multibase.encode(keyPair.publicKey)
        return (multibase, keyPair.privateKey)
    }

    static func createTestCredential(
        issuerDid: String,
        keyId: String,
        privateKey: Data,
        expirationDate: String? = nil,
        credentialStatus: CredentialStatus? = nil
    ) throws -> VerifiableCredential {
        let now = ISO8601DateFormatter().string(from: Date())
        let subject = CredentialSubject(id: "did:ssdid:holder123")

        // Build credential without a real proof value first (placeholder)
        var cred = VerifiableCredential(
            id: "urn:uuid:\(UUID().uuidString)",
            type: ["VerifiableCredential"],
            issuer: issuerDid,
            issuanceDate: now,
            expirationDate: expirationDate,
            credentialSubject: subject,
            credentialStatus: credentialStatus,
            proof: Proof(
                type: "Ed25519Signature2020",
                created: now,
                verificationMethod: keyId,
                proofPurpose: "assertionMethod",
                proofValue: ""
            )
        )

        // Canonicalize without proof, then sign
        let canonical = try canonicalizeWithoutProof(cred)
        let signature = try classicalProvider.sign(algorithm: .ED25519, privateKey: privateKey, data: canonical)
        let proofValue = Multibase.encode(signature)

        // Rebuild with real proof value
        cred = VerifiableCredential(
            id: cred.id,
            type: cred.type,
            issuer: cred.issuer,
            issuanceDate: cred.issuanceDate,
            expirationDate: cred.expirationDate,
            credentialSubject: cred.credentialSubject,
            credentialStatus: cred.credentialStatus,
            proof: Proof(
                type: cred.proof.type,
                created: cred.proof.created,
                verificationMethod: cred.proof.verificationMethod,
                proofPurpose: cred.proof.proofPurpose,
                proofValue: proofValue
            )
        )
        return cred
    }

    static func createBundle(
        issuerDid: String,
        didDocument: DidDocument,
        freshnessRatio: Double = 0.1,
        ttlDays: Int = 7,
        statusList: StatusListCredential? = nil
    ) -> VerificationBundle {
        let ttlSeconds = TimeInterval(ttlDays) * 86400
        let ageSeconds = ttlSeconds * freshnessRatio
        let fetchedAt = Date().addingTimeInterval(-ageSeconds)
        let expiresAt = fetchedAt.addingTimeInterval(ttlSeconds)
        let formatter = ISO8601DateFormatter()
        return VerificationBundle(
            issuerDid: issuerDid,
            didDocument: didDocument,
            statusList: statusList,
            fetchedAt: formatter.string(from: fetchedAt),
            expiresAt: formatter.string(from: expiresAt)
        )
    }

    static func createStaleBundle(
        issuerDid: String,
        didDocument: DidDocument,
        statusList: StatusListCredential? = nil
    ) -> VerificationBundle {
        // Bundle that expired 1 day ago (fetched 8 days ago with 7-day TTL)
        let ttlSeconds = TimeInterval(7) * 86400
        let fetchedAt = Date().addingTimeInterval(-(ttlSeconds + 86400))
        let expiresAt = fetchedAt.addingTimeInterval(ttlSeconds)
        let formatter = ISO8601DateFormatter()
        return VerificationBundle(
            issuerDid: issuerDid,
            didDocument: didDocument,
            statusList: statusList,
            fetchedAt: formatter.string(from: fetchedAt),
            expiresAt: formatter.string(from: expiresAt)
        )
    }

    static func createDidDocument(did: String, keyId: String, publicKeyMultibase: String) -> DidDocument {
        DidDocument(
            context: ["https://www.w3.org/ns/did/v1"],
            id: did,
            controller: did,
            verificationMethod: [
                VerificationMethod(
                    id: keyId,
                    type: "Ed25519VerificationKey2020",
                    controller: did,
                    publicKeyMultibase: publicKeyMultibase
                )
            ],
            authentication: [keyId],
            assertionMethod: [keyId],
            capabilityInvocation: [keyId]
        )
    }

    private static func canonicalizeWithoutProof(_ credential: VerifiableCredential) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        let data = try encoder.encode(credential)
        var dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        dict.removeValue(forKey: "proof")
        let canonical = JsonUtils.canonicalJson(dict)
        return Data(canonical.utf8)
    }
}
