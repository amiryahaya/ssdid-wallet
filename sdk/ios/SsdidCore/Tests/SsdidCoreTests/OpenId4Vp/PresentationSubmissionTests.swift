import XCTest
@testable import SsdidCore

final class PresentationSubmissionTests: XCTestCase {

    func testToJsonContainsAllFields() throws {
        let submission = PresentationSubmission(
            id: "sub-123",
            definitionId: "pd-1",
            descriptorMap: [
                DescriptorMapEntry(id: "id-1", format: "vc+sd-jwt", path: "$")
            ]
        )
        let jsonStr = try submission.toJson()
        XCTAssertTrue(jsonStr.contains("\"id\":\"sub-123\""))
        XCTAssertTrue(jsonStr.contains("\"definition_id\":\"pd-1\""))
        XCTAssertTrue(jsonStr.contains("\"descriptor_map\""))
        XCTAssertTrue(jsonStr.contains("\"format\":\"vc+sd-jwt\""))
        XCTAssertTrue(jsonStr.contains("\"path\":\"$\""))
    }

    func testGeneratesUuidIdWhenNotProvided() throws {
        let submission = PresentationSubmission.create(
            definitionId: "pd-1",
            descriptorIds: ["id-1"]
        )
        let jsonStr = try submission.toJson()
        XCTAssertFalse(submission.id.isEmpty)
        XCTAssertEqual(submission.id.count, 36) // UUID format
        XCTAssertTrue(jsonStr.contains("\"definition_id\":\"pd-1\""))
    }
}
