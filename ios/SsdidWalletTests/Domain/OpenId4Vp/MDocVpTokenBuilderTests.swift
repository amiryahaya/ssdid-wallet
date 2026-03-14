import XCTest
@testable import SsdidWallet

final class MDocVpTokenBuilderTests: XCTestCase {

    private func makeStoredMDoc() -> StoredMDoc {
        // Build a minimal IssuerSigned CBOR for testing
        let item1Map: [String: Any] = [
            "digestID": 0,
            "random": Data([0x01, 0x02, 0x03]),
            "elementIdentifier": "family_name",
            "elementValue": "Smith"
        ]
        let item2Map: [String: Any] = [
            "digestID": 1,
            "random": Data([0x04, 0x05, 0x06]),
            "elementIdentifier": "given_name",
            "elementValue": "John"
        ]

        let item1Bytes = CborCodec.encodeMap(item1Map)
        let item2Bytes = CborCodec.encodeMap(item2Map)

        // issuerAuth: minimal COSE_Sign1 = [bstr, {}, bstr, bstr]
        var issuerAuthBuffer = Data()
        issuerAuthBuffer.append(0x84) // array of 4
        issuerAuthBuffer.append(0x40) // empty bstr
        issuerAuthBuffer.append(0xa0) // empty map
        issuerAuthBuffer.append(0x40) // empty bstr (payload)
        issuerAuthBuffer.append(0x44) // bstr of 4 bytes (signature)
        issuerAuthBuffer.append(contentsOf: [0xAA, 0xBB, 0xCC, 0xDD])

        let issuerSignedMap: [String: Any] = [
            "nameSpaces": [
                "org.iso.18013.5.1": [item1Bytes, item2Bytes]
            ] as [String: Any],
            "issuerAuth": issuerAuthBuffer
        ]
        let issuerSignedCbor = CborCodec.encodeMap(issuerSignedMap)

        return StoredMDoc(
            id: "mdoc-1",
            docType: "org.iso.18013.5.1.mDL",
            issuerSignedCbor: issuerSignedCbor,
            deviceKeyId: "key-1",
            issuedAt: 1_700_000_000,
            nameSpaces: ["org.iso.18013.5.1": ["family_name", "given_name"]]
        )
    }

    func testBuildProducesBase64UrlString() {
        let mdoc = makeStoredMDoc()
        let dummySigner: (Data) -> Data = { _ in Data([0x01, 0x02, 0x03, 0x04]) }

        let result = MDocVpTokenBuilder.build(
            storedMDoc: mdoc,
            requestedElements: ["org.iso.18013.5.1": ["family_name"]],
            clientId: "https://verifier.example.com",
            responseUri: "https://verifier.example.com/response",
            nonce: "test-nonce",
            signer: dummySigner
        )

        XCTAssertNotNil(result)
        // Should be base64url encoded (no +, /, or =)
        if let token = result {
            XCTAssertFalse(token.contains("+"))
            XCTAssertFalse(token.contains("/"))
            XCTAssertFalse(token.contains("="))
            XCTAssertFalse(token.isEmpty)
        }
    }

    func testBuildReturnsNilForInvalidMDoc() {
        let invalidMDoc = StoredMDoc(
            id: "bad",
            docType: "test",
            issuerSignedCbor: Data([0xFF, 0xFF]), // invalid CBOR
            deviceKeyId: "key",
            issuedAt: 1_700_000_000
        )
        let dummySigner: (Data) -> Data = { _ in Data([0x01]) }

        let result = MDocVpTokenBuilder.build(
            storedMDoc: invalidMDoc,
            requestedElements: ["ns": ["elem"]],
            clientId: "client",
            responseUri: "https://example.com/resp",
            nonce: "nonce",
            signer: dummySigner
        )

        XCTAssertNil(result)
    }

    func testBuildResultDecodesToCborMap() {
        let mdoc = makeStoredMDoc()
        let dummySigner: (Data) -> Data = { _ in Data([0x01, 0x02, 0x03, 0x04]) }

        guard let result = MDocVpTokenBuilder.build(
            storedMDoc: mdoc,
            requestedElements: ["org.iso.18013.5.1": ["family_name"]],
            clientId: "https://verifier.example.com",
            responseUri: "https://verifier.example.com/response",
            nonce: "test-nonce",
            signer: dummySigner
        ) else {
            XCTFail("Expected non-nil result")
            return
        }

        // Decode from base64url
        var base64 = result
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        while base64.count % 4 != 0 {
            base64 += "="
        }
        guard let data = Data(base64Encoded: base64),
              let decoded = CborCodec.decodeMap(data) else {
            XCTFail("Failed to decode result as CBOR map")
            return
        }

        XCTAssertEqual(decoded["version"] as? String, "1.0")
        XCTAssertEqual(decoded["status"] as? Int, 0)
        XCTAssertNotNil(decoded["documents"])
    }
}
