import XCTest
@testable import SsdidWallet

final class VpTokenBuilderTests: XCTestCase {

    /// Dummy signer that returns a fixed 64-byte signature.
    private let signer: (Data) -> Data = { _ in Data(repeating: 0, count: 64) }

    private func makeCredential() -> StoredSdJwtVc {
        StoredSdJwtVc(
            id: "vc-1",
            compact: "eyJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6c3NkaWQ6aXNzdWVyMSJ9.sig~WyJzYWx0MSIsIm5hbWUiLCJBaG1hZCJd~WyJzYWx0MiIsImVtYWlsIiwiYUBleC5jb20iXQ~",
            issuer: "did:ssdid:issuer1",
            subject: "did:ssdid:holder1",
            type: "IdentityCredential",
            claims: ["name": "Ahmad", "email": "a@ex.com"],
            disclosableClaims: ["name", "email"],
            issuedAt: 1_700_000_000
        )
    }

    func testBuildVpTokenWithSelectedDisclosures() throws {
        let vpToken = try VpTokenBuilder.build(
            storedSdJwtVc: makeCredential(),
            selectedClaims: ["name"],
            audience: "https://verifier.example.com",
            nonce: "nonce-123",
            algorithm: "EdDSA",
            signer: signer
        )

        let parts = vpToken.split(separator: "~", omittingEmptySubsequences: false)
        // issuerJwt + 1 disclosure + kbJwt = at least 3 non-empty parts
        XCTAssertGreaterThanOrEqual(parts.filter { !$0.isEmpty }.count, 3)
        // First part is the issuer JWT (starts with base64url header)
        XCTAssertTrue(parts[0].hasPrefix("eyJ"))
        // Last non-empty part is the KB-JWT (has 3 dot-separated segments)
        let lastPart = String(parts.last { !$0.isEmpty }!)
        XCTAssertEqual(lastPart.filter { $0 == "." }.count, 2)
    }

    func testBuildVpTokenWithAllDisclosures() throws {
        let vpToken = try VpTokenBuilder.build(
            storedSdJwtVc: makeCredential(),
            selectedClaims: ["name", "email"],
            audience: "https://v.example.com",
            nonce: "n-1",
            algorithm: "EdDSA",
            signer: signer
        )

        let parts = vpToken.split(separator: "~", omittingEmptySubsequences: false)
        // issuerJwt + 2 disclosures + kbJwt = 4 non-empty parts
        XCTAssertEqual(parts.filter { !$0.isEmpty }.count, 4)
    }

    func testBuildVpTokenContainsOnlySelectedDisclosures() throws {
        let vpToken = try VpTokenBuilder.build(
            storedSdJwtVc: makeCredential(),
            selectedClaims: ["email"],
            audience: "https://verifier.example.com",
            nonce: "nonce-456",
            algorithm: "EdDSA",
            signer: signer
        )

        // Should contain the email disclosure but not the name disclosure
        XCTAssertTrue(vpToken.contains("WyJzYWx0MiIsImVtYWlsIiwiYUBleC5jb20iXQ"))
        XCTAssertFalse(vpToken.contains("WyJzYWx0MSIsIm5hbWUiLCJBaG1hZCJd"))
    }
}
