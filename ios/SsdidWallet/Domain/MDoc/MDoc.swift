import Foundation

/// Persistent metadata for a stored mdoc / mDL credential.
struct StoredMDoc: Codable, Identifiable, Equatable {
    let id: String
    let docType: String
    let issuerSignedCbor: Data
    let deviceKeyId: String
    let issuedAt: Int64
    let expiresAt: Int64?
    let nameSpaces: [String: [String]]

    init(
        id: String,
        docType: String,
        issuerSignedCbor: Data,
        deviceKeyId: String,
        issuedAt: Int64,
        expiresAt: Int64? = nil,
        nameSpaces: [String: [String]] = [:]
    ) {
        self.id = id
        self.docType = docType
        self.issuerSignedCbor = issuerSignedCbor
        self.deviceKeyId = deviceKeyId
        self.issuedAt = issuedAt
        self.expiresAt = expiresAt
        self.nameSpaces = nameSpaces
    }

    static func == (lhs: StoredMDoc, rhs: StoredMDoc) -> Bool {
        lhs.id == rhs.id
    }
}

/// Decoded IssuerSigned structure from ISO 18013-5.
struct IssuerSigned: Equatable {
    let nameSpaces: [String: [IssuerSignedItem]]
    let issuerAuth: Data

    static func == (lhs: IssuerSigned, rhs: IssuerSigned) -> Bool {
        lhs.nameSpaces == rhs.nameSpaces && lhs.issuerAuth == rhs.issuerAuth
    }
}

/// A single data element within a namespace, wrapped with random salt and digest ID.
struct IssuerSignedItem: Equatable {
    let digestId: Int
    let random: Data
    let elementIdentifier: String
    let elementValue: Any

    static func == (lhs: IssuerSignedItem, rhs: IssuerSignedItem) -> Bool {
        lhs.digestId == rhs.digestId && lhs.elementIdentifier == rhs.elementIdentifier
    }
}

/// Mobile Security Object — the signed payload inside issuerAuth (COSE_Sign1).
struct MobileSecurityObject {
    let version: String
    let digestAlgorithm: String
    let valueDigests: [String: [Int: Data]]
    let deviceKeyInfo: DeviceKeyInfo
    let validityInfo: ValidityInfo
}

/// COSE key identifying the device that holds the mdoc.
struct DeviceKeyInfo: Equatable {
    let deviceKey: Data
}

/// Validity window for the MSO.
struct ValidityInfo: Equatable {
    let signed: String
    let validFrom: String
    let validUntil: String
}
