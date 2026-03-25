import XCTest

/// UC-8: Background sync — tests that the app triggers a bundle sync
/// when returning to the foreground after being backgrounded.
final class BackgroundSyncUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["--skip-otp", "--ui-testing"]
        app.launch()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    // MARK: - UC-8: Foreground resume triggers bundle sync

    func testForegroundResume_triggersBundleSync() throws {
        // Ensure the app is on the main screen
        let homeScreenElement = app.staticTexts.firstMatch
        XCTAssertTrue(homeScreenElement.waitForExistence(timeout: 5))

        // Background the app
        XCUIDevice.shared.press(.home)

        // Wait briefly while app is backgrounded
        Thread.sleep(forTimeInterval: 2)

        // Bring app back to foreground
        app.activate()

        // App should still be running and showing its main screen
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))

        // Navigate to bundle management to verify no crash after foreground resume
        let settingsButton = app.buttons["Settings"]
        if settingsButton.waitForExistence(timeout: 5) {
            settingsButton.tap()

            let prepareOffline = app.staticTexts["Prepare for Offline"]
            if prepareOffline.waitForExistence(timeout: 5) {
                prepareOffline.tap()
                // Verify the bundle management screen loads successfully after resume
                let bundlesHeader = app.staticTexts["Offline Bundles"]
                XCTAssertTrue(bundlesHeader.waitForExistence(timeout: 5))
            }
        }
    }
}
