import Foundation
import CryptoKit

public struct Disclosure: Equatable {
    public let salt: String
    public let claimName: String
    public let claimValue: Any  // RFC 9901: any JSON type
    public let encoded: String

    public static func == (lhs: Disclosure, rhs: Disclosure) -> Bool {
        lhs.salt == rhs.salt && lhs.claimName == rhs.claimName
    }

    public init(salt: String, claimName: String, claimValue: Any, encoded: String = "") {
        self.salt = salt
        self.claimName = claimName
        self.claimValue = claimValue
        self.encoded = encoded
    }

    public func encode() throws -> String {
        if !encoded.isEmpty { return encoded }
        let array: [Any] = [salt, claimName, claimValue]
        let data = try JSONSerialization.data(withJSONObject: array, options: [.withoutEscapingSlashes])
        return data.base64URLEncodedString()
    }

    func hash(algorithm: String = "sha-256") throws -> String {
        guard algorithm == "sha-256" else {
            throw SdJwtError.unsupportedAlgorithm(algorithm)
        }
        let input = try encode()
        let digest = SHA256.hash(data: Data(input.utf8))
        return Data(digest).base64URLEncodedString()
    }

    public static func decode(_ base64url: String) throws -> Disclosure {
        guard let data = Data(base64URLEncoded: base64url),
              let json = String(data: data, encoding: .utf8),
              let array = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [Any],
              array.count >= 3,
              let salt = array[0] as? String,
              let claimName = array[1] as? String
        else {
            throw SdJwtError.invalidDisclosure
        }
        let claimValue = array[2]
        return Disclosure(salt: salt, claimName: claimName, claimValue: claimValue, encoded: base64url)
    }
}
