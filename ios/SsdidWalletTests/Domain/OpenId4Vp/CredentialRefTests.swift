import XCTest
@testable import SsdidWallet

final class CredentialRefTests: XCTestCase {

    func testSdJwtRefHoldsCredential() {
        let vc = StoredSdJwtVc(
            id: "vc-1",
            compact: "eyJ...",
            issuer: "did:ssdid:issuer",
            subject: "did:ssdid:holder",
            type: "IdentityCredential",
            claims: ["name": "Ahmad"],
            disclosableClaims: ["name"],
            issuedAt: 1_700_000_000
        )
        let ref = CredentialRef.sdJwt(vc)

        if case .sdJwt(let stored) = ref {
            XCTAssertEqual(stored.id, "vc-1")
            XCTAssertEqual(stored.type, "IdentityCredential")
        } else {
            XCTFail("Expected sdJwt case")
        }
    }

    func testMDocRefHoldsCredential() {
        let mdoc = StoredMDoc(
            id: "mdoc-1",
            docType: "org.iso.18013.5.1.mDL",
            issuerSignedCbor: Data([0xa0]),
            deviceKeyId: "key-1",
            issuedAt: 1_700_000_000,
            nameSpaces: ["org.iso.18013.5.1": ["family_name", "given_name"]]
        )
        let ref = CredentialRef.mdoc(mdoc)

        if case .mdoc(let stored) = ref {
            XCTAssertEqual(stored.id, "mdoc-1")
            XCTAssertEqual(stored.docType, "org.iso.18013.5.1.mDL")
        } else {
            XCTFail("Expected mdoc case")
        }
    }

    func testSdJwtAndMDocAreDifferentCases() {
        let vc = StoredSdJwtVc(
            id: "vc-1",
            compact: "eyJ...",
            issuer: "did:ssdid:issuer",
            subject: "did:ssdid:holder",
            type: "IdentityCredential",
            claims: [:],
            disclosableClaims: [],
            issuedAt: 1_700_000_000
        )
        let mdoc = StoredMDoc(
            id: "mdoc-1",
            docType: "org.iso.18013.5.1.mDL",
            issuerSignedCbor: Data([0xa0]),
            deviceKeyId: "key-1",
            issuedAt: 1_700_000_000
        )

        let sdJwtRef = CredentialRef.sdJwt(vc)
        let mdocRef = CredentialRef.mdoc(mdoc)

        if case .sdJwt = sdJwtRef {
            // expected
        } else {
            XCTFail("Expected sdJwt case")
        }

        if case .mdoc = mdocRef {
            // expected
        } else {
            XCTFail("Expected mdoc case")
        }
    }
}
