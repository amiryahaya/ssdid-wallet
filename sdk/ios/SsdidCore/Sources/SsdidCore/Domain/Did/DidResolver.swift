import Foundation

public protocol DidResolver {
    func resolve(did: String) async throws -> DidDocument
}
