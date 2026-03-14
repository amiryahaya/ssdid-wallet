import XCTest
import CryptoKit
@testable import SsdidWallet

final class X25519ProviderTests: XCTestCase {

    private var provider: X25519Provider!

    override func setUp() {
        super.setUp()
        provider = X25519Provider()
    }

    // MARK: - Key Generation

    func testGenerateKeyPairProduces32ByteKeys() {
        let keyPair = provider.generateKeyPair()
        XCTAssertEqual(keyPair.publicKey.count, 32, "X25519 public key should be 32 bytes")
        XCTAssertEqual(keyPair.privateKey.count, 32, "X25519 private key should be 32 bytes")
    }

    func testGeneratedKeyPairsAreUnique() {
        let kp1 = provider.generateKeyPair()
        let kp2 = provider.generateKeyPair()
        XCTAssertNotEqual(kp1.publicKey, kp2.publicKey, "Public keys must be unique")
        XCTAssertNotEqual(kp1.privateKey, kp2.privateKey, "Private keys must be unique")
    }

    func testPublicKeyIsNotAllZeros() {
        let keyPair = provider.generateKeyPair()
        let allZeros = Data(count: 32)
        XCTAssertNotEqual(keyPair.publicKey, allZeros)
        XCTAssertNotEqual(keyPair.privateKey, allZeros)
    }

    // MARK: - Shared Secret Derivation

    func testDeriveSharedSecretSymmetry() throws {
        let alice = provider.generateKeyPair()
        let bob = provider.generateKeyPair()

        let sharedAB = try provider.deriveSharedSecret(
            privateKey: alice.privateKey,
            peerPublicKey: bob.publicKey
        )
        let sharedBA = try provider.deriveSharedSecret(
            privateKey: bob.privateKey,
            peerPublicKey: alice.publicKey
        )

        XCTAssertEqual(sharedAB, sharedBA, "Shared secrets must be symmetric (Alice-Bob == Bob-Alice)")
        XCTAssertEqual(sharedAB.count, 32, "X25519 shared secret should be 32 bytes")
    }

    func testDeriveSharedSecretDiffersForDifferentPeers() throws {
        let alice = provider.generateKeyPair()
        let bob = provider.generateKeyPair()
        let carol = provider.generateKeyPair()

        let sharedAB = try provider.deriveSharedSecret(
            privateKey: alice.privateKey,
            peerPublicKey: bob.publicKey
        )
        let sharedAC = try provider.deriveSharedSecret(
            privateKey: alice.privateKey,
            peerPublicKey: carol.publicKey
        )

        XCTAssertNotEqual(sharedAB, sharedAC, "Shared secrets with different peers must differ")
    }

    func testDeriveSharedSecretIsNotAllZeros() throws {
        let alice = provider.generateKeyPair()
        let bob = provider.generateKeyPair()

        let shared = try provider.deriveSharedSecret(
            privateKey: alice.privateKey,
            peerPublicKey: bob.publicKey
        )

        XCTAssertNotEqual(shared, Data(count: 32), "Shared secret must not be all zeros")
    }

    func testDeriveSharedSecretWithInvalidKeyThrows() {
        let badKey = Data(repeating: 0x42, count: 16) // Wrong size
        let validKP = provider.generateKeyPair()

        XCTAssertThrowsError(
            try provider.deriveSharedSecret(privateKey: badKey, peerPublicKey: validKP.publicKey),
            "Should throw for invalid private key size"
        )

        XCTAssertThrowsError(
            try provider.deriveSharedSecret(privateKey: validKP.privateKey, peerPublicKey: badKey),
            "Should throw for invalid public key size"
        )
    }

    // MARK: - Determinism

    func testDeriveSharedSecretIsDeterministic() throws {
        let alice = provider.generateKeyPair()
        let bob = provider.generateKeyPair()

        let shared1 = try provider.deriveSharedSecret(
            privateKey: alice.privateKey,
            peerPublicKey: bob.publicKey
        )
        let shared2 = try provider.deriveSharedSecret(
            privateKey: alice.privateKey,
            peerPublicKey: bob.publicKey
        )

        XCTAssertEqual(shared1, shared2, "Same inputs must produce same shared secret")
    }
}
