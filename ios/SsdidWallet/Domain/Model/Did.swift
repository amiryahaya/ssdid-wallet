import Foundation

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
