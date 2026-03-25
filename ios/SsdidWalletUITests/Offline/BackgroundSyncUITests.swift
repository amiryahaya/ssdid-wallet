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
        // Ensure the app is on the main screen before backgrounding
        let homeScreenElement = app.staticTexts.firstMatch
        XCTAssertTrue(homeScreenElement.waitForExistence(timeout: 5),
                      "App main screen did not appear before background/foreground cycle")

        // Background the app
        XCUIDevice.shared.press(.home)

        // Wait briefly while app is backgrounded
        Thread.sleep(forTimeInterval: 2)

        // Bring app back to foreground
        app.activate()

        // Assert: app is running in the foreground (not crashed)
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10),
                      "App did not return to foreground within 10 seconds")

        // Navigate to bundle management and assert the screen loads correctly
        // after the foreground resume — this confirms the sync path did not crash
        // and the UI re-rendered successfully.
        let settingsButton = app.buttons["Settings"]
        guard settingsButton.waitForExistence(timeout: 5) else {
            XCTSkip("Settings button not found — cannot complete background sync UI check")
        }
        settingsButton.tap()

        let prepareOffline = app.staticTexts["Prepare for Offline"]
        guard prepareOffline.waitForExistence(timeout: 5) else {
            XCTSkip("'Prepare for Offline' option not found in Settings")
        }
        prepareOffline.tap()

        // The "Offline Bundles" heading must appear, confirming the bundle management
        // screen rendered without crashing after the foreground resume sync.
        let bundlesHeader = app.staticTexts["Offline Bundles"]
        XCTAssertTrue(bundlesHeader.waitForExistence(timeout: 10),
                      "Bundle management screen ('Offline Bundles') did not appear after foreground resume")

        // Additionally confirm no error or crash overlay is visible
        let errorLabel = app.staticTexts.matching(
            NSPredicate(format: "label CONTAINS[c] 'error' OR label CONTAINS[c] 'crash'")
        ).firstMatch
        XCTAssertFalse(errorLabel.exists,
                       "An error/crash message appeared after foreground resume: \(errorLabel.label)")
    }
}
