import Foundation

enum DidError: Error, LocalizedError {
    case invalidFormat(String)

    var errorDescription: String? {
        switch self {
        case .invalidFormat(let reason):
            return "Invalid DID format: \(reason)"
        }
    }
}

struct Did {
    let value: String

    func keyId(keyIndex: Int = 1) -> String {
        "\(value)#key-\(keyIndex)"
    }

    func methodSpecificId() -> String {
        if value.hasPrefix("did:ssdid:") {
            return String(value.dropFirst("did:ssdid:".count))
        }
        return value
    }

    static func generate() -> Did {
        var bytes = [UInt8](repeating: 0, count: 16)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let id = Data(bytes).base64URLEncodedStringNoPadding()
        return Did(value: "did:ssdid:\(id)")
    }

    static func validate(_ value: String) throws -> Did {
        guard value.hasPrefix("did:ssdid:") else {
            throw DidError.invalidFormat("must start with 'did:ssdid:'")
        }
        let id = String(value.dropFirst("did:ssdid:".count))
        guard !id.isEmpty else {
            throw DidError.invalidFormat("method-specific ID must not be empty")
        }
        guard id.count >= 16 else {
            throw DidError.invalidFormat("method-specific ID too short (minimum 16 characters)")
        }
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-_"))
        guard id.unicodeScalars.allSatisfy({ allowed.contains($0) }) else {
            throw DidError.invalidFormat("method-specific ID contains invalid characters")
        }
        return Did(value: value)
    }

    static func fromKeyId(_ keyId: String) -> Did {
        let didPart = keyId.components(separatedBy: "#").first ?? keyId
        return Did(value: didPart)
    }
}

private extension Data {
    func base64URLEncodedStringNoPadding() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
