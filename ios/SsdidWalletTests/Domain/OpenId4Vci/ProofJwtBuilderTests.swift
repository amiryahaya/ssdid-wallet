import XCTest
@testable import SsdidWallet

final class ProofJwtBuilderTests: XCTestCase {

    /// Dummy signer that returns a fixed-length byte array (simulates a signature).
    private let signer: (Data) -> Data = { data in Data(repeating: 0, count: 64) }

    func testBuildProofJwtStructure() throws {
        let jwt = try ProofJwtBuilder.build(
            algorithm: "EdDSA",
            keyId: "did:ssdid:holder1#key-1",
            walletDid: "did:ssdid:holder1",
            issuerUrl: "https://issuer.example.com",
            nonce: "c-nonce-1",
            signer: signer,
            issuedAt: 1_700_000_000
        )

        let parts = jwt.split(separator: ".")
        XCTAssertEqual(parts.count, 3)

        // Decode header
        let headerJson = try decodeBase64Url(String(parts[0]))
        XCTAssertTrue(headerJson.contains("openid4vci-proof+jwt"))
        XCTAssertTrue(headerJson.contains("EdDSA"))
        XCTAssertTrue(headerJson.contains("did:ssdid:holder1#key-1"))

        // Decode payload
        let payloadJson = try decodeBase64Url(String(parts[1]))
        XCTAssertTrue(payloadJson.contains("did:ssdid:holder1"))
        XCTAssertTrue(payloadJson.contains("https://issuer.example.com"))
        XCTAssertTrue(payloadJson.contains("c-nonce-1"))
        XCTAssertTrue(payloadJson.contains("1700000000"))
    }

    func testProofJwtContainsCorrectFields() throws {
        let jwt = try ProofJwtBuilder.build(
            algorithm: "ES256",
            keyId: "did:ssdid:h#k-1",
            walletDid: "did:ssdid:h",
            issuerUrl: "https://iss.example.com",
            nonce: "n-1",
            signer: signer,
            issuedAt: 1_700_000_000
        )
        XCTAssertEqual(jwt.split(separator: ".").count, 3)

        let parts = jwt.split(separator: ".")
        let headerJson = try decodeBase64Url(String(parts[0]))
        XCTAssertTrue(headerJson.contains("ES256"))
    }

    func testSignatureUsesHeaderDotPayloadAsInput() throws {
        var capturedInput: Data?
        let capturingSigner: (Data) -> Data = { data in
            capturedInput = data
            return Data(repeating: 0, count: 32)
        }

        let jwt = try ProofJwtBuilder.build(
            algorithm: "EdDSA",
            keyId: "did:ssdid:x#k",
            walletDid: "did:ssdid:x",
            issuerUrl: "https://iss.example.com",
            nonce: "n",
            signer: capturingSigner,
            issuedAt: 1_700_000_000
        )

        let parts = jwt.split(separator: ".")
        let expectedInput = "\(parts[0]).\(parts[1])"
        let actualInput = String(data: capturedInput!, encoding: .utf8)
        XCTAssertEqual(actualInput, expectedInput)
    }

    func testHeaderContainsTypField() throws {
        let jwt = try ProofJwtBuilder.build(
            algorithm: "EdDSA",
            keyId: "did:ssdid:test#k1",
            walletDid: "did:ssdid:test",
            issuerUrl: "https://issuer.example.com",
            nonce: "test-nonce",
            signer: signer,
            issuedAt: 1_700_000_000
        )

        let parts = jwt.split(separator: ".")
        let headerJson = try decodeBase64Url(String(parts[0]))
        XCTAssertTrue(headerJson.contains("\"typ\""))
        XCTAssertTrue(headerJson.contains("openid4vci-proof+jwt"))
    }

    func testPayloadContainsAudAndIss() throws {
        let jwt = try ProofJwtBuilder.build(
            algorithm: "EdDSA",
            keyId: "did:ssdid:holder#k",
            walletDid: "did:ssdid:holder",
            issuerUrl: "https://my-issuer.example.com",
            nonce: "nonce-abc",
            signer: signer,
            issuedAt: 1_700_000_000
        )

        let parts = jwt.split(separator: ".")
        let payloadJson = try decodeBase64Url(String(parts[1]))
        XCTAssertTrue(payloadJson.contains("\"iss\""))
        XCTAssertTrue(payloadJson.contains("\"aud\""))
        XCTAssertTrue(payloadJson.contains("\"iat\""))
        XCTAssertTrue(payloadJson.contains("\"nonce\""))
        XCTAssertTrue(payloadJson.contains("did:ssdid:holder"))
        XCTAssertTrue(payloadJson.contains("https://my-issuer.example.com"))
    }

    func testPayloadContainsExpClaim() throws {
        let iat: Int64 = 1_700_000_000
        let jwt = try ProofJwtBuilder.build(
            algorithm: "EdDSA",
            keyId: "did:ssdid:holder#k",
            walletDid: "did:ssdid:holder",
            issuerUrl: "https://issuer.example.com",
            nonce: "nonce-exp",
            signer: signer,
            issuedAt: iat
        )

        let parts = jwt.split(separator: ".")
        let payloadJson = try decodeBase64Url(String(parts[1]))
        XCTAssertTrue(payloadJson.contains("\"exp\""))
        // exp should be iat + 120
        XCTAssertTrue(payloadJson.contains("\(iat + 120)"))
    }

    // MARK: - Helpers

    private func decodeBase64Url(_ base64url: String) throws -> String {
        var s = base64url
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = s.count % 4
        if remainder > 0 {
            s += String(repeating: "=", count: 4 - remainder)
        }
        guard let data = Data(base64Encoded: s),
              let str = String(data: data, encoding: .utf8) else {
            throw NSError(domain: "Test", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to decode base64url"])
        }
        return str
    }
}
