import XCTest
@testable import SsdidWallet

final class DidJwkResolverTests: XCTestCase {
    let resolver = DidJwkResolver()

    func testResolveEd25519DidJwk() async throws {
        let jwk = #"{"kty":"OKP","crv":"Ed25519","x":"0BE0_DgKo7df36V2_HEalJWNBpFj8ZnfNcK7BJ6jHuQ"}"#
        let encoded = Data(jwk.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        let did = "did:jwk:\(encoded)"
        let doc = try await resolver.resolve(did: did)
        XCTAssertEqual(doc.id, did)
        XCTAssertEqual(doc.verificationMethod[0].type, "JsonWebKey2020")
    }

    func testResolveNonDidJwkThrows() async {
        do {
            _ = try await resolver.resolve(did: "did:ssdid:abc")
            XCTFail("Should have thrown")
        } catch {}
    }
}
