import Foundation
import CryptoKit

/// Minimal CBOR codec for mdoc data elements.
///
/// This provides basic CBOR encoding/decoding for the subset of types used by
/// ISO 18013-5 mdoc structures. A full-featured CBOR library (e.g. SwiftCBOR)
/// should replace this once added as an SPM dependency.
enum CborCodec {

    // MARK: - Major type constants

    private static let majorUnsigned: UInt8 = 0x00      // 0
    private static let majorNegative: UInt8 = 0x20      // 1
    private static let majorBytes: UInt8    = 0x40      // 2
    private static let majorText: UInt8     = 0x60      // 3
    private static let majorArray: UInt8    = 0x80      // 4
    private static let majorMap: UInt8      = 0xa0      // 5
    private static let majorTag: UInt8      = 0xc0      // 6

    // MARK: - Encode

    /// Encode an arbitrary Swift value to CBOR bytes.
    static func encodeDataElement(_ value: Any) -> Data {
        var buffer = Data()
        encode(value, into: &buffer)
        return buffer
    }

    /// Encode a `[String: Any]` map to CBOR bytes.
    static func encodeMap(_ map: [String: Any]) -> Data {
        var buffer = Data()
        encodeMapValue(map, into: &buffer)
        return buffer
    }

    private static func encode(_ value: Any, into buffer: inout Data) {
        switch value {
        case let s as String:
            encodeText(s, into: &buffer)
        case let i as Int:
            encodeInt(i, into: &buffer)
        case let i64 as Int64:
            encodeInt(Int(i64), into: &buffer)
        case let b as Bool:
            buffer.append(b ? 0xf5 : 0xf4)
        case let d as Data:
            encodeBytes(d, into: &buffer)
        case let arr as [Any]:
            encodeLength(majorArray, count: arr.count, into: &buffer)
            for item in arr {
                encode(item, into: &buffer)
            }
        case let dict as [String: Any]:
            encodeMapValue(dict, into: &buffer)
        default:
            encodeText(String(describing: value), into: &buffer)
        }
    }

    private static func encodeMapValue(_ dict: [String: Any], into buffer: inout Data) {
        let sortedKeys = dict.keys.sorted()
        encodeLength(majorMap, count: sortedKeys.count, into: &buffer)
        for key in sortedKeys {
            encodeText(key, into: &buffer)
            encode(dict[key]!, into: &buffer)
        }
    }

    private static func encodeText(_ text: String, into buffer: inout Data) {
        let utf8 = Data(text.utf8)
        encodeLength(majorText, count: utf8.count, into: &buffer)
        buffer.append(utf8)
    }

    private static func encodeBytes(_ bytes: Data, into buffer: inout Data) {
        encodeLength(majorBytes, count: bytes.count, into: &buffer)
        buffer.append(bytes)
    }

    private static func encodeInt(_ value: Int, into buffer: inout Data) {
        if value >= 0 {
            encodeLength(majorUnsigned, count: value, into: &buffer)
        } else {
            encodeLength(majorNegative, count: -1 - value, into: &buffer)
        }
    }

    private static func encodeLength(_ major: UInt8, count: Int, into buffer: inout Data) {
        if count < 24 {
            buffer.append(major | UInt8(count))
        } else if count <= UInt8.max {
            buffer.append(major | 24)
            buffer.append(UInt8(count))
        } else if count <= UInt16.max {
            buffer.append(major | 25)
            var be = UInt16(count).bigEndian
            buffer.append(Data(bytes: &be, count: 2))
        } else if count <= UInt32.max {
            buffer.append(major | 26)
            var be = UInt32(count).bigEndian
            buffer.append(Data(bytes: &be, count: 4))
        } else {
            buffer.append(major | 27)
            var be = UInt64(count).bigEndian
            buffer.append(Data(bytes: &be, count: 8))
        }
    }

    // MARK: - Decode

    /// Decode CBOR bytes into a Swift value.
    static func decodeDataElement(_ data: Data) -> Any? {
        var offset = 0
        return decodeItem(data, offset: &offset)
    }

    /// Decode CBOR bytes into a `[String: Any]` map.
    static func decodeMap(_ data: Data) -> [String: Any]? {
        guard let value = decodeDataElement(data) as? [String: Any] else { return nil }
        return value
    }

    private static func decodeItem(_ data: Data, offset: inout Int) -> Any? {
        guard offset < data.count else { return nil }
        let initial = data[offset]
        let major = initial & 0xe0
        let additional = initial & 0x1f
        offset += 1

        switch major {
        case majorUnsigned:
            return decodeUnsigned(data, additional: additional, offset: &offset)

        case majorNegative:
            guard let raw = decodeUnsigned(data, additional: additional, offset: &offset) else { return nil }
            return -1 - raw

        case majorBytes:
            guard let length = decodeUnsigned(data, additional: additional, offset: &offset) else { return nil }
            guard offset + length <= data.count else { return nil }
            let bytes = data[offset..<(offset + length)]
            offset += length
            return Data(bytes)

        case majorText:
            guard let length = decodeUnsigned(data, additional: additional, offset: &offset) else { return nil }
            guard offset + length <= data.count else { return nil }
            let text = String(data: data[offset..<(offset + length)], encoding: .utf8) ?? ""
            offset += length
            return text

        case majorArray:
            guard let count = decodeUnsigned(data, additional: additional, offset: &offset) else { return nil }
            var array = [Any]()
            for _ in 0..<count {
                guard let item = decodeItem(data, offset: &offset) else { return nil }
                array.append(item)
            }
            return array

        case majorMap:
            guard let count = decodeUnsigned(data, additional: additional, offset: &offset) else { return nil }
            var map = [String: Any]()
            for _ in 0..<count {
                guard let key = decodeItem(data, offset: &offset) else { return nil }
                guard let value = decodeItem(data, offset: &offset) else { return nil }
                if let keyStr = key as? String {
                    map[keyStr] = value
                } else if let keyInt = key as? Int {
                    map[String(keyInt)] = value
                }
            }
            return map

        case majorTag:
            // Skip tag number, decode the inner item
            _ = decodeUnsigned(data, additional: additional, offset: &offset)
            return decodeItem(data, offset: &offset)

        default:
            // Simple values
            if initial == 0xf4 { return false }
            if initial == 0xf5 { return true }
            if initial == 0xf6 { return nil }
            return nil
        }
    }

    private static func decodeUnsigned(_ data: Data, additional: UInt8, offset: inout Int) -> Int? {
        if additional < 24 {
            return Int(additional)
        } else if additional == 24 {
            guard offset < data.count else { return nil }
            let val = Int(data[offset])
            offset += 1
            return val
        } else if additional == 25 {
            guard offset + 2 <= data.count else { return nil }
            let val = Int(data[offset]) << 8 | Int(data[offset + 1])
            offset += 2
            return val
        } else if additional == 26 {
            guard offset + 4 <= data.count else { return nil }
            var val = 0
            for i in 0..<4 { val = val << 8 | Int(data[offset + i]) }
            offset += 4
            return val
        } else if additional == 27 {
            guard offset + 8 <= data.count else { return nil }
            var val = 0
            for i in 0..<8 { val = val << 8 | Int(data[offset + i]) }
            offset += 8
            return val
        }
        return nil
    }

    // MARK: - Digest helper

    /// Compute SHA-256 digest of CBOR-encoded IssuerSignedItem, matching ISO 18013-5.
    static func digestIssuerSignedItem(_ item: IssuerSignedItem, algorithm: String = "SHA-256") -> Data? {
        let itemMap: [String: Any] = [
            "digestID": item.digestId,
            "random": item.random,
            "elementIdentifier": item.elementIdentifier,
            "elementValue": item.elementValue
        ]
        let encoded = encodeMap(itemMap)

        switch algorithm {
        case "SHA-256":
            return Data(SHA256.hash(data: encoded))
        case "SHA-384":
            return Data(SHA384.hash(data: encoded))
        case "SHA-512":
            return Data(SHA512.hash(data: encoded))
        default:
            return nil
        }
    }
}
