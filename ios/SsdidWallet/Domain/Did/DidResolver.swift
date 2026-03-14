import Foundation

protocol DidResolver {
    func resolve(did: String) async throws -> DidDocument
}
