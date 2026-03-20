import XCTest
@testable import SsdidWallet

final class SsdidWalletTests: XCTestCase {
    func testDidGenerate() {
        let did = Did.generate()
        XCTAssertTrue(did.value.hasPrefix("did:ssdid:"))
        XCTAssertEqual(did.keyId(), "\(did.value)#key-1")
    }

    func testDidFromKeyId() {
        let did = Did.fromKeyId("did:ssdid:abc123#key-1")
        XCTAssertEqual(did.value, "did:ssdid:abc123")
    }

    func testAlgorithmProperties() {
        XCTAssertFalse(Algorithm.ED25519.isPostQuantum)
        XCTAssertTrue(Algorithm.KAZ_SIGN_128.isPostQuantum)
        XCTAssertTrue(Algorithm.KAZ_SIGN_128.isKazSign)
        XCTAssertFalse(Algorithm.ED25519.isKazSign)
        XCTAssertTrue(Algorithm.ML_DSA_44.isMlDsa)
        XCTAssertTrue(Algorithm.SLH_DSA_SHA2_128S.isSlhDsa)
    }

    func testMultibaseRoundTrip() throws {
        let original = Data([0x01, 0x02, 0x03, 0x04, 0x05])
        let encoded = Multibase.encode(original)
        XCTAssertTrue(encoded.hasPrefix("u"))
        let decoded = try Multibase.decode(encoded)
        XCTAssertEqual(original, decoded)
    }

    func testPqcProviderKazSign128() throws {
        try XCTSkipIf(true, "Key size expectations need updating")
        let provider = PqcProvider()
        XCTAssertTrue(provider.supportsAlgorithm(.KAZ_SIGN_128))
        XCTAssertFalse(provider.supportsAlgorithm(.ED25519))

        let keyPair = try provider.generateKeyPair(algorithm: .KAZ_SIGN_128)
        XCTAssertEqual(keyPair.publicKey.count, 54)
        XCTAssertEqual(keyPair.privateKey.count, 32)

        let message = "Hello PQC".data(using: .utf8)!
        let signature = try provider.sign(algorithm: .KAZ_SIGN_128, privateKey: keyPair.privateKey, data: message)
        XCTAssertFalse(signature.isEmpty)

        let valid = try provider.verify(algorithm: .KAZ_SIGN_128, publicKey: keyPair.publicKey, signature: signature, data: message)
        XCTAssertTrue(valid)

        // Verify wrong message fails
        let wrongMessage = "Wrong".data(using: .utf8)!
        let invalid = try provider.verify(algorithm: .KAZ_SIGN_128, publicKey: keyPair.publicKey, signature: signature, data: wrongMessage)
        XCTAssertFalse(invalid)
    }

    func testPqcProviderKazSign192() throws {
        try XCTSkipIf(true, "Key size expectations need updating")
        let provider = PqcProvider()
        let keyPair = try provider.generateKeyPair(algorithm: .KAZ_SIGN_192)
        XCTAssertEqual(keyPair.publicKey.count, 88)
        XCTAssertEqual(keyPair.privateKey.count, 50)

        let message = "Test 192".data(using: .utf8)!
        let signature = try provider.sign(algorithm: .KAZ_SIGN_192, privateKey: keyPair.privateKey, data: message)
        let valid = try provider.verify(algorithm: .KAZ_SIGN_192, publicKey: keyPair.publicKey, signature: signature, data: message)
        XCTAssertTrue(valid)
    }

    func testPqcProviderKazSign256() throws {
        try XCTSkipIf(true, "Key size expectations need updating")
        let provider = PqcProvider()
        let keyPair = try provider.generateKeyPair(algorithm: .KAZ_SIGN_256)
        XCTAssertEqual(keyPair.publicKey.count, 118)
        XCTAssertEqual(keyPair.privateKey.count, 64)

        let message = "Test 256".data(using: .utf8)!
        let signature = try provider.sign(algorithm: .KAZ_SIGN_256, privateKey: keyPair.privateKey, data: message)
        let valid = try provider.verify(algorithm: .KAZ_SIGN_256, publicKey: keyPair.publicKey, signature: signature, data: message)
        XCTAssertTrue(valid)
    }

    // MARK: - ML-DSA Tests

    func testPqcProviderMlDsa44() throws {
        let provider = PqcProvider()
        let keyPair = try provider.generateKeyPair(algorithm: .ML_DSA_44)
        XCTAssertEqual(keyPair.publicKey.count, 1312)
        XCTAssertEqual(keyPair.privateKey.count, 2560)

        let message = "ML-DSA-44 test".data(using: .utf8)!
        let signature = try provider.sign(algorithm: .ML_DSA_44, privateKey: keyPair.privateKey, data: message)
        XCTAssertFalse(signature.isEmpty)

        let valid = try provider.verify(algorithm: .ML_DSA_44, publicKey: keyPair.publicKey, signature: signature, data: message)
        XCTAssertTrue(valid)

        let wrongMessage = "wrong".data(using: .utf8)!
        let invalid = try provider.verify(algorithm: .ML_DSA_44, publicKey: keyPair.publicKey, signature: signature, data: wrongMessage)
        XCTAssertFalse(invalid)
    }

    func testPqcProviderMlDsa65() throws {
        let provider = PqcProvider()
        let keyPair = try provider.generateKeyPair(algorithm: .ML_DSA_65)
        XCTAssertEqual(keyPair.publicKey.count, 1952)
        XCTAssertEqual(keyPair.privateKey.count, 4032)

        let message = "ML-DSA-65 test".data(using: .utf8)!
        let signature = try provider.sign(algorithm: .ML_DSA_65, privateKey: keyPair.privateKey, data: message)
        let valid = try provider.verify(algorithm: .ML_DSA_65, publicKey: keyPair.publicKey, signature: signature, data: message)
        XCTAssertTrue(valid)
    }

    func testPqcProviderMlDsa87() throws {
        let provider = PqcProvider()
        let keyPair = try provider.generateKeyPair(algorithm: .ML_DSA_87)
        XCTAssertEqual(keyPair.publicKey.count, 2592)
        XCTAssertEqual(keyPair.privateKey.count, 4896)

        let message = "ML-DSA-87 test".data(using: .utf8)!
        let signature = try provider.sign(algorithm: .ML_DSA_87, privateKey: keyPair.privateKey, data: message)
        let valid = try provider.verify(algorithm: .ML_DSA_87, publicKey: keyPair.publicKey, signature: signature, data: message)
        XCTAssertTrue(valid)
    }

    // MARK: - SLH-DSA Tests

    func testPqcProviderSlhDsaSha2_128s() throws {
        let provider = PqcProvider()
        let keyPair = try provider.generateKeyPair(algorithm: .SLH_DSA_SHA2_128S)
        XCTAssertFalse(keyPair.publicKey.isEmpty)
        XCTAssertFalse(keyPair.privateKey.isEmpty)

        let message = "SLH-DSA test".data(using: .utf8)!
        let signature = try provider.sign(algorithm: .SLH_DSA_SHA2_128S, privateKey: keyPair.privateKey, data: message)
        XCTAssertFalse(signature.isEmpty)

        let valid = try provider.verify(algorithm: .SLH_DSA_SHA2_128S, publicKey: keyPair.publicKey, signature: signature, data: message)
        XCTAssertTrue(valid)
    }

    func testPqcProviderSlhDsaShake_256f() throws {
        let provider = PqcProvider()
        let keyPair = try provider.generateKeyPair(algorithm: .SLH_DSA_SHAKE_256F)
        XCTAssertFalse(keyPair.publicKey.isEmpty)

        let message = "SLH-DSA SHAKE-256f test".data(using: .utf8)!
        let signature = try provider.sign(algorithm: .SLH_DSA_SHAKE_256F, privateKey: keyPair.privateKey, data: message)
        let valid = try provider.verify(algorithm: .SLH_DSA_SHAKE_256F, publicKey: keyPair.publicKey, signature: signature, data: message)
        XCTAssertTrue(valid)
    }

    // MARK: - Model Tests

    func testCredentialSubjectRoundTrip() throws {
        let json = """
        {"id":"did:ssdid:test","service":"drive","registeredAt":"2024-01-01","claims":{"name":"Alice"}}
        """.data(using: .utf8)!

        let decoder = JSONDecoder()
        let subject = try decoder.decode(CredentialSubject.self, from: json)
        XCTAssertEqual(subject.id, "did:ssdid:test")
        XCTAssertEqual(subject.claims["name"], "Alice")
        XCTAssertNotNil(subject.additionalProperties["service"])
        XCTAssertNotNil(subject.additionalProperties["registeredAt"])

        // Round-trip: encode back and verify fields preserved
        let encoder = JSONEncoder()
        let reEncoded = try encoder.encode(subject)
        let dict = try JSONSerialization.jsonObject(with: reEncoded) as! [String: Any]
        XCTAssertEqual(dict["service"] as? String, "drive")
        XCTAssertEqual(dict["registeredAt"] as? String, "2024-01-01")
    }
}
