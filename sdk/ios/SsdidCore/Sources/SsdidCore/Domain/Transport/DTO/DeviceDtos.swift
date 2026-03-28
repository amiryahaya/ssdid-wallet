import Foundation

/// Request/response DTOs for device pairing operations.

public struct PairingInitRequest: Codable {
    public let did: String
    public let challenge: String
    public let primaryKeyId: String

    enum CodingKeys: String, CodingKey {
        case did, challenge
        case primaryKeyId = "primary_key_id"
    }
}

public struct PairingInitResponse: Codable {
    public let pairingId: String

    enum CodingKeys: String, CodingKey {
        case pairingId = "pairing_id"
    }
}

public struct PairingJoinRequest: Codable {
    public let pairingId: String
    public let publicKey: String
    public let signedChallenge: String
    public let deviceName: String
    public let platform: String

    enum CodingKeys: String, CodingKey {
        case pairingId = "pairing_id"
        case publicKey = "public_key"
        case signedChallenge = "signed_challenge"
        case deviceName = "device_name"
        case platform
    }
}

public struct PairingJoinResponse: Codable {
    public let status: String
}

public struct PairingApproveRequest: Codable {
    public let did: String
    public let keyId: String
    public let signedApproval: String

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
