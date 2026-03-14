import Foundation

/// DIDComm v2 plaintext message envelope, mirroring the Android model.
/// Conforms to DIDComm Messaging v2 specification.
struct DIDCommMessage: Codable, Equatable {
    let id: String
    let type: String
    let from: String?
    let to: [String]
    let createdTime: Int64?
    let body: [String: AnyCodable]
    let attachments: [DIDCommAttachment]

    enum CodingKeys: String, CodingKey {
        case id, type, from, to
        case createdTime = "created_time"
        case body, attachments
    }

    init(
        id: String,
        type: String,
        from: String? = nil,
        to: [String],
        createdTime: Int64? = nil,
        body: [String: AnyCodable] = [:],
        attachments: [DIDCommAttachment] = []
    ) {
        self.id = id
        self.type = type
        self.from = from
        self.to = to
        self.createdTime = createdTime
        self.body = body
        self.attachments = attachments
    }
}

/// Attachment within a DIDComm message.
struct DIDCommAttachment: Codable, Equatable {
    let id: String
    let mediaType: String?
    let data: DIDCommAttachmentData

    enum CodingKeys: String, CodingKey {
        case id
        case mediaType = "media_type"
        case data
    }

    init(id: String, mediaType: String? = nil, data: DIDCommAttachmentData) {
        self.id = id
        self.mediaType = mediaType
        self.data = data
    }
}

/// Data payload for a DIDComm attachment.
struct DIDCommAttachmentData: Codable, Equatable {
    let base64: String?
    let json: [String: AnyCodable]?

    init(base64: String? = nil, json: [String: AnyCodable]? = nil) {
        self.base64 = base64
        self.json = json
    }
}
