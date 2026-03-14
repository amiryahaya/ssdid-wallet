import XCTest
import CryptoKit
@testable import SsdidWallet

final class DIDCommPackerTests: XCTestCase {

    private var packer: DIDCommPacker!
    private var unpacker: DIDCommUnpacker!
    private var x25519: X25519Provider!

    override func setUp() {
        super.setUp()
        packer = DIDCommPacker()
        unpacker = DIDCommUnpacker()
        x25519 = X25519Provider()
    }

    // MARK: - Pack

    func testPackProducesNonEmptyOutput() throws {
        let sender = x25519.generateKeyPair()
        let recipient = x25519.generateKeyPair()

        let message = DIDCommMessage(
            id: "pack-001",
            type: "https://didcomm.org/basicmessage/2.0/message",
            from: "did:ssdid:alice",
            to: ["did:ssdid:bob"],
            body: ["content": AnyCodable("Hello")]
        )

        let packed = try packer.pack(
            message: message,
            senderPrivateKey: sender.privateKey,
            recipientPublicKey: recipient.publicKey
        )

        // AES-GCM combined format: nonce (12) + ciphertext (>= 1) + tag (16) = >= 29 bytes
        XCTAssertGreaterThan(packed.count, 28, "Packed output must contain nonce + ciphertext + tag")
    }

    func testPackOutputDiffersPerCall() throws {
        let sender = x25519.generateKeyPair()
        let recipient = x25519.generateKeyPair()

        let message = DIDCommMessage(
            id: "pack-002",
            type: "test",
            to: ["did:ssdid:bob"]
        )

        let packed1 = try packer.pack(
            message: message,
            senderPrivateKey: sender.privateKey,
            recipientPublicKey: recipient.publicKey
        )
        let packed2 = try packer.pack(
            message: message,
            senderPrivateKey: sender.privateKey,
            recipientPublicKey: recipient.publicKey
        )

        // AES-GCM uses random nonce, so outputs should differ
        XCTAssertNotEqual(packed1, packed2, "Packed output should differ due to random nonce")
    }

    // MARK: - Round-Trip (Pack + Unpack)

    func testPackUnpackRoundTrip() throws {
        let sender = x25519.generateKeyPair()
        let recipient = x25519.generateKeyPair()

        let original = DIDCommMessage(
            id: "rt-001",
            type: "https://didcomm.org/basicmessage/2.0/message",
            from: "did:ssdid:alice",
            to: ["did:ssdid:bob"],
            createdTime: 1700000000,
            body: ["content": AnyCodable("Hello, Bob!")]
        )

        let packed = try packer.pack(
            message: original,
            senderPrivateKey: sender.privateKey,
            recipientPublicKey: recipient.publicKey
        )

        let unpacked = try unpacker.unpack(
            packed: packed,
            recipientPrivateKey: recipient.privateKey,
            senderPublicKey: sender.publicKey
        )

        XCTAssertEqual(unpacked.id, original.id)
        XCTAssertEqual(unpacked.type, original.type)
        XCTAssertEqual(unpacked.from, original.from)
        XCTAssertEqual(unpacked.to, original.to)
        XCTAssertEqual(unpacked.createdTime, original.createdTime)
        XCTAssertEqual(unpacked.body, original.body)
    }

    func testRoundTripWithAttachments() throws {
        let sender = x25519.generateKeyPair()
        let recipient = x25519.generateKeyPair()

        let original = DIDCommMessage(
            id: "rt-att-001",
            type: "https://didcomm.org/issue-credential/3.0/offer-credential",
            from: "did:ssdid:issuer",
            to: ["did:ssdid:holder"],
            body: ["goal_code": AnyCodable("issue-vc")],
            attachments: [
                DIDCommAttachment(
                    id: "att-1",
                    mediaType: "application/json",
                    data: DIDCommAttachmentData(base64: "eyJ0eXBlIjoiY3JlZCJ9")
                )
            ]
        )

        let packed = try packer.pack(
            message: original,
            senderPrivateKey: sender.privateKey,
            recipientPublicKey: recipient.publicKey
        )
        let unpacked = try unpacker.unpack(
            packed: packed,
            recipientPrivateKey: recipient.privateKey,
            senderPublicKey: sender.publicKey
        )

        XCTAssertEqual(unpacked.attachments.count, 1)
        XCTAssertEqual(unpacked.attachments[0].id, "att-1")
        XCTAssertEqual(unpacked.attachments[0].mediaType, "application/json")
        XCTAssertEqual(unpacked.attachments[0].data.base64, "eyJ0eXBlIjoiY3JlZCJ9")
    }

    func testRoundTripWithEmptyBody() throws {
        let sender = x25519.generateKeyPair()
        let recipient = x25519.generateKeyPair()

        let original = DIDCommMessage(
            id: "rt-empty-001",
            type: "https://didcomm.org/trust-ping/2.0/ping",
            to: ["did:ssdid:bob"]
        )

        let packed = try packer.pack(
            message: original,
            senderPrivateKey: sender.privateKey,
            recipientPublicKey: recipient.publicKey
        )
        let unpacked = try unpacker.unpack(
            packed: packed,
            recipientPrivateKey: recipient.privateKey,
            senderPublicKey: sender.publicKey
        )

        XCTAssertEqual(unpacked.id, original.id)
        XCTAssertTrue(unpacked.body.isEmpty)
    }

    // MARK: - Decryption Failure

    func testUnpackWithWrongKeyFails() throws {
        let sender = x25519.generateKeyPair()
        let recipient = x25519.generateKeyPair()
        let wrongKey = x25519.generateKeyPair()

        let message = DIDCommMessage(
            id: "fail-001",
            type: "test",
            to: ["did:ssdid:bob"]
        )

        let packed = try packer.pack(
            message: message,
            senderPrivateKey: sender.privateKey,
            recipientPublicKey: recipient.publicKey
        )

        // Try to unpack with wrong recipient private key
        XCTAssertThrowsError(
            try unpacker.unpack(
                packed: packed,
                recipientPrivateKey: wrongKey.privateKey,
                senderPublicKey: sender.publicKey
            ),
            "Unpacking with wrong key must fail"
        )
    }

    func testUnpackWithTamperedDataFails() throws {
        let sender = x25519.generateKeyPair()
        let recipient = x25519.generateKeyPair()

        let message = DIDCommMessage(
            id: "tamper-001",
            type: "test",
            to: ["did:ssdid:bob"]
        )

        var packed = try packer.pack(
            message: message,
            senderPrivateKey: sender.privateKey,
            recipientPublicKey: recipient.publicKey
        )

        // Tamper with ciphertext (flip a byte past the nonce)
        if packed.count > 20 {
            packed[20] ^= 0xFF
        }

        XCTAssertThrowsError(
            try unpacker.unpack(
                packed: packed,
                recipientPrivateKey: recipient.privateKey,
                senderPublicKey: sender.publicKey
            ),
            "Unpacking tampered data must fail"
        )
    }

    func testUnpackTooShortDataFails() {
        let recipient = x25519.generateKeyPair()
        let sender = x25519.generateKeyPair()
        let tooShort = Data(repeating: 0x00, count: 10)

        XCTAssertThrowsError(
            try unpacker.unpack(
                packed: tooShort,
                recipientPrivateKey: recipient.privateKey,
                senderPublicKey: sender.publicKey
            ),
            "Unpacking data shorter than minimum must fail"
        )
    }

    // MARK: - HKDF

    func testHkdfSha256ProducesCorrectLength() {
        let ikm = Data(repeating: 0xAB, count: 32)
        let info = Data("test-info".utf8)

        let key = packer.hkdfSha256(ikm: ikm, info: info, length: 32)
        XCTAssertEqual(key.count, 32)

        let shortKey = packer.hkdfSha256(ikm: ikm, info: info, length: 16)
        XCTAssertEqual(shortKey.count, 16)
    }

    func testHkdfSha256IsDeterministic() {
        let ikm = Data(repeating: 0xCD, count: 32)
        let info = Data("DIDComm-authcrypt".utf8)

        let key1 = packer.hkdfSha256(ikm: ikm, info: info, length: 32)
        let key2 = packer.hkdfSha256(ikm: ikm, info: info, length: 32)

        XCTAssertEqual(key1, key2, "Same inputs must produce same derived key")
    }

    func testHkdfSha256DiffersForDifferentInfo() {
        let ikm = Data(repeating: 0xEF, count: 32)

        let key1 = packer.hkdfSha256(ikm: ikm, info: Data("info-a".utf8), length: 32)
        let key2 = packer.hkdfSha256(ikm: ikm, info: Data("info-b".utf8), length: 32)

        XCTAssertNotEqual(key1, key2, "Different info must produce different keys")
    }
}
