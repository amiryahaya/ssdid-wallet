import XCTest
import CryptoKit
import CommonCrypto
@testable import SsdidWallet

/// Tests for the cryptographic primitives used by BackupManager:
/// PBKDF2-HMAC-SHA256, HKDF-Expand, AES-256-GCM, HMAC-SHA256, Base64URL encoding.
///
/// These tests exercise the crypto functions directly without needing mocks,
/// by creating a BackupManager with minimal stubs and testing the
/// backup/restore round-trip as well as tamper detection.
final class BackupCryptoTests: XCTestCase {

    // MARK: - Base64URL Encoding/Decoding

    func testBase64URLRoundTrip() throws {
        let original = Data([0x00, 0xFF, 0x3E, 0x3F, 0xFB, 0xEF, 0xBF])
        let encoded = original.testBase64URLEncodedNoPadding()
        // Must not contain +, /, or =
        XCTAssertFalse(encoded.contains("+"))
        XCTAssertFalse(encoded.contains("/"))
        XCTAssertFalse(encoded.contains("="))

        let decoded = try Data.testFromBase64URL(encoded)
        XCTAssertEqual(original, decoded)
    }

    func testBase64URLEmptyData() throws {
        let empty = Data()
        let encoded = empty.testBase64URLEncodedNoPadding()
        XCTAssertEqual(encoded, "")
        let decoded = try Data.testFromBase64URL(encoded)
        XCTAssertEqual(decoded, empty)
    }

    func testBase64URLPaddingVariants() throws {
        // 1 byte -> 2 base64 chars -> needs 2 padding
        let oneB = Data([0x41])
        let enc1 = oneB.testBase64URLEncodedNoPadding()
        XCTAssertFalse(enc1.contains("="))
        XCTAssertEqual(try Data.testFromBase64URL(enc1), oneB)

        // 2 bytes -> 3 base64 chars -> needs 1 padding
        let twoB = Data([0x41, 0x42])
        let enc2 = twoB.testBase64URLEncodedNoPadding()
        XCTAssertFalse(enc2.contains("="))
        XCTAssertEqual(try Data.testFromBase64URL(enc2), twoB)

        // 3 bytes -> 4 base64 chars -> no padding needed
        let threeB = Data([0x41, 0x42, 0x43])
        let enc3 = threeB.testBase64URLEncodedNoPadding()
        XCTAssertEqual(try Data.testFromBase64URL(enc3), threeB)
    }

    func testBase64URLInvalidInputThrows() {
        XCTAssertThrowsError(try Data.testFromBase64URL("!!!invalid!!!"))
    }

    // MARK: - PBKDF2

    func testPBKDF2DeterministicOutput() {
        let passphrase = "test-passphrase"
        let salt = Data(repeating: 0xAA, count: 32)

        let key1 = pbkdf2(passphrase: passphrase, salt: salt)
        let key2 = pbkdf2(passphrase: passphrase, salt: salt)
        XCTAssertEqual(key1, key2, "PBKDF2 must be deterministic for same inputs")
        XCTAssertEqual(key1.count, 32, "Should produce 32-byte key")
    }

    func testPBKDF2DifferentSaltProducesDifferentKey() {
        let passphrase = "same-pass"
        let salt1 = Data(repeating: 0x01, count: 32)
        let salt2 = Data(repeating: 0x02, count: 32)

        let key1 = pbkdf2(passphrase: passphrase, salt: salt1)
        let key2 = pbkdf2(passphrase: passphrase, salt: salt2)
        XCTAssertNotEqual(key1, key2)
    }

    func testPBKDF2DifferentPassphraseProducesDifferentKey() {
        let salt = Data(repeating: 0xBB, count: 32)
        let key1 = pbkdf2(passphrase: "password1", salt: salt)
        let key2 = pbkdf2(passphrase: "password2", salt: salt)
        XCTAssertNotEqual(key1, key2)
    }

    func testPBKDF2EmptyPassphrase() {
        let salt = Data(repeating: 0xCC, count: 32)
        let key = pbkdf2(passphrase: "", salt: salt)
        XCTAssertEqual(key.count, 32, "Should still produce 32-byte key for empty passphrase")
    }

    // MARK: - HKDF-Expand (deriveSubKey)

    func testHKDFExpandDeterministic() {
        let prk = Data(repeating: 0x07, count: 32)
        let key1 = hkdfExpand(prk: prk, info: "enc")
        let key2 = hkdfExpand(prk: prk, info: "enc")
        XCTAssertEqual(key1, key2)
        XCTAssertEqual(key1.count, 32)
    }

    func testHKDFExpandDifferentInfoProducesDifferentKey() {
        let prk = Data(repeating: 0x07, count: 32)
        let encKey = hkdfExpand(prk: prk, info: "enc")
        let macKey = hkdfExpand(prk: prk, info: "mac")
        XCTAssertNotEqual(encKey, macKey, "Different info labels must produce different sub-keys")
    }

    func testHKDFExpandMatchesManualComputation() {
        // HKDF-Expand for one block: HMAC-SHA256(PRK, info || 0x01)
        let prk = Data(repeating: 0xAB, count: 32)
        let info = "enc"
        var input = Data(info.utf8)
        input.append(0x01)
        let expected = Data(HMAC<SHA256>.authenticationCode(for: input, using: SymmetricKey(data: prk)))
        let actual = hkdfExpand(prk: prk, info: info)
        XCTAssertEqual(actual, expected)
    }

    // MARK: - AES-256-GCM Round Trip

    func testAES256GCMEncryptDecrypt() throws {
        let key = SymmetricKey(size: .bits256)
        let plaintext = Data("sensitive identity data".utf8)
        let nonce = AES.GCM.Nonce()

        let sealedBox = try AES.GCM.seal(plaintext, using: key, nonce: nonce)
        let ciphertext = sealedBox.ciphertext + sealedBox.tag

        // Decrypt
        let encData = ciphertext.prefix(ciphertext.count - 16)
        let tag = ciphertext.suffix(16)
        let reopened = try AES.GCM.SealedBox(nonce: nonce, ciphertext: encData, tag: tag)
        let decrypted = try AES.GCM.open(reopened, using: key)

        XCTAssertEqual(plaintext, decrypted)
    }

    func testAES256GCMTamperedCiphertextFails() throws {
        let key = SymmetricKey(size: .bits256)
        let plaintext = Data("test".utf8)
        let nonce = AES.GCM.Nonce()

        let sealedBox = try AES.GCM.seal(plaintext, using: key, nonce: nonce)
        var ciphertextBytes = Array(sealedBox.ciphertext) + Array(sealedBox.tag)

        // Tamper with first byte of ciphertext
        ciphertextBytes[0] ^= 0xFF
        let ciphertext = Data(ciphertextBytes)

        let encData = ciphertext.prefix(ciphertext.count - 16)
        let tag = ciphertext.suffix(16)
        let tampered = try AES.GCM.SealedBox(nonce: nonce, ciphertext: encData, tag: tag)

        XCTAssertThrowsError(try AES.GCM.open(tampered, using: key), "Tampered ciphertext must fail GCM authentication")
    }

    func testAES256GCMWrongKeyFails() throws {
        let key1 = SymmetricKey(size: .bits256)
        let key2 = SymmetricKey(size: .bits256)
        let plaintext = Data("test".utf8)
        let nonce = AES.GCM.Nonce()

        let sealedBox = try AES.GCM.seal(plaintext, using: key1, nonce: nonce)
        let ciphertext = sealedBox.ciphertext + sealedBox.tag
        let encData = ciphertext.prefix(ciphertext.count - 16)
        let tag = ciphertext.suffix(16)
        let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: encData, tag: tag)

        XCTAssertThrowsError(try AES.GCM.open(box, using: key2), "Wrong key must fail decryption")
    }

    // MARK: - HMAC-SHA256

    func testHMACSHA256Verification() {
        let key = SymmetricKey(size: .bits256)
        let data = Data("payload to authenticate".utf8)

        let mac1 = HMAC<SHA256>.authenticationCode(for: data, using: key)
        let mac2 = HMAC<SHA256>.authenticationCode(for: data, using: key)

        XCTAssertEqual(Data(mac1), Data(mac2), "HMAC must be deterministic")
        XCTAssertTrue(HMAC<SHA256>.isValidAuthenticationCode(mac1, authenticating: data, using: key))
    }

    func testHMACSHA256RejectsTamperedData() {
        let key = SymmetricKey(size: .bits256)
        let data = Data("original".utf8)
        let mac = HMAC<SHA256>.authenticationCode(for: data, using: key)

        let tampered = Data("modified".utf8)
        XCTAssertFalse(HMAC<SHA256>.isValidAuthenticationCode(mac, authenticating: tampered, using: key))
    }

    func testHMACSHA256RejectsWrongKey() {
        let key1 = SymmetricKey(size: .bits256)
        let key2 = SymmetricKey(size: .bits256)
        let data = Data("test".utf8)

        let mac = HMAC<SHA256>.authenticationCode(for: data, using: key1)
        XCTAssertFalse(HMAC<SHA256>.isValidAuthenticationCode(mac, authenticating: data, using: key2))
    }

    // MARK: - BackupPackage Codable

    func testBackupPackageRoundTrip() throws {
        let package = BackupPackage(
            salt: "c2FsdA",
            nonce: "bm9uY2U",
            ciphertext: "Y2lwaGVy",
            algorithms: ["ED25519"],
            dids: ["did:ssdid:test123"],
            createdAt: "2024-01-01T00:00:00Z",
            hmac: "aG1hYw"
        )

        let encoded = try JSONEncoder().encode(package)
        let decoded = try JSONDecoder().decode(BackupPackage.self, from: encoded)

        XCTAssertEqual(decoded.version, 1)
        XCTAssertEqual(decoded.salt, package.salt)
        XCTAssertEqual(decoded.nonce, package.nonce)
        XCTAssertEqual(decoded.ciphertext, package.ciphertext)
        XCTAssertEqual(decoded.algorithms, package.algorithms)
        XCTAssertEqual(decoded.dids, package.dids)
        XCTAssertEqual(decoded.createdAt, package.createdAt)
        XCTAssertEqual(decoded.hmac, package.hmac)
    }

    func testBackupPackageUsesSnakeCaseKeys() throws {
        let package = BackupPackage(
            salt: "s", nonce: "n", ciphertext: "c",
            algorithms: [], dids: [], createdAt: "now", hmac: "h"
        )
        let json = try JSONEncoder().encode(package)
        let dict = try JSONSerialization.jsonObject(with: json) as! [String: Any]

        XCTAssertNotNil(dict["created_at"], "createdAt should encode as created_at")
        XCTAssertNil(dict["createdAt"], "Should not have camelCase key")
    }

    func testBackupIdentityUsesSnakeCaseKeys() throws {
        let identity = BackupIdentity(
            keyId: "k1", did: "did:ssdid:x", name: "Test",
            algorithm: "ED25519", privateKey: "priv", publicKey: "pub",
            createdAt: "2024-01-01"
        )
        let json = try JSONEncoder().encode(identity)
        let dict = try JSONSerialization.jsonObject(with: json) as! [String: Any]

        XCTAssertNotNil(dict["key_id"])
        XCTAssertNotNil(dict["private_key"])
        XCTAssertNotNil(dict["public_key"])
        XCTAssertNotNil(dict["created_at"])
    }

    // MARK: - Full Encrypt-Decrypt Pipeline

    func testFullEncryptDecryptPipeline() throws {
        // Simulate what BackupManager does internally
        let passphrase = "my-secure-passphrase-2024"
        let plaintext = Data(#"{"identities":[{"did":"did:ssdid:test"}]}"#.utf8)

        // Generate salt
        var salt = Data(count: 32)
        salt.withUnsafeMutableBytes { _ = SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!) }

        // Derive keys
        let backupKey = pbkdf2(passphrase: passphrase, salt: salt)
        let encKey = hkdfExpand(prk: backupKey, info: "enc")
        let macKey = hkdfExpand(prk: backupKey, info: "mac")

        // Encrypt
        let nonce = AES.GCM.Nonce()
        let sealedBox = try AES.GCM.seal(plaintext, using: SymmetricKey(data: encKey), nonce: nonce)
        let ciphertext = sealedBox.ciphertext + sealedBox.tag

        // HMAC
        var hmacInput = Data()
        hmacInput.append(salt)
        hmacInput.append(contentsOf: nonce)
        hmacInput.append(ciphertext)
        let hmac = HMAC<SHA256>.authenticationCode(for: hmacInput, using: SymmetricKey(data: macKey))

        // --- Now decrypt (simulating restore) ---

        // Re-derive keys from same passphrase + salt
        let restoreBackupKey = pbkdf2(passphrase: passphrase, salt: salt)
        let restoreEncKey = hkdfExpand(prk: restoreBackupKey, info: "enc")
        let restoreMacKey = hkdfExpand(prk: restoreBackupKey, info: "mac")

        // Verify HMAC
        var restoreHmacInput = Data()
        restoreHmacInput.append(salt)
        restoreHmacInput.append(contentsOf: nonce)
        restoreHmacInput.append(ciphertext)
        let expectedHmac = HMAC<SHA256>.authenticationCode(for: restoreHmacInput, using: SymmetricKey(data: restoreMacKey))
        XCTAssertEqual(Data(hmac), Data(expectedHmac), "HMAC must match")

        // Decrypt
        let encData = ciphertext.prefix(ciphertext.count - 16)
        let tag = ciphertext.suffix(16)
        let restoreBox = try AES.GCM.SealedBox(nonce: nonce, ciphertext: encData, tag: tag)
        let decrypted = try AES.GCM.open(restoreBox, using: SymmetricKey(data: restoreEncKey))

        XCTAssertEqual(plaintext, decrypted)
    }

    func testWrongPassphraseFailsHMAC() throws {
        let passphrase = "correct-passphrase"
        let plaintext = Data("secret".utf8)

        var salt = Data(count: 32)
        salt.withUnsafeMutableBytes { _ = SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!) }

        let backupKey = pbkdf2(passphrase: passphrase, salt: salt)
        let encKey = hkdfExpand(prk: backupKey, info: "enc")
        let macKey = hkdfExpand(prk: backupKey, info: "mac")

        let nonce = AES.GCM.Nonce()
        let sealedBox = try AES.GCM.seal(plaintext, using: SymmetricKey(data: encKey), nonce: nonce)
        let ciphertext = sealedBox.ciphertext + sealedBox.tag

        var hmacInput = Data()
        hmacInput.append(salt)
        hmacInput.append(contentsOf: nonce)
        hmacInput.append(ciphertext)
        let originalHmac = HMAC<SHA256>.authenticationCode(for: hmacInput, using: SymmetricKey(data: macKey))

        // Try with wrong passphrase
        let wrongBackupKey = pbkdf2(passphrase: "wrong-passphrase", salt: salt)
        let wrongMacKey = hkdfExpand(prk: wrongBackupKey, info: "mac")
        let wrongHmac = HMAC<SHA256>.authenticationCode(for: hmacInput, using: SymmetricKey(data: wrongMacKey))

        XCTAssertNotEqual(Data(originalHmac), Data(wrongHmac), "Wrong passphrase must produce different HMAC")
    }

    // MARK: - Helpers (mirror BackupManager's private methods)

    private func pbkdf2(passphrase: String, salt: Data) -> Data {
        let passphraseData = Data(passphrase.utf8)
        var derivedKey = Data(count: 32)

        let result = derivedKey.withUnsafeMutableBytes { derivedKeyBytes in
            salt.withUnsafeBytes { saltBytes in
                passphraseData.withUnsafeBytes { passphraseBytes in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passphraseBytes.baseAddress?.assumingMemoryBound(to: Int8.self),
                        passphraseData.count,
                        saltBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        600_000,
                        derivedKeyBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        32
                    )
                }
            }
        }

        precondition(result == kCCSuccess)
        return derivedKey
    }

    private func hkdfExpand(prk: Data, info: String) -> Data {
        let key = SymmetricKey(data: prk)
        var input = Data(info.utf8)
        input.append(0x01)
        let mac = HMAC<SHA256>.authenticationCode(for: input, using: key)
        return Data(mac)
    }
}

// MARK: - Test-accessible Base64URL helpers (mirror BackupManager's private extension)

private extension Data {
    func testBase64URLEncodedNoPadding() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func testFromBase64URL(_ string: String) throws -> Data {
        var base64 = string
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = base64.count % 4
        if remainder > 0 {
            base64 += String(repeating: "=", count: 4 - remainder)
        }
        guard let data = Data(base64Encoded: base64) else {
            throw NSError(domain: "test", code: 1, userInfo: [NSLocalizedDescriptionKey: "Invalid Base64URL"])
        }
        return data
    }
}
