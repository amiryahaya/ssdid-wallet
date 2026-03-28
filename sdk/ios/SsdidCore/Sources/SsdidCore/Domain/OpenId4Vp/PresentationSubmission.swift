import Foundation

/// Describes how VP tokens map to input descriptors in a presentation definition.
public struct PresentationSubmission: Codable {
    public let id: String
    public let definitionId: String
    public let descriptorMap: [DescriptorMapEntry]

    enum CodingKeys: String, CodingKey {
        case id
        case definitionId = "definition_id"
        case descriptorMap = "descriptor_map"
    }

    /// Serializes this submission to a JSON string.
    public func toJson() throws -> String {
        let encoder = JSONEncoder()
        let data = try encoder.encode(self)
        guard let json = String(data: data, encoding: .utf8) else {
            throw OpenId4VpError.invalidRequest("Failed to encode PresentationSubmission")
        }
        return json
    }

    /// Creates a PresentationSubmission for SD-JWT VCs.
    public static func create(
        definitionId: String,
        descriptorIds: [String]
    ) -> PresentationSubmission {
        PresentationSubmission(
            id: UUID().uuidString,
            definitionId: definitionId,
            descriptorMap: descriptorIds.map { descId in
                DescriptorMapEntry(id: descId, format: "vc+sd-jwt", path: "$")
            }
        )
    }
}

/// A single entry in the descriptor_map of a PresentationSubmission.
public struct DescriptorMapEntry: Codable {
    public let id: String
    public let format: String
    public let path: String
}
