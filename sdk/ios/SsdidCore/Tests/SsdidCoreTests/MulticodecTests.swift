import XCTest
@testable import SsdidCore

final class MulticodecTests: XCTestCase {

    func testDecodeEd25519Prefix() throws {
        // Ed25519 multicodec prefix is 0xed (single byte, high bit set)
        // Varint encoding: 0xed -> two bytes: 0xed & 0x7f | 0x80 = 0xed, 0x01
        // Actually 0xed as varint: first byte = 0xed (high bit set), second = 0x01
        // codec = (0xed & 0x7f) | (0x01 << 7) = 0x6d | 0x80 = 0xed
        let keyBytes = Data([0x01, 0x02, 0x03, 0x04])
        var encoded = Data([0xed, 0x01])  // varint for 0xed
        encoded.append(keyBytes)
        let (codec, decoded) = try Multicodec.decode(encoded)
        XCTAssertEqual(codec, Multicodec.ed25519Pub)
        XCTAssertEqual(decoded, keyBytes)
    }

    func testDecodeP256Prefix() throws {
        // P-256 multicodec prefix is 0x1200
        // Varint encoding: 0x1200 -> first byte = (0x1200 & 0x7f) | 0x80 = 0x80, second = 0x1200 >> 7 = 0x24
        let keyBytes = Data([0xAA, 0xBB])
        var encoded = Data([0x80, 0x24])  // varint for 0x1200
        encoded.append(keyBytes)
        let (codec, decoded) = try Multicodec.decode(encoded)
        XCTAssertEqual(codec, Multicodec.p256Pub)
        XCTAssertEqual(decoded, keyBytes)
    }

    func testDecodeP384Prefix() throws {
        // P-384 multicodec prefix is 0x1201
        // Varint: first byte = (0x1201 & 0x7f) | 0x80 = 0x81, second = 0x1201 >> 7 = 0x24
        let keyBytes = Data([0xCC, 0xDD, 0xEE])
        var encoded = Data([0x81, 0x24])  // varint for 0x1201
        encoded.append(keyBytes)
        let (codec, decoded) = try Multicodec.decode(encoded)
        XCTAssertEqual(codec, Multicodec.p384Pub)
        XCTAssertEqual(decoded, keyBytes)
    }

    func testDecodeSingleByteCodec() throws {
        // A codec value < 0x80 uses a single byte (high bit not set)
        let keyBytes = Data([0x01, 0x02])
        var encoded = Data([0x01])  // codec = 1
        encoded.append(keyBytes)
        let (codec, decoded) = try Multicodec.decode(encoded)
        XCTAssertEqual(codec, 0x01)
        XCTAssertEqual(decoded, keyBytes)
    }

    func testDecodeRejectsEmptyInput() {
        XCTAssertThrowsError(try Multicodec.decode(Data())) { error in
            guard case DidResolutionError.dataTooShort = error else {
                XCTFail("Expected dataTooShort, got \(error)")
                return
            }
        }
    }

    func testDecodeRejectsSingleByte() {
        // Single byte with high bit set needs a second byte
        XCTAssertThrowsError(try Multicodec.decode(Data([0x80]))) { error in
            guard case DidResolutionError.dataTooShort = error else {
                XCTFail("Expected dataTooShort, got \(error)")
                return
            }
        }
    }
}
