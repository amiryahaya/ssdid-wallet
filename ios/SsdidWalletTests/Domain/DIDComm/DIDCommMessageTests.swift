import XCTest
@testable import SsdidWallet

final class DIDCommMessageTests: XCTestCase {

    // MARK: - Construction

    func testMessageConstruction() {
        let message = DIDCommMessage(
            id: "msg-001",
            type: "https://didcomm.org/basicmessage/2.0/message",
            from: "did:ssdid:alice",
            to: ["did:ssdid:bob"],
            createdTime: 1700000000,
            body: ["content": AnyCodable("Hello")],
            attachments: []
        )

        XCTAssertEqual(message.id, "msg-001")
        XCTAssertEqual(message.type, "https://didcomm.org/basicmessage/2.0/message")
        XCTAssertEqual(message.from, "did:ssdid:alice")
        XCTAssertEqual(message.to, ["did:ssdid:bob"])
        XCTAssertEqual(message.createdTime, 1700000000)
        XCTAssertTrue(message.attachments.isEmpty)
    }

    func testMessageConstructionWithDefaults() {
        let message = DIDCommMessage(
            id: "msg-002",
            type: "https://didcomm.org/trust-ping/2.0/ping",
            to: ["did:ssdid:bob"]
        )

        XCTAssertNil(message.from)
        XCTAssertNil(message.createdTime)
        XCTAssertTrue(message.body.isEmpty)
        XCTAssertTrue(message.attachments.isEmpty)
    }

    func testMessageWithMultipleRecipients() {
        let message = DIDCommMessage(
            id: "msg-003",
            type: "https://didcomm.org/basicmessage/2.0/message",
            from: "did:ssdid:alice",
            to: ["did:ssdid:bob", "did:ssdid:carol", "did:ssdid:dave"]
        )

        XCTAssertEqual(message.to.count, 3)
    }

    // MARK: - Codable Round-Trip

    func testCodableRoundTrip() throws {
        let original = DIDCommMessage(
            id: "rt-001",
            type: "https://didcomm.org/basicmessage/2.0/message",
            from: "did:ssdid:alice",
            to: ["did:ssdid:bob"],
            createdTime: 1700000000,
            body: [
                "content": AnyCodable("Hello, Bob!"),
                "lang": AnyCodable("en")
            ],
            attachments: []
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        let data = try encoder.encode(original)
        let decoded = try JSONDecoder().decode(DIDCommMessage.self, from: data)

        XCTAssertEqual(decoded.id, original.id)
        XCTAssertEqual(decoded.type, original.type)
        XCTAssertEqual(decoded.from, original.from)
        XCTAssertEqual(decoded.to, original.to)
        XCTAssertEqual(decoded.createdTime, original.createdTime)
        XCTAssertEqual(decoded.body, original.body)
    }

    func testCodableRoundTripWithNilFields() throws {
        let original = DIDCommMessage(
            id: "rt-002",
            type: "https://didcomm.org/trust-ping/2.0/ping",
            to: ["did:ssdid:bob"]
        )

        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(DIDCommMessage.self, from: data)

        XCTAssertEqual(decoded.id, original.id)
        XCTAssertNil(decoded.from)
        XCTAssertNil(decoded.createdTime)
    }

    func testCodableSnakeCaseMapping() throws {
        let message = DIDCommMessage(
            id: "sc-001",
            type: "test",
            to: ["did:ssdid:bob"],
            createdTime: 1234567890
        )

        let data = try JSONEncoder().encode(message)
        let jsonString = String(data: data, encoding: .utf8)!

        XCTAssertTrue(jsonString.contains("\"created_time\""), "Should use snake_case for createdTime")
        XCTAssertFalse(jsonString.contains("\"createdTime\""), "Should not use camelCase for createdTime")
    }

    // MARK: - Attachment

    func testAttachmentWithBase64Data() throws {
        let attachment = DIDCommAttachment(
            id: "att-001",
            mediaType: "application/json",
            data: DIDCommAttachmentData(base64: "SGVsbG8gV29ybGQ=")
        )

        let data = try JSONEncoder().encode(attachment)
        let decoded = try JSONDecoder().decode(DIDCommAttachment.self, from: data)

        XCTAssertEqual(decoded.id, "att-001")
        XCTAssertEqual(decoded.mediaType, "application/json")
        XCTAssertEqual(decoded.data.base64, "SGVsbG8gV29ybGQ=")
        XCTAssertNil(decoded.data.json)
    }

    func testAttachmentWithJsonData() throws {
        let jsonPayload: [String: AnyCodable] = ["key": AnyCodable("value")]
        let attachment = DIDCommAttachment(
            id: "att-002",
            data: DIDCommAttachmentData(json: jsonPayload)
        )

        let data = try JSONEncoder().encode(attachment)
        let decoded = try JSONDecoder().decode(DIDCommAttachment.self, from: data)

        XCTAssertEqual(decoded.id, "att-002")
        XCTAssertNil(decoded.mediaType)
        XCTAssertNil(decoded.data.base64)
        XCTAssertEqual(decoded.data.json, jsonPayload)
    }

    func testAttachmentMediaTypeSnakeCase() throws {
        let attachment = DIDCommAttachment(
            id: "att-003",
            mediaType: "text/plain",
            data: DIDCommAttachmentData(base64: "dGVzdA==")
        )

        let data = try JSONEncoder().encode(attachment)
        let jsonString = String(data: data, encoding: .utf8)!

        XCTAssertTrue(jsonString.contains("\"media_type\""), "Should use snake_case for mediaType")
        XCTAssertFalse(jsonString.contains("\"mediaType\""), "Should not use camelCase for mediaType")
    }

    func testMessageWithAttachments() throws {
        let message = DIDCommMessage(
            id: "msg-att-001",
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

        let data = try JSONEncoder().encode(message)
        let decoded = try JSONDecoder().decode(DIDCommMessage.self, from: data)

        XCTAssertEqual(decoded.attachments.count, 1)
        XCTAssertEqual(decoded.attachments[0].id, "att-1")
        XCTAssertEqual(decoded.attachments[0].data.base64, "eyJ0eXBlIjoiY3JlZCJ9")
    }

    // MARK: - Equatable

    func testMessageEquality() {
        let msg1 = DIDCommMessage(
            id: "eq-001",
            type: "test",
            from: "did:ssdid:alice",
            to: ["did:ssdid:bob"],
            body: ["key": AnyCodable("value")]
        )
        let msg2 = DIDCommMessage(
            id: "eq-001",
            type: "test",
            from: "did:ssdid:alice",
            to: ["did:ssdid:bob"],
            body: ["key": AnyCodable("value")]
        )
        XCTAssertEqual(msg1, msg2)
    }

    func testMessageInequality() {
        let msg1 = DIDCommMessage(id: "a", type: "test", to: ["did:ssdid:bob"])
        let msg2 = DIDCommMessage(id: "b", type: "test", to: ["did:ssdid:bob"])
        XCTAssertNotEqual(msg1, msg2)
    }
}
