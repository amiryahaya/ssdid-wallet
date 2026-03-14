import XCTest
@testable import SsdidWallet

final class MDocModelTests: XCTestCase {

    func testStoredMDocConstruction() {
        let cbor = Data([0xa1, 0x61, 0x61, 0x01])
        let mdoc = StoredMDoc(
            id: "mdoc-001",
            docType: "org.iso.18013.5.1.mDL",
            issuerSignedCbor: cbor,
            deviceKeyId: "did:ssdid:abc#key-1",
            issuedAt: 1700000000,
            expiresAt: 1700086400,
            nameSpaces: ["org.iso.18013.5.1": ["family_name", "given_name"]]
        )

        XCTAssertEqual(mdoc.id, "mdoc-001")
        XCTAssertEqual(mdoc.docType, "org.iso.18013.5.1.mDL")
        XCTAssertEqual(mdoc.issuerSignedCbor, cbor)
        XCTAssertEqual(mdoc.deviceKeyId, "did:ssdid:abc#key-1")
        XCTAssertEqual(mdoc.issuedAt, 1700000000)
        XCTAssertEqual(mdoc.expiresAt, 1700086400)
        XCTAssertEqual(mdoc.nameSpaces["org.iso.18013.5.1"], ["family_name", "given_name"])
    }

    func testStoredMDocDefaultValues() {
        let mdoc = StoredMDoc(
            id: "mdoc-002",
            docType: "org.iso.18013.5.1.mDL",
            issuerSignedCbor: Data(),
            deviceKeyId: "key-2",
            issuedAt: 1700000000
        )

        XCTAssertNil(mdoc.expiresAt)
        XCTAssertTrue(mdoc.nameSpaces.isEmpty)
    }

    func testStoredMDocEquality() {
        let mdoc1 = StoredMDoc(
            id: "mdoc-001",
            docType: "org.iso.18013.5.1.mDL",
            issuerSignedCbor: Data([0x01]),
            deviceKeyId: "key-1",
            issuedAt: 1700000000
        )
        let mdoc2 = StoredMDoc(
            id: "mdoc-001",
            docType: "different.type",
            issuerSignedCbor: Data([0x02]),
            deviceKeyId: "key-2",
            issuedAt: 1800000000
        )
        let mdoc3 = StoredMDoc(
            id: "mdoc-999",
            docType: "org.iso.18013.5.1.mDL",
            issuerSignedCbor: Data([0x01]),
            deviceKeyId: "key-1",
            issuedAt: 1700000000
        )

        XCTAssertEqual(mdoc1, mdoc2, "StoredMDoc equality should be based on id only")
        XCTAssertNotEqual(mdoc1, mdoc3)
    }

    func testStoredMDocSerializationRoundTrip() throws {
        let mdoc = StoredMDoc(
            id: "mdoc-001",
            docType: "org.iso.18013.5.1.mDL",
            issuerSignedCbor: Data([0xa1, 0x61, 0x61, 0x01]),
            deviceKeyId: "did:ssdid:abc#key-1",
            issuedAt: 1700000000,
            expiresAt: 1700086400,
            nameSpaces: ["org.iso.18013.5.1": ["family_name"]]
        )

        let encoder = JSONEncoder()
        let data = try encoder.encode(mdoc)
        let decoder = JSONDecoder()
        let decoded = try decoder.decode(StoredMDoc.self, from: data)

        XCTAssertEqual(decoded.id, mdoc.id)
        XCTAssertEqual(decoded.docType, mdoc.docType)
        XCTAssertEqual(decoded.issuerSignedCbor, mdoc.issuerSignedCbor)
        XCTAssertEqual(decoded.deviceKeyId, mdoc.deviceKeyId)
        XCTAssertEqual(decoded.issuedAt, mdoc.issuedAt)
        XCTAssertEqual(decoded.expiresAt, mdoc.expiresAt)
        XCTAssertEqual(decoded.nameSpaces, mdoc.nameSpaces)
    }

    func testIssuerSignedItemEquality() {
        let item1 = IssuerSignedItem(
            digestId: 0,
            random: Data([0x01, 0x02]),
            elementIdentifier: "family_name",
            elementValue: "Smith"
        )
        let item2 = IssuerSignedItem(
            digestId: 0,
            random: Data([0x03, 0x04]),
            elementIdentifier: "family_name",
            elementValue: "Jones"
        )
        let item3 = IssuerSignedItem(
            digestId: 1,
            random: Data([0x01, 0x02]),
            elementIdentifier: "given_name",
            elementValue: "John"
        )

        XCTAssertEqual(item1, item2, "Items with same digestId and elementIdentifier should be equal")
        XCTAssertNotEqual(item1, item3)
    }

    func testValidityInfoConstruction() {
        let validity = ValidityInfo(
            signed: "2024-01-01T00:00:00Z",
            validFrom: "2024-01-01T00:00:00Z",
            validUntil: "2025-01-01T00:00:00Z"
        )

        XCTAssertEqual(validity.signed, "2024-01-01T00:00:00Z")
        XCTAssertEqual(validity.validFrom, "2024-01-01T00:00:00Z")
        XCTAssertEqual(validity.validUntil, "2025-01-01T00:00:00Z")
    }

    func testDeviceKeyInfoEquality() {
        let info1 = DeviceKeyInfo(deviceKey: Data([0x01, 0x02, 0x03]))
        let info2 = DeviceKeyInfo(deviceKey: Data([0x01, 0x02, 0x03]))
        let info3 = DeviceKeyInfo(deviceKey: Data([0x04, 0x05, 0x06]))

        XCTAssertEqual(info1, info2)
        XCTAssertNotEqual(info1, info3)
    }
}
