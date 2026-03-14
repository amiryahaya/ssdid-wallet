import XCTest
@testable import SsdidWallet

final class KeyBindingJwtTests: XCTestCase {

    private let testSigner: (Data) -> Data = { _ in Data("kb-signature".utf8) }

    func testCreateProducesValidJwtStructure() throws {
        let kbJwt = try KeyBindingJwt.create(
            sdJwtWithDisclosures: "header.payload.sig~disclosure1~",
            audience: "https://verifier.example.com",
            nonce: "test-nonce-123",
            algorithm: "EdDSA",
            signer: testSigner,
            issuedAt: 1700000000
        )
        let parts = kbJwt.split(separator: ".")
        XCTAssertEqual(parts.count, 3, "KB-JWT must have 3 dot-separated parts")

        // Verify header
        let headerData = Data(base64URLEncoded: String(parts[0]))!
        let header = try JSONSerialization.jsonObject(with: headerData) as! [String: String]
        XCTAssertEqual(header["alg"], "EdDSA")
        XCTAssertEqual(header["typ"], "kb+jwt")

        // Verify payload
        let payloadData = Data(base64URLEncoded: String(parts[1]))!
        let payload = try JSONSerialization.jsonObject(with: payloadData) as! [String: Any]
        XCTAssertEqual(payload["aud"] as? String, "https://verifier.example.com")
        XCTAssertEqual(payload["nonce"] as? String, "test-nonce-123")
        XCTAssertEqual(payload["iat"] as? Int, 1700000000)
        XCTAssertNotNil(payload["sd_hash"])
    }

    func testSdHashIsDeterministic() throws {
        let input = "header.payload.sig~disclosure1~"
        let jwt1 = try KeyBindingJwt.create(
            sdJwtWithDisclosures: input,
            audience: "https://v.example.com",
            nonce: "n1",
            algorithm: "EdDSA",
            signer: testSigner,
            issuedAt: 1700000000
        )
        let jwt2 = try KeyBindingJwt.create(
            sdJwtWithDisclosures: input,
            audience: "https://v.example.com",
            nonce: "n1",
            algorithm: "EdDSA",
            signer: testSigner,
            issuedAt: 1700000000
        )
        // Extract sd_hash from both
        let hash1 = try extractSdHash(jwt1)
        let hash2 = try extractSdHash(jwt2)
        XCTAssertEqual(hash1, hash2)
        XCTAssertFalse(hash1.isEmpty)
    }

    func testAudienceAndNonceInPayload() throws {
        let kbJwt = try KeyBindingJwt.create(
            sdJwtWithDisclosures: "a.b.c~d~",
            audience: "did:key:z6MkVerifier",
            nonce: "unique-nonce-456",
            algorithm: "ES256",
            signer: testSigner,
            issuedAt: 1700000000
        )
        let parts = kbJwt.split(separator: ".")
        let payloadData = Data(base64URLEncoded: String(parts[1]))!
        let payload = try JSONSerialization.jsonObject(with: payloadData) as! [String: Any]
        XCTAssertEqual(payload["aud"] as? String, "did:key:z6MkVerifier")
        XCTAssertEqual(payload["nonce"] as? String, "unique-nonce-456")
    }

    private func extractSdHash(_ jwt: String) throws -> String {
        let parts = jwt.split(separator: ".")
        let payloadData = Data(base64URLEncoded: String(parts[1]))!
        let payload = try JSONSerialization.jsonObject(with: payloadData) as! [String: Any]
        return payload["sd_hash"] as! String
    }
}
