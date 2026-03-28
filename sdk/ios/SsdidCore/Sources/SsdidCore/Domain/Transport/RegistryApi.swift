import Foundation

/// Registry API client for DID operations and device pairing.
public final class RegistryApi: @unchecked Sendable {

    private let client: SsdidHttpClient
    private let baseURL: String

    public     init(client: SsdidHttpClient, baseURL: String) {
        self.client = client
        self.baseURL = baseURL
    }

    // MARK: - Registry Info

    public     func getRegistryInfo() async throws -> RegistryInfoResponse {
        return try await client.get(
            url: "\(baseURL)/api/registry/info",
            responseType: RegistryInfoResponse.self
        )
    }

    // MARK: - DID Operations

    public     func registerDid(request: RegisterDidRequest) async throws -> RegisterDidResponse {
        return try await client.post(
            url: "\(baseURL)/api/did",
            body: request,
            responseType: RegisterDidResponse.self
        )
    }

    public     func resolveDid(did: String) async throws -> DidDocument {
        let encodedDid = did.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? did
        return try await client.get(
            url: "\(baseURL)/api/did/\(encodedDid)",
            responseType: DidDocument.self
        )
    }

    public     func updateDid(did: String, request: UpdateDidRequest) async throws -> RegisterDidResponse {
        let encodedDid = did.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? did
        return try await client.request(
            method: "PUT",
            url: "\(baseURL)/api/did/\(encodedDid)",
            body: request,
            responseType: RegisterDidResponse.self
        )
    }

    public     func deactivateDid(did: String, request: DeactivateDidRequest) async throws {
        let encodedDid = did.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? did
        try await client.requestVoid(
            method: "DELETE",
            url: "\(baseURL)/api/did/\(encodedDid)",
            body: request
        )
    }

    public     func createChallenge(did: String) async throws -> ChallengeResponse {
        let encodedDid = did.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? did
        return try await client.post(
            url: "\(baseURL)/api/did/\(encodedDid)/challenge",
            body: Empty(),
            responseType: ChallengeResponse.self
        )
    }

    // MARK: - Device Pairing

    public     func initPairing(did: String, request: PairingInitRequest) async throws -> PairingInitResponse {
        let encodedDid = did.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? did
        return try await client.post(
            url: "\(baseURL)/api/did/\(encodedDid)/pair",
            body: request,
            responseType: PairingInitResponse.self
        )
    }

    public     func joinPairing(did: String, pairingId: String, request: PairingJoinRequest) async throws -> PairingJoinResponse {
        let encodedDid = did.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? did
        return try await client.post(
            url: "\(baseURL)/api/did/\(encodedDid)/pair/\(pairingId)/join",
            body: request,
            responseType: PairingJoinResponse.self
        )
    }

    public     func getPairingStatus(did: String, pairingId: String) async throws -> PairingStatusResponse {
        let encodedDid = did.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? did
        return try await client.get(
            url: "\(baseURL)/api/did/\(encodedDid)/pair/\(pairingId)",
            responseType: PairingStatusResponse.self
        )
    }

    public     func approvePairing(did: String, pairingId: String, request: PairingApproveRequest) async throws {
        let encodedDid = did.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? did
        try await client.requestVoid(
            method: "POST",
            url: "\(baseURL)/api/did/\(encodedDid)/pair/\(pairingId)/approve",
            body: request
        )
    }
}
