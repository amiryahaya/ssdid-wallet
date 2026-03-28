import Foundation

/// Persistent storage for held (received) verifiable credentials.
public protocol CredentialRepository: Sendable {
    func saveCredential(_ credential: VerifiableCredential) async throws
    func getHeldCredentials() async -> [VerifiableCredential]
    func getUniqueIssuerDids() async -> [String]
    func deleteCredential(credentialId: String) async throws
}
