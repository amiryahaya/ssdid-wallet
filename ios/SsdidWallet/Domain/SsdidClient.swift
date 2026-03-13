import Foundation
import LibOQS

/// Central orchestrator for SSDID Wallet operations.
/// Coordinates between Vault, HTTP client, Verifier, and Activity logging.
final class SsdidClient: @unchecked Sendable {

    private nonisolated(unsafe) static let iso8601 = ISO8601DateFormatter()

    private let vault: Vault
    private let verifier: Verifier
    private let httpClient: SsdidHttpClient
    private let activityRepo: ActivityRepository
    private let revocationManager: RevocationManager
    private let notifyManager: NotifyManager?

    init(
        vault: Vault,
        verifier: Verifier,
        httpClient: SsdidHttpClient,
        activityRepo: ActivityRepository,
        revocationManager: RevocationManager,
        notifyManager: NotifyManager? = nil
    ) {
        self.vault = vault
        self.verifier = verifier
        self.httpClient = httpClient
        self.activityRepo = activityRepo
        self.revocationManager = revocationManager
        self.notifyManager = notifyManager
    }

    // MARK: - Activity Logging

    private func logActivity(
        type: ActivityType,
        did: String,
        serviceUrl: String? = nil,
        details: [String: String] = [:]
    ) async {
        do {
            try await activityRepo.addActivity(ActivityRecord(
                id: UUID().uuidString,
                type: type,
                did: did,
                serviceUrl: serviceUrl,
                timestamp: Self.iso8601.string(from: Date()),
                status: .SUCCESS,
                details: details
            ))
        } catch {
            // Activity logging should never break the main flow
        }
    }

    // MARK: - Flow 1: Create Identity and Publish DID

    /// Creates a new identity, builds a DID Document, and registers it with the Registry.
    /// Rolls back the local identity if registry registration fails.
    func initIdentity(name: String, algorithm: Algorithm) async throws -> Identity {
        let identity = try await vault.createIdentity(name: name, algorithm: algorithm)

        do {
            let didDoc = try await vault.buildDidDocument(keyId: identity.keyId)

            // Encode DID Document as dictionary for proof creation
            let didDocDict = try encodableToDict(didDoc)
            let proof = try await vault.createProof(
                keyId: identity.keyId,
                document: didDocDict,
                proofPurpose: "assertionMethod",
                challenge: nil,
                domain: nil
            )

            _ = try await httpClient.registry.registerDid(request: RegisterDidRequest(
                didDocument: didDoc,
                proof: proof
            ))
        } catch {
            // Roll back: remove the locally saved identity on failure
            try? await vault.deleteIdentity(keyId: identity.keyId)
            throw error
        }

        // Best-effort: notify failure must not block identity creation.
        try? await notifyManager?.createMailbox(for: identity)

        await logActivity(type: .IDENTITY_CREATED, did: identity.did, details: ["algorithm": algorithm.rawValue])
        return identity
    }

    // MARK: - Update DID Document

    /// Updates the DID Document on the Registry (used by rotation and recovery).
    func updateDidDocument(keyId: String) async throws {
        guard let identity = await vault.getIdentity(keyId: keyId) else {
            throw VaultError.identityNotFound(keyId)
        }
        let didDoc = try await vault.buildDidDocument(keyId: keyId)
        let didDocDict = try encodableToDict(didDoc)

        let challengeResp = try await httpClient.registry.createChallenge(did: identity.did)
        let proof = try await vault.createProof(
            keyId: keyId,
            document: didDocDict,
            proofPurpose: "capabilityInvocation",
            challenge: challengeResp.challenge,
            domain: challengeResp.domain
        )

        _ = try await httpClient.registry.updateDid(
            did: identity.did,
            request: UpdateDidRequest(didDocument: didDoc, proof: proof)
        )
    }

    // MARK: - Deactivate DID

    /// Deactivates a DID -- irreversible.
    func deactivateDid(keyId: String) async throws {
        guard let identity = await vault.getIdentity(keyId: keyId) else {
            throw VaultError.identityNotFound(keyId)
        }

        let deactivateData: [String: Any] = ["action": "deactivate", "did": identity.did]
        let challengeResp = try await httpClient.registry.createChallenge(did: identity.did)

        let proof = try await vault.createProof(
            keyId: keyId,
            document: deactivateData,
            proofPurpose: "capabilityInvocation",
            challenge: challengeResp.challenge,
            domain: challengeResp.domain
        )

        try await httpClient.registry.deactivateDid(
            did: identity.did,
            request: DeactivateDidRequest(proof: proof)
        )

        // Best-effort: notify failure must not block deactivation.
        try? await notifyManager?.deleteMailbox(for: identity)

        try await vault.deleteIdentity(keyId: keyId)
        await logActivity(type: .IDENTITY_DEACTIVATED, did: identity.did)
    }

    // MARK: - Flow 2: Register with Service

    /// Registers with a service using mutual authentication.
    /// Returns the verifiable credential received from the service.
    func registerWithService(identity: Identity, serverUrl: String) async throws -> VerifiableCredential {
        let serverApi = httpClient.serverApi(baseURL: serverUrl)

        // Step 1: Start registration
        let startResp = try await serverApi.registerStart(request: RegisterStartRequest(
            did: identity.did,
            keyId: identity.keyId
        ))

        // Step 2: Verify server's signature (mutual auth)
        let serverVerified = try await verifier.verifyChallengeResponse(
            did: startResp.serverDid,
            keyId: startResp.serverKeyId,
            challenge: startResp.challenge,
            signedChallenge: startResp.serverSignature
        )
        guard serverVerified else {
            throw SsdidClientError.mutualAuthFailed("Server mutual authentication failed")
        }

        // Step 3: Sign the challenge
        let signatureBytes = try await vault.sign(keyId: identity.keyId, data: Data(startResp.challenge.utf8))
        let signedChallenge = Multibase.encode(signatureBytes)

        // Step 4: Complete registration
        let verifyResp = try await serverApi.registerVerify(request: RegisterVerifyRequest(
            did: identity.did,
            keyId: identity.keyId,
            signedChallenge: signedChallenge
        ))

        // Step 5: Store the credential
        let vc = verifyResp.credential
        try await vault.storeCredential(vc)

        await logActivity(type: .SERVICE_REGISTERED, did: identity.did, serviceUrl: serverUrl)
        await logActivity(type: .CREDENTIAL_RECEIVED, did: identity.did, serviceUrl: serverUrl)
        return vc
    }

    // MARK: - Flow 3: Authenticate with Service

    /// Authenticates with a service by presenting a credential.
    func authenticate(credential: VerifiableCredential, serverUrl: String) async throws -> AuthenticateResponse {
        let revocationStatus = await revocationManager.checkRevocation(credential)
        if revocationStatus == .revoked {
            throw SsdidClientError.credentialRevoked
        }

        let serverApi = httpClient.serverApi(baseURL: serverUrl)
        let resp = try await serverApi.authenticate(request: AuthenticateRequest(credential: credential))

        // Verify server's session token signature (mutual auth -- mandatory)
        guard let serverSig = resp.serverSignature else {
            throw SsdidClientError.mutualAuthFailed("Server did not provide mutual authentication signature")
        }
        let verified = try await verifier.verifyChallengeResponse(
            did: resp.serverDid,
            keyId: resp.serverKeyId,
            challenge: resp.sessionToken,
            signedChallenge: serverSig
        )
        guard verified else {
            throw SsdidClientError.mutualAuthFailed("Server session token verification failed")
        }

        await logActivity(type: .AUTHENTICATED, did: credential.credentialSubject.id, serviceUrl: serverUrl)
        return resp
    }

    // MARK: - Fetch Transaction Details

    /// Fetches transaction details from server for display before signing.
    func fetchTransactionDetails(sessionToken: String, serverUrl: String) async throws -> [String: String] {
        let serverApi = httpClient.serverApi(baseURL: serverUrl)
        let resp = try await serverApi.requestChallenge(request: TxChallengeRequest(sessionToken: sessionToken))
        return resp.transaction
    }

    // MARK: - Flow 4: Sign Transaction

    /// Signs a transaction with challenge-response and transaction binding.
    func signTransaction(
        sessionToken: String,
        identity: Identity,
        transaction: [String: String],
        serverUrl: String
    ) async throws -> TxSubmitResponse {
        let serverApi = httpClient.serverApi(baseURL: serverUrl)

        // Step 1: Request fresh challenge
        let challengeResp = try await serverApi.requestChallenge(request: TxChallengeRequest(sessionToken: sessionToken))

        // Step 2: Hash transaction body with SHA3-256
        let txData = try JSONSerialization.data(
            withJSONObject: transaction,
            options: [.sortedKeys, .withoutEscapingSlashes]
        )
        let txHash = SHA3.sha256(txData)
        let txHashBase64 = txHash.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")

        // Step 3: Sign challenge || txHash (transaction binding)
        let payload = Data((challengeResp.challenge + txHashBase64).utf8)
        let signatureBytes = try await vault.sign(keyId: identity.keyId, data: payload)
        let signedChallenge = Multibase.encode(signatureBytes)

        // Step 4: Submit signed transaction
        let response = try await serverApi.submitTransaction(request: TxSubmitRequest(
            sessionToken: sessionToken,
            did: identity.did,
            keyId: identity.keyId,
            signedChallenge: signedChallenge,
            transaction: transaction
        ))

        await logActivity(type: .TX_SIGNED, did: identity.did, serviceUrl: serverUrl)
        return response
    }

    // MARK: - Verification

    /// Verifies a multibase-encoded challenge response against a DID's public key.
    func verifyChallengeResponse(did: String, keyId: String, challenge: String, signedChallenge: String) async throws -> Bool {
        return try await verifier.verifyChallengeResponse(did: did, keyId: keyId, challenge: challenge, signedChallenge: signedChallenge)
    }

    // MARK: - Helpers

    /// Converts an Encodable value to a [String: Any] dictionary.
    private func encodableToDict<T: Encodable>(_ value: T) throws -> [String: Any] {
        let data = try JSONEncoder().encode(value)
        guard let dict = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw SsdidClientError.serializationFailed("Failed to convert to dictionary")
        }
        return dict
    }
}

/// Errors specific to SsdidClient operations.
enum SsdidClientError: Error, LocalizedError {
    case mutualAuthFailed(String)
    case serializationFailed(String)
    case credentialRevoked

    var errorDescription: String? {
        switch self {
        case .mutualAuthFailed(let reason):
            return "Mutual authentication failed: \(reason)"
        case .serializationFailed(let reason):
            return "Serialization failed: \(reason)"
        case .credentialRevoked:
            return "Credential has been revoked"
        }
    }
}
