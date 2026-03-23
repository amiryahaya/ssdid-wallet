import XCTest

final class IdentityCreationUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["--skip-otp", "--ui-testing"]
        app.launch()
    }

    func testCreateIdentity_showsDisplayNameField() throws {
        let createButton = app.buttons["Create Identity"]
        if createButton.waitForExistence(timeout: 5) {
            createButton.tap()
        }
        let displayNameField = app.textFields["Display Name"]
        XCTAssertTrue(displayNameField.waitForExistence(timeout: 3))
    }

    func testCreateIdentity_canEnterDisplayName() throws {
        let createButton = app.buttons["Create Identity"]
        if createButton.waitForExistence(timeout: 5) {
            createButton.tap()
        }
        let displayNameField = app.textFields["Display Name"]
        XCTAssertTrue(displayNameField.waitForExistence(timeout: 3))
        displayNameField.tap()
        displayNameField.typeText("Test User")
        XCTAssertEqual(displayNameField.value as? String, "Test User")
    }

    func testCreateIdentity_showsEmailField() throws {
        let createButton = app.buttons["Create Identity"]
        if createButton.waitForExistence(timeout: 5) {
            createButton.tap()
        }
        let emailField = app.textFields["Email"]
        XCTAssertTrue(emailField.waitForExistence(timeout: 3))
    }

    func testCreateIdentity_skipOtpAdvancesToAlgorithmStep() throws {
        let createButton = app.buttons["Create Identity"]
        if createButton.waitForExistence(timeout: 5) {
            createButton.tap()
        }
        let displayNameField = app.textFields["Display Name"]
        displayNameField.tap()
        displayNameField.typeText("Test User")
        let emailField = app.textFields["Email"]
        emailField.tap()
        emailField.typeText("test@example.com")
        let verifyButton = app.buttons["Verify"]
        if verifyButton.waitForExistence(timeout: 3) {
            verifyButton.tap()
        }
        let algorithmText = app.staticTexts["Algorithm"]
        XCTAssertTrue(algorithmText.waitForExistence(timeout: 5))
    }
}
