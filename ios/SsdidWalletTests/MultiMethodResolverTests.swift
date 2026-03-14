import XCTest
@testable import SsdidWallet

final class MultiMethodResolverTests: XCTestCase {

    func testRoutesToDidKeyResolver() async throws {
        let did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        let resolver = makeResolver()
        let doc = try await resolver.resolve(did: did)
        XCTAssertEqual(doc.id, did)
        XCTAssertEqual(doc.verificationMethod[0].type, "Ed25519VerificationKey2020")
    }

    func testRoutesToDidJwkResolver() async throws {
        let jwk = #"{"kty":"OKP","crv":"Ed25519","x":"0BE0_DgKo7df36V2_HEalJWNBpFj8ZnfNcK7BJ6jHuQ"}"#
        let encoded = Data(jwk.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        let did = "did:jwk:\(encoded)"
        let resolver = makeResolver()
        let doc = try await resolver.resolve(did: did)
        XCTAssertEqual(doc.id, did)
        XCTAssertEqual(doc.verificationMethod[0].type, "JsonWebKey2020")
    }

    func testUnsupportedMethodThrows() async {
        let resolver = makeResolver()
        do {
            _ = try await resolver.resolve(did: "did:web:example.com")
            XCTFail("Should have thrown for unsupported method")
        } catch let error as DidResolutionError {
            if case .unsupportedMethod = error {
                // Expected
            } else {
                XCTFail("Expected unsupportedMethod error, got \(error)")
            }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    private func makeResolver() -> MultiMethodResolver {
        // Use a real SsdidHttpClient with a dummy URL -- ssdid resolution won't be called.
        let httpClient = SsdidHttpClient(registryURL: "https://localhost:0")
        let registryApi = RegistryApi(client: httpClient, baseURL: "https://localhost:0")
        return MultiMethodResolver(
            ssdidResolver: SsdidRegistryResolver(registryApi: registryApi),
            keyResolver: DidKeyResolver(),
            jwkResolver: DidJwkResolver()
        )
    }
}
