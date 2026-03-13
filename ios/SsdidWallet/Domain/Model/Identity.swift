import Foundation

struct Identity: Codable, Identifiable {
    let name: String
    let did: String
    let keyId: String
    let algorithm: Algorithm
    let publicKeyMultibase: String
    let createdAt: String
    var isActive: Bool = true
    var recoveryKeyId: String? = nil
    var hasRecoveryKey: Bool = false
    var preRotatedKeyId: String? = nil

    var id: String { keyId }
}
