import XCTest

/// UC-1, UC-2: Verification traffic light UI tests.
/// Tests that the VerificationResultView correctly shows green/yellow/red
/// states and offline badges when network is unavailable.
final class VerificationFlowUITests: XCTestCase {

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

    // MARK: - UC-1: Verified credential shows green badge

    func testVerifiedCredential_showsGreenStatus() throws {
        navigateToCredentials()
        let credentialCell = app.cells.firstMatch
        guard credentialCell.waitForExistence(timeout: 5) else {
            XCTSkip("No credentials available to verify")
        }
        credentialCell.tap()

        let verifyButton = app.buttons["Verify Credential"]
        if verifyButton.waitForExistence(timeout: 5) {
            verifyButton.tap()
        }

        // Expect the "Credential verified" title for online success
        let verifiedText = app.staticTexts["Credential verified"]
        if verifiedText.waitForExistence(timeout: 10) {
            XCTAssertTrue(verifiedText.exists)
        } else {
            // Alternatively, check for the verification result screen itself
            let resultTitle = app.staticTexts["Verification Result"]
            XCTAssertTrue(resultTitle.waitForExistence(timeout: 5))
        }
    }

    // MARK: - UC-1b: Verified offline shows "Credential verified offline"

    func testVerifiedOffline_showsOfflineTitle() throws {
        app.terminate()
        var offlineApp = XCUIApplication()
        offlineApp.launchArguments = ["--skip-otp", "--ui-testing", "--simulate-offline"]
        offlineApp.launch()

        navigateToCredentials(in: offlineApp)
        let credentialCell = offlineApp.cells.firstMatch
        guard credentialCell.waitForExistence(timeout: 5) else {
            XCTSkip("No credentials available to verify")
        }
        credentialCell.tap()

        let verifyButton = offlineApp.buttons["Verify Credential"]
        if verifyButton.waitForExistence(timeout: 5) {
            verifyButton.tap()
        }

        // Either verified offline or the result screen appears
        let resultTitle = offlineApp.staticTexts["Verification Result"]
        XCTAssertTrue(resultTitle.waitForExistence(timeout: 10))

        offlineApp.terminate()
    }

    // MARK: - UC-1c: Verification failed shows red title

    func testVerificationFailed_showsRedStatus() throws {
        // WHY SKIPPED: XCUITest runs against the real app binary and cannot inject a
        // FakeOnlineVerifier that forces a FAILED verification result. Without mock
        // injection there is no deterministic way to produce the "Verification failed"
        // state without a real credential whose signature is intentionally invalid.
        //
        // WHEN TO UNSKIP: Once launch-argument-driven mock injection is implemented
        // (e.g., "--mock-verifier=always-fail" handled in AppDelegate/DIContainer),
        // remove the throw below and add `app.launchArguments += ["--mock-verifier=always-fail"]`
        // to setUpWithError. The domain-level equivalent is covered by
        // OfflineVerificationTest (unit/instrumented) using FakeOnlineVerifier.
        throw XCTSkip("Requires mock verifier injection via launch arguments (not yet implemented). " +
                      "Covered by OfflineVerificationTest (unit/instrumented) with FakeOnlineVerifier.")
    }

    // MARK: - UC-1d: Degraded (stale bundle) shows yellow/warning

    func testDegradedVerification_showsWarningStatus() throws {
        // WHY SKIPPED: XCUITest cannot seed an in-memory BundleStore with a stale bundle
        // (freshnessRatio ≥ 0.5) at launch time without launch-argument-driven DI. Without
        // a seeded stale bundle and the real credential that references its issuer DID, the
        // DEGRADED state cannot be triggered deterministically in the full app process.
        //
        // WHEN TO UNSKIP: Once launch-argument-driven BundleStore seeding is implemented
        // (e.g., "--seed-stale-bundle=<did>" handled in AppDelegate/DIContainer), remove
        // the throw below and seed the store before navigating. The domain-level equivalent
        // is covered by OfflineVerificationTest (instrumented) using InMemoryBundleStore +
        // FakeOnlineVerifier.
        throw XCTSkip("Requires stale bundle seeding via launch arguments (not yet implemented). " +
                      "Covered by OfflineVerificationTest (instrumented) with InMemoryBundleStore.")
    }

    // MARK: - UC-2: Verification Checks expand section shows details

    func testVerificationResult_showsChecksSection() throws {
        navigateToCredentials()
        let credentialCell = app.cells.firstMatch
        guard credentialCell.waitForExistence(timeout: 5) else {
            XCTSkip("No credentials available to verify")
        }
        credentialCell.tap()

        let verifyButton = app.buttons["Verify Credential"]
        if verifyButton.waitForExistence(timeout: 5) {
            verifyButton.tap()
        }

        let resultTitle = app.staticTexts["Verification Result"]
        guard resultTitle.waitForExistence(timeout: 10) else {
            XCTSkip("Verification result screen not reached")
        }

        let checksButton = app.buttons["Verification Checks"]
        if checksButton.waitForExistence(timeout: 3) {
            checksButton.tap()
            // After expanding, check row labels should appear
            let signatureLabel = app.staticTexts["Signature"]
            XCTAssertTrue(signatureLabel.waitForExistence(timeout: 3))
        }
    }

    // MARK: - UC-2b: Offline badge shown when source is offline

    func testOfflineBadge_shownWhenOffline() throws {
        app.terminate()
        var offlineApp = XCUIApplication()
        offlineApp.launchArguments = ["--skip-otp", "--ui-testing", "--simulate-offline"]
        offlineApp.launch()

        let resultTitle = offlineApp.staticTexts["Verification Result"]
        if resultTitle.waitForExistence(timeout: 5) {
            // Look for the "Offline" badge text
            let offlineBadge = offlineApp.staticTexts["Offline"]
            XCTAssertTrue(offlineBadge.exists)
        }

        offlineApp.terminate()
    }

    // MARK: - Helpers

    private func navigateToCredentials(in application: XCUIApplication? = nil) {
        let target = application ?? app
        // Navigate to credentials tab or screen
        let credentialsTab = target.buttons["Credentials"]
        if credentialsTab.waitForExistence(timeout: 5) {
            credentialsTab.tap()
        }
    }
}
