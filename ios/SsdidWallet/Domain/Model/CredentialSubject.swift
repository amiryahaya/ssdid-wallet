import Foundation

struct CredentialSubject: Codable {
    let id: String
    var claims: [String: String] = [:]
    var additionalProperties: [String: AnyCodable] = [:]

    enum CodingKeys: String, CodingKey {
        case id
        case claims
    }

    /// Dynamic coding key for encoding/decoding unknown fields.
    private struct DynamicKey: CodingKey {
        var stringValue: String
        var intValue: Int? { nil }

        init(stringValue: String) {
            self.stringValue = stringValue
        }

        init?(intValue: Int) {
            return nil
        }
    }

    init(id: String, claims: [String: String] = [:], additionalProperties: [String: AnyCodable] = [:]) {
        self.id = id
        self.claims = claims
        self.additionalProperties = additionalProperties
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: DynamicKey.self)

        // Extract "id"
        id = (try? container.decode(String.self, forKey: DynamicKey(stringValue: "id"))) ?? ""

        // Extract "claims" if present
        claims = (try? container.decode([String: String].self, forKey: DynamicKey(stringValue: "claims"))) ?? [:]

        // Everything else goes into additionalProperties
        var extra: [String: AnyCodable] = [:]
        for key in container.allKeys where key.stringValue != "id" && key.stringValue != "claims" {
            let value = try container.decode(AnyCodable.self, forKey: key)
            extra[key.stringValue] = value
        }
        additionalProperties = extra
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: DynamicKey.self)

        // Always encode id
        try container.encode(id, forKey: DynamicKey(stringValue: "id"))

        // Encode claims only if non-empty
        if !claims.isEmpty {
            try container.encode(claims, forKey: DynamicKey(stringValue: "claims"))
        }

        // Merge additional properties back into the top level
        for (key, value) in additionalProperties {
            try container.encode(value, forKey: DynamicKey(stringValue: key))
        }
    }
}
