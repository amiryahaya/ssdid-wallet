import Foundation

public enum DidError: Error, LocalizedError {
    case invalidFormat(String)

    public var errorDescription: String? {
        switch self {
        case .invalidFormat(let reason):
            return "Invalid DID format: \(reason)"
        }
    }
}

public struct Did {
    public let value: String

    public func keyId(keyIndex: Int = 1) -> String {
        "\(value)#key-\(keyIndex)"
    }

    public func methodSpecificId() -> String {
        precondition(value.hasPrefix("did:ssdid:"), "methodSpecificId() called on malformed DID: \(value)")
        return String(value.dropFirst("did:ssdid:".count))
    }

    public static func generate() -> Did {
        var bytes = [UInt8](repeating: 0, count: 16)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let id = Data(bytes).base64URLEncodedStringNoPadding()
        return Did(value: "did:ssdid:\(id)")
    }

    public static func validate(_ value: String) throws -> Did {
        guard value.hasPrefix("did:ssdid:") else {
            throw DidError.invalidFormat("must start with 'did:ssdid:'")
        }
        let id = String(value.dropFirst("did:ssdid:".count))
        guard !id.isEmpty else {
            throw DidError.invalidFormat("method-specific ID must not be empty")
        }
        guard id.count >= 22 else {
            throw DidError.invalidFormat("method-specific ID too short (minimum 22 characters for 128-bit entropy)")
        }
        guard id.count <= 128 else {
            throw DidError.invalidFormat("method-specific ID too long")
        }
        // ASCII-only base64url characters (not Unicode alphanumerics)
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_")
        guard id.unicodeScalars.allSatisfy({ allowed.contains($0) }) else {
            throw DidError.invalidFormat("method-specific ID contains invalid characters")
        }
        return Did(value: value)
    }

    public static func fromKeyId(_ keyId: String) -> Did {
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
