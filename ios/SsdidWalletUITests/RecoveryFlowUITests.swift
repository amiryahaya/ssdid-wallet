import XCTest

final class RecoveryFlowUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["--ui-testing"]
        app.launch()
    }

    func testSettings_showsRecoveryOption() throws {
        let settingsTab = app.tabBars.buttons["Settings"]
        if settingsTab.waitForExistence(timeout: 5) {
            settingsTab.tap()
        }
        let recoveryCell = app.staticTexts["Recovery"]
        XCTAssertTrue(recoveryCell.waitForExistence(timeout: 3))
    }
}
