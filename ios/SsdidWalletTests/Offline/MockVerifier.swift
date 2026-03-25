import Foundation
@testable import SsdidWallet

final class MockVerifier: Verifier {
    var shouldThrow: Error?
    var shouldReturn: Bool
    var verifyCallCount = 0

    init(shouldThrow: Error? = URLError(.notConnectedToInternet), shouldReturn: Bool = true) {
        self.shouldThrow = shouldThrow
        self.shouldReturn = shouldReturn
    }

    func resolveDid(did: String) async throws -> DidDocument {
        if let error = shouldThrow { throw error }
        throw NSError(domain: "MockVerifier", code: 0, userInfo: [NSLocalizedDescriptionKey: "Not implemented"])
    }

    func verifySignature(did: String, keyId: String, signature: Data, data: Data) async throws -> Bool {
        if let error = shouldThrow { throw error }
        return shouldReturn
    }

    func verifyChallengeResponse(did: String, keyId: String, challenge: String, signedChallenge: String) async throws -> Bool {
        if let error = shouldThrow { throw error }
        return shouldReturn
    }

    func verifyCredential(credential: VerifiableCredential) async throws -> Bool {
        verifyCallCount += 1
        if let error = shouldThrow { throw error }
        return shouldReturn
    }
}
