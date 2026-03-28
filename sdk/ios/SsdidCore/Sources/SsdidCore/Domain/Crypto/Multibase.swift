import Foundation

/// Multibase encoding/decoding supporting Base64URL (u prefix) and Base58BTC (z prefix).
/// Default encoding uses Base64URL no-padding (u prefix).
public enum Multibase {

    enum MultibaseError: Error, LocalizedError {
        case emptyInput
        case unsupportedPrefix(Character)
        case decodingFailed(String)

        var errorDescription: String? {
            switch self {
            case .emptyInput:
                return "Multibase string is empty"
            case .unsupportedPrefix(let prefix):
                return "Unsupported multibase prefix: \(prefix)"
            case .decodingFailed(let reason):
                return "Multibase decoding failed: \(reason)"
            }
        }
    }

    // MARK: - Encode

    /// Encodes data using Base64URL no-padding with `u` prefix (default).
    public static func encode(_ data: Data) -> String {
        let base64url = data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        return "u\(base64url)"
    }

    /// Encodes data using Base58BTC with `z` prefix.
    public static func encodeBase58(_ data: Data) -> String {
        return "z\(Base58.encode(data))"
    }

    // MARK: - Decode

    /// Decodes a multibase-encoded string. Supports `u` (Base64URL) and `z` (Base58BTC).
    public static func decode(_ encoded: String) throws -> Data {
        guard let prefix = encoded.first else {
            throw MultibaseError.emptyInput
        }

        let payload = String(encoded.dropFirst())

        switch prefix {
        case "u":
            return try decodeBase64URL(payload)
        case "z":
            return try decodeBase58(payload)
        default:
            throw MultibaseError.unsupportedPrefix(prefix)
        }
    }

    // MARK: - Private

    private static func decodeBase64URL(_ string: String) throws -> Data {
        // Convert base64url to standard base64
        var base64 = string
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")

        // Add padding if needed
        let remainder = base64.count % 4
        if remainder > 0 {
            base64 += String(repeating: "=", count: 4 - remainder)
        }

        guard let data = Data(base64Encoded: base64) else {
            throw MultibaseError.decodingFailed("Invalid Base64URL encoding")
        }
        return data
    }

    private static func decodeBase58(_ string: String) throws -> Data {
        guard let data = Base58.decode(string) else {
            throw MultibaseError.decodingFailed("Invalid Base58BTC encoding")
        }
        return data
    }
}

// MARK: - Base58 (Bitcoin alphabet)

/// Minimal Base58 implementation using the Bitcoin alphabet.
public enum Base58 {

    private static let alphabet = Array("123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz")

    private static let baseCount = UInt(alphabet.count) // 58

    /// Encodes raw bytes to a Base58 string.
    public static func encode(_ data: Data) -> String {
        let bytes = [UInt8](data)

        // Count leading zeros
        var leadingZeros = 0
        for byte in bytes {
            if byte == 0 { leadingZeros += 1 }
            else { break }
        }

        // Convert to base58
        var result: [Character] = []
        var num = bytes.map { UInt($0) }

        while !num.isEmpty {
            var carry: UInt = 0
            var newNum: [UInt] = []

            for digit in num {
                let value = carry * 256 + digit
                let quotient = value / baseCount
                carry = value % baseCount

                if !newNum.isEmpty || quotient > 0 {
                    newNum.append(quotient)
                }
            }

            result.insert(alphabet[Int(carry)], at: 0)
            num = newNum
        }

        // Add leading '1's for each leading zero byte
        let prefix = String(repeating: "1", count: leadingZeros)
        return prefix + String(result)
    }

    /// Decodes a Base58 string to raw bytes. Returns nil on invalid input.
    public static func decode(_ string: String) -> Data? {
        let chars = Array(string)

        // Count leading '1's
        var leadingOnes = 0
        for char in chars {
            if char == "1" { leadingOnes += 1 }
            else { break }
        }

        // Build reverse lookup
        var alphabetMap: [Character: UInt] = [:]
        for (index, char) in alphabet.enumerated() {
            alphabetMap[char] = UInt(index)
        }

        // Convert from base58
        var result: [UInt] = []

        for char in chars {
            guard let value = alphabetMap[char] else {
                return nil // Invalid character
            }

            var carry = value
            var i = result.count - 1

            while i >= 0 || carry > 0 {
                if i >= 0 {
                    carry += result[i] * baseCount
                    result[i] = carry % 256
                    carry /= 256
                    i -= 1
                } else {
                    result.insert(carry % 256, at: 0)
                    carry /= 256
                }
            }
        }

        // Add leading zero bytes
        let leadingZeros = [UInt](repeating: 0, count: leadingOnes)
        let bytes = (leadingZeros + result).map { UInt8($0) }
        return Data(bytes)
    }
}
