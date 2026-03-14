import XCTest
@testable import SsdidWallet

final class MDocPresenterTests: XCTestCase {

    private func makeItem(digestId: Int, identifier: String, value: Any) -> IssuerSignedItem {
        IssuerSignedItem(
            digestId: digestId,
            random: Data([UInt8(digestId)]),
            elementIdentifier: identifier,
            elementValue: value
        )
    }

    private func makeSampleIssuerSigned() -> IssuerSigned {
        let mdlItems = [
            makeItem(digestId: 0, identifier: "family_name", value: "Smith"),
            makeItem(digestId: 1, identifier: "given_name", value: "John"),
            makeItem(digestId: 2, identifier: "birth_date", value: "1990-01-15"),
            makeItem(digestId: 3, identifier: "document_number", value: "DL123456"),
            makeItem(digestId: 4, identifier: "portrait", value: Data([0xff, 0xd8]))
        ]
        let aamvaItems = [
            makeItem(digestId: 0, identifier: "DHS_compliance", value: "F"),
            makeItem(digestId: 1, identifier: "organ_donor", value: true)
        ]
        return IssuerSigned(
            nameSpaces: [
                "org.iso.18013.5.1": mdlItems,
                "org.iso.18013.5.1.aamva": aamvaItems
            ],
            issuerAuth: Data([0xd2, 0x84])
        )
    }

    func testPresentFiltersToRequestedElements() {
        let issuerSigned = makeSampleIssuerSigned()
        let requested: [String: [String]] = [
            "org.iso.18013.5.1": ["family_name", "given_name"]
        ]

        let result = MDocPresenter.present(issuerSigned: issuerSigned, requestedElements: requested)

        XCTAssertEqual(result.nameSpaces.count, 1)
        XCTAssertEqual(result.nameSpaces["org.iso.18013.5.1"]?.count, 2)

        let identifiers = result.nameSpaces["org.iso.18013.5.1"]?.map(\.elementIdentifier) ?? []
        XCTAssertTrue(identifiers.contains("family_name"))
        XCTAssertTrue(identifiers.contains("given_name"))
        XCTAssertFalse(identifiers.contains("birth_date"))
        XCTAssertFalse(identifiers.contains("document_number"))
    }

    func testPresentPreservesIssuerAuth() {
        let issuerSigned = makeSampleIssuerSigned()
        let requested: [String: [String]] = [
            "org.iso.18013.5.1": ["family_name"]
        ]

        let result = MDocPresenter.present(issuerSigned: issuerSigned, requestedElements: requested)

        XCTAssertEqual(result.issuerAuth, issuerSigned.issuerAuth)
    }

    func testPresentMultipleNamespaces() {
        let issuerSigned = makeSampleIssuerSigned()
        let requested: [String: [String]] = [
            "org.iso.18013.5.1": ["family_name"],
            "org.iso.18013.5.1.aamva": ["organ_donor"]
        ]

        let result = MDocPresenter.present(issuerSigned: issuerSigned, requestedElements: requested)

        XCTAssertEqual(result.nameSpaces.count, 2)
        XCTAssertEqual(result.nameSpaces["org.iso.18013.5.1"]?.count, 1)
        XCTAssertEqual(result.nameSpaces["org.iso.18013.5.1.aamva"]?.count, 1)
        XCTAssertEqual(
            result.nameSpaces["org.iso.18013.5.1.aamva"]?.first?.elementIdentifier,
            "organ_donor"
        )
    }

    func testPresentUnknownNamespaceIgnored() {
        let issuerSigned = makeSampleIssuerSigned()
        let requested: [String: [String]] = [
            "com.unknown.namespace": ["some_field"]
        ]

        let result = MDocPresenter.present(issuerSigned: issuerSigned, requestedElements: requested)

        XCTAssertTrue(result.nameSpaces.isEmpty)
    }

    func testPresentUnknownElementsFilteredOut() {
        let issuerSigned = makeSampleIssuerSigned()
        let requested: [String: [String]] = [
            "org.iso.18013.5.1": ["family_name", "nonexistent_field"]
        ]

        let result = MDocPresenter.present(issuerSigned: issuerSigned, requestedElements: requested)

        XCTAssertEqual(result.nameSpaces["org.iso.18013.5.1"]?.count, 1)
        XCTAssertEqual(
            result.nameSpaces["org.iso.18013.5.1"]?.first?.elementIdentifier,
            "family_name"
        )
    }

    func testPresentEmptyRequest() {
        let issuerSigned = makeSampleIssuerSigned()
        let requested: [String: [String]] = [:]

        let result = MDocPresenter.present(issuerSigned: issuerSigned, requestedElements: requested)

        XCTAssertTrue(result.nameSpaces.isEmpty)
        XCTAssertEqual(result.issuerAuth, issuerSigned.issuerAuth)
    }
}
