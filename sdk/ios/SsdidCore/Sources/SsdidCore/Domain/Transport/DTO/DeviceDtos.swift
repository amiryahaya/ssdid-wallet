import Foundation

/// Request/response DTOs for device pairing operations.

struct PairingInitRequest: Codable {
    let did: String
    let challenge: String
    let primaryKeyId: String

    enum CodingKeys: String, CodingKey {
        case did, challenge
        case primaryKeyId = "primary_key_id"
    }
}

struct PairingInitResponse: Codable {
    let pairingId: String

    enum CodingKeys: String, CodingKey {
        case pairingId = "pairing_id"
    }
}

struct PairingJoinRequest: Codable {
    let pairingId: String
    let publicKey: String
    let signedChallenge: String
    let deviceName: String
    let platform: String

    enum CodingKeys: String, CodingKey {
        case pairingId = "pairing_id"
        case publicKey = "public_key"
        case signedChallenge = "signed_challenge"
        case deviceName = "device_name"
        case platform
    }
}

struct PairingJoinResponse: Codable {
    let status: String
}

struct PairingApproveRequest: Codable {
    let did: String
    let keyId: String
    let signedApproval: String

    enum CodingKeys: String, CodingKey {
        case did
        case keyId = "key_id"
        case signedApproval = "signed_approval"
    }
}

public struct PairingStatusResponse: Codable {
    public let status: String
    public var deviceName: String? = nil
    public var publicKey: String? = nil
    public var signedChallenge: String? = nil

    enum CodingKeys: String, CodingKey {
        case status
        case deviceName = "device_name"
        case publicKey = "public_key"
        case signedChallenge = "signed_challenge"
    }
}
