import Foundation

/// Facade for SSDID protocol flows (registration, authentication, transaction signing).
public struct FlowsApi {
    let client: SsdidClient

    public func registerWithService(identity: Identity, serviceUrl: String) async throws -> VerifiableCredential {
        try await client.registerWithService(identity: identity, serverUrl: serviceUrl)
    }

    public func authenticate(credential: VerifiableCredential, serviceUrl: String) async throws -> AuthenticateResponse {
        try await client.authenticate(credential: credential, serverUrl: serviceUrl)
    }

    public func fetchTransactionDetails(sessionToken: String, serverUrl: String) async throws -> [String: String] {
        try await client.fetchTransactionDetails(sessionToken: sessionToken, serverUrl: serverUrl)
    }

    public func signTransaction(
        sessionToken: String,
        identity: Identity,
        transaction: [String: String],
        serverUrl: String
    ) async throws -> TxSubmitResponse {
        try await client.signTransaction(
            sessionToken: sessionToken,
            identity: identity,
            transaction: transaction,
            serverUrl: serverUrl
        )
    }
}
