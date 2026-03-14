import XCTest
@testable import SsdidWallet

final class DidKeyResolverTests: XCTestCase {
    let resolver = DidKeyResolver()

    func testResolveEd25519DidKey() async throws {
        let did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        let doc = try await resolver.resolve(did: did)
        XCTAssertEqual(doc.id, did)
        XCTAssertEqual(doc.verificationMethod.count, 1)
        XCTAssertEqual(doc.verificationMethod[0].type, "Ed25519VerificationKey2020")
        XCTAssertEqual(doc.verificationMethod[0].controller, did)
        XCTAssertTrue(doc.authentication.contains(doc.verificationMethod[0].id))
    }

    func testResolveP256DidKey() async throws {
        let did = "did:key:zDnaeWgbpcUat3VPa1GqrFbcr7jVBNMhBMRKTsgBHYBcJkRYH"
        let doc = try await resolver.resolve(did: did)
        XCTAssertEqual(doc.verificationMethod[0].type, "EcdsaSecp256r1VerificationKey2019")
    }

    func testResolveNonDidKeyThrows() async {
        do {
            _ = try await resolver.resolve(did: "did:ssdid:abc")
            XCTFail("Should have thrown")
        } catch {
            // Expected
        }
    }

    func testAssertionMethodIsPopulated() async throws {
        let did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        let doc = try await resolver.resolve(did: did)
        XCTAssertFalse(doc.assertionMethod.isEmpty, "assertionMethod must be populated")
        XCTAssertEqual(doc.assertionMethod.count, 1)
        XCTAssertEqual(doc.assertionMethod[0], doc.verificationMethod[0].id)
    }

    func testCapabilityInvocationIsPopulated() async throws {
        let did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        let doc = try await resolver.resolve(did: did)
        XCTAssertFalse(doc.capabilityInvocation.isEmpty, "capabilityInvocation must be populated")
        XCTAssertEqual(doc.capabilityInvocation.count, 1)
        XCTAssertEqual(doc.capabilityInvocation[0], doc.verificationMethod[0].id)
    }
}
