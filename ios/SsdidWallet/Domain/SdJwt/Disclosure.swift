import Foundation
import CryptoKit

struct Disclosure: Equatable {
    let salt: String
    let claimName: String
    let claimValue: String
    let encoded: String

    init(salt: String, claimName: String, claimValue: String, encoded: String = "") {
        self.salt = salt
        self.claimName = claimName
        self.claimValue = claimValue
        self.encoded = encoded
    }

    func encode() -> String {
        if !encoded.isEmpty { return encoded }
        let array = "[\"\(salt)\",\"\(claimName)\",\"\(claimValue)\"]"
        return Data(array.utf8).base64URLEncodedString()
    }

    func hash(algorithm: String = "sha-256") -> String {
        let input = encode()
        let digest = SHA256.hash(data: Data(input.utf8))
        return Data(digest).base64URLEncodedString()
    }

    static func decode(_ base64url: String) throws -> Disclosure {
        guard let data = Data(base64URLEncoded: base64url),
              let json = String(data: data, encoding: .utf8),
              let array = try? JSONSerialization.jsonObject(with: Data(json.utf8)) as? [Any],
              array.count >= 3,
              let salt = array[0] as? String,
              let claimName = array[1] as? String,
              let claimValue = array[2] as? String
        else {
            throw SdJwtError.invalidDisclosure
        }
        return Disclosure(salt: salt, claimName: claimName, claimValue: claimValue, encoded: base64url)
    }
}
