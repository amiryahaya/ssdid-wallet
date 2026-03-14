import XCTest
@testable import SsdidWallet

final class VpTokenBuilderTests: XCTestCase {

    private let testSigner: (Data) -> Data = { _ in "test-sig".data(using: .utf8)! }

    func testBuildsVpTokenWithSelectedDisclosuresAndKbJwt() throws {
        let issuer = SdJwtIssuer(signer: testSigner, algorithm: "EdDSA")
        let sdJwt = try issuer.issue(
            issuer: "did:ssdid:issuer1",
            subject: "did:ssdid:holder1",
            type: ["VerifiableCredential", "VerifiedEmployee"],
            claims: ["name": "Ahmad", "dept": "Eng"],
            disclosable: Set(["name", "dept"]),
            issuedAt: 1719792000
        )

        let builder = VpTokenBuilder()
        let vpToken = try builder.build(
            sdJwtVc: sdJwt,
            selectedClaimNames: Set(["name"]),
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
        let builder = VpTokenBuilder()
        let submission = builder.buildPresentationSubmission(
            definitionId: "req-1",
            descriptorId: "emp-cred"
        )

        XCTAssertEqual(submission.definitionId, "req-1")
        XCTAssertEqual(submission.descriptorMap.count, 1)
        XCTAssertEqual(submission.descriptorMap[0].id, "emp-cred")
        XCTAssertEqual(submission.descriptorMap[0].format, "vc+sd-jwt")
        XCTAssertEqual(submission.descriptorMap[0].path, "$")
    }

    func testSelectsOnlyMatchingDisclosures() throws {
        let issuer = SdJwtIssuer(signer: testSigner, algorithm: "EdDSA")
        let sdJwt = try issuer.issue(
            issuer: "did:ssdid:issuer1",
            subject: "did:ssdid:holder1",
            type: ["VerifiableCredential"],
            claims: ["a": "1", "b": "2", "c": "3"],
            disclosable: Set(["a", "b", "c"])
        )

        let builder = VpTokenBuilder()
        let vpToken = try builder.build(
            sdJwtVc: sdJwt,
            selectedClaimNames: Set(["a", "c"]),
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
