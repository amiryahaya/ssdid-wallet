@testable import SsdidCore
import XCTest
@testable import SsdidWallet

final class VpTokenBuilderTests: XCTestCase {

    private let testSigner: (Data) -> Data = { _ in "test-sig".data(using: .utf8)! }

    /// Issues an SD-JWT VC and wraps it as a StoredSdJwtVc for VpTokenBuilder.
    private func issueAndStore(
        claims: [String: Any],
        disclosable: Set<String>,
        type: String = "VerifiedEmployee"
    ) throws -> StoredSdJwtVc {
        let issuer = SdJwtIssuer(signer: testSigner, algorithm: "EdDSA")
        let sdJwt = try issuer.issue(
            issuer: "did:ssdid:issuer1",
            subject: "did:ssdid:holder1",
            type: ["VerifiableCredential", type],
            claims: claims,
            disclosable: disclosable
        )
        let compact = try sdJwt.present(selectedDisclosures: sdJwt.disclosures)

        var claimStrings: [String: String] = [:]
        for d in sdJwt.disclosures {
            claimStrings[d.claimName] = "\(d.claimValue)"
        }

        return StoredSdJwtVc(
            id: "vc-test",
            compact: compact,
            issuer: "did:ssdid:issuer1",
            subject: "did:ssdid:holder1",
            type: type,
            claims: claimStrings,
            disclosableClaims: Array(disclosable),
            issuedAt: 1719792000
        )
    }

    func testBuildsVpTokenWithSelectedDisclosuresAndKbJwt() throws {
        let stored = try issueAndStore(
            claims: ["name": "Ahmad", "dept": "Eng"],
            disclosable: Set(["name", "dept"])
        )

        let vpToken = try VpTokenBuilder.build(
            storedSdJwtVc: stored,
            selectedClaims: ["name"],
            audience: "https://verifier.example.com",
            nonce: "n-0S6_WzA2Mj",
            algorithm: "EdDSA",
            signer: testSigner
        )

        // VP token: issuerJwt~disc~kbJwt
        let parts = vpToken.split(separator: "~", omittingEmptySubsequences: true).map(String.init)
        XCTAssertEqual(parts.count, 3) // issuerJwt, disclosure, kbJwt
        XCTAssertTrue(parts[0].contains(".")) // JWT
        XCTAssertTrue(parts[2].contains(".")) // KB-JWT
    }

    func testBuildsPresentationSubmission() {
        let submission = PresentationSubmission.create(
            definitionId: "req-1",
            descriptorIds: ["emp-cred"]
        )

        XCTAssertEqual(submission.definitionId, "req-1")
        XCTAssertEqual(submission.descriptorMap.count, 1)
        XCTAssertEqual(submission.descriptorMap[0].id, "emp-cred")
        XCTAssertEqual(submission.descriptorMap[0].format, "vc+sd-jwt")
        XCTAssertEqual(submission.descriptorMap[0].path, "$")
    }

    func testSelectsOnlyMatchingDisclosures() throws {
        let stored = try issueAndStore(
            claims: ["a": "1", "b": "2", "c": "3"],
            disclosable: Set(["a", "b", "c"]),
            type: "TestCredential"
        )

        let vpToken = try VpTokenBuilder.build(
            storedSdJwtVc: stored,
            selectedClaims: ["a", "c"],
            audience: "https://v.example.com",
            nonce: "abc",
            algorithm: "EdDSA",
            signer: testSigner
        )

        let parts = vpToken.split(separator: "~", omittingEmptySubsequences: true).map(String.init)
        // issuerJwt + 2 disclosures + kbJwt = 4 parts
        XCTAssertEqual(parts.count, 4)
    }
}
