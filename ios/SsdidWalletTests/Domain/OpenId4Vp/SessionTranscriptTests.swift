import XCTest
@testable import SsdidWallet

final class SessionTranscriptTests: XCTestCase {

    func testBuildProducesValidCborArray() {
        let result = SessionTranscript.build(
            clientId: "https://verifier.example.com",
            responseUri: "https://verifier.example.com/response",
            nonce: "test-nonce-123"
        )

        // Result should be valid CBOR
        XCTAssertFalse(result.isEmpty)

        // Decode the CBOR to verify structure
        guard let decoded = CborCodec.decodeDataElement(result) as? [Any] else {
            XCTFail("Failed to decode SessionTranscript as CBOR array")
            return
        }

        // Should be array of 3 elements: [null, null, handover]
        XCTAssertEqual(decoded.count, 3)

        // Third element should be the handover array with [clientId, responseUri, nonce]
        guard let handover = decoded[2] as? [Any] else {
            XCTFail("Third element should be handover array")
            return
        }
        XCTAssertEqual(handover.count, 3)
        XCTAssertEqual(handover[0] as? String, "https://verifier.example.com")
        XCTAssertEqual(handover[1] as? String, "https://verifier.example.com/response")
        XCTAssertEqual(handover[2] as? String, "test-nonce-123")
    }

    func testBuildIsDeterministic() {
        let result1 = SessionTranscript.build(
            clientId: "client1",
            responseUri: "https://example.com/resp",
            nonce: "nonce1"
        )
        let result2 = SessionTranscript.build(
            clientId: "client1",
            responseUri: "https://example.com/resp",
            nonce: "nonce1"
        )
        XCTAssertEqual(result1, result2)
    }

    func testBuildDiffersWithDifferentInputs() {
        let result1 = SessionTranscript.build(
            clientId: "client1",
            responseUri: "https://example.com/resp",
            nonce: "nonce1"
        )
        let result2 = SessionTranscript.build(
            clientId: "client2",
            responseUri: "https://example.com/resp",
            nonce: "nonce1"
        )
        XCTAssertNotEqual(result1, result2)
    }
}
