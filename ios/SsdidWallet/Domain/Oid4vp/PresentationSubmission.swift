import Foundation

struct PresentationSubmission: Codable {
    let id: String
    let definitionId: String
    let descriptorMap: [DescriptorMapEntry]

    enum CodingKeys: String, CodingKey {
        case id
        case definitionId = "definition_id"
        case descriptorMap = "descriptor_map"
    }

    func toJson() throws -> String {
        let data = try JSONEncoder().encode(self)
        return String(data: data, encoding: .utf8) ?? "{}"
    }
}

struct DescriptorMapEntry: Codable {
    let id: String
    let format: String
    let path: String
}
