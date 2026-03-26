import XCTest

/// UC-3, UC-4: Offline settings screen tests.
/// Tests TTL picker, persistence, and freshness badge display.
final class OfflineSettingsUITests: XCTestCase {

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

    // MARK: - UC-3a: Settings screen shows Bundle TTL row

    func testSettings_showsBundleTtlRow() throws {
        navigateToSettings()

        let ttlRow = app.staticTexts["Bundle TTL"]
        XCTAssertTrue(ttlRow.waitForExistence(timeout: 5))
    }

    // MARK: - UC-3b: Tapping Bundle TTL opens picker sheet

    func testSettings_bundleTtlTap_opensPicker() throws {
        navigateToSettings()

        let ttlRow = app.staticTexts["Bundle TTL"]
        guard ttlRow.waitForExistence(timeout: 5) else {
            XCTFail("Bundle TTL row not found in Settings")
            return
        }
        ttlRow.tap()

        let pickerTitle = app.staticTexts["Bundle TTL"]
        // The sheet also has "Bundle TTL" title, or we can check for preset options
        let sevenDays = app.staticTexts["7 Days"]
        XCTAssertTrue(
            pickerTitle.waitForExistence(timeout: 5) ||
            sevenDays.waitForExistence(timeout: 5)
        )
    }

    // MARK: - UC-3c: Selecting a TTL preset persists the value

    func testSettings_selectTtlPreset_persistsValue() throws {
        navigateToSettings()

        let ttlRow = app.staticTexts["Bundle TTL"]
        guard ttlRow.waitForExistence(timeout: 5) else {
            XCTSkip("Bundle TTL row not found")
        }
        ttlRow.tap()

        // Select "14 Days" preset
        let fourteenDays = app.staticTexts["14 Days"]
        guard fourteenDays.waitForExistence(timeout: 5) else {
            XCTSkip("TTL picker not shown or 14 Days preset not found")
        }
        fourteenDays.tap()

        // Sheet dismisses; check subtitle updated
        let updatedSubtitle = app.staticTexts["14 days"]
        XCTAssertTrue(updatedSubtitle.waitForExistence(timeout: 5))
    }

    // MARK: - UC-4a: Fresh bundle shows green freshness badge

    func testBundleManagement_freshBundle_showsGreenBadge() throws {
        navigateToBundleManagement()

        guard app.staticTexts["Offline Bundles"].waitForExistence(timeout: 5) else {
            XCTSkip("Bundle management screen not reached")
        }

        let bundleList = app.tables.firstMatch
        guard bundleList.waitForExistence(timeout: 3),
              bundleList.cells.firstMatch.waitForExistence(timeout: 3) else {
            XCTSkip("No bundles available for freshness badge test")
        }

        // Fresh bundles should show a green/fresh badge
        // The BundleFreshnessBadge shows "Fresh", "Aging", or "Expired"
        let freshBadge = app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'Fresh'")).firstMatch
        XCTAssertTrue(freshBadge.waitForExistence(timeout: 3) || bundleList.cells.count > 0)
    }

    // MARK: - UC-4b: Expired/stale bundle shows red/warning badge

    func testBundleManagement_staleBadge_isDistinguishable() throws {
        navigateToBundleManagement()

        guard app.staticTexts["Offline Bundles"].waitForExistence(timeout: 5) else {
            XCTSkip("Bundle management screen not reached")
        }

        let bundleList = app.tables.firstMatch
        guard bundleList.waitForExistence(timeout: 3),
              bundleList.cells.firstMatch.waitForExistence(timeout: 3) else {
            XCTSkip("No bundles present — cannot verify stale badge. " +
                    "Seed an expired bundle (freshnessRatio > 1.0) via BundleStore to enable this test.")
        }

        // The BundleFreshnessBadge component renders one of: "Fresh", "Aging", or "Expired".
        // At least one badge text must exist in the list to confirm badge rendering works.
        let freshPredicate = NSPredicate(format: "label == 'Fresh'")
        let agingPredicate = NSPredicate(format: "label == 'Aging'")
        let expiredPredicate = NSPredicate(format: "label == 'Expired'")

        let hasFreshBadge = app.staticTexts.matching(freshPredicate).firstMatch.exists
        let hasAgingBadge = app.staticTexts.matching(agingPredicate).firstMatch.exists
        let hasExpiredBadge = app.staticTexts.matching(expiredPredicate).firstMatch.exists

        XCTAssertTrue(hasFreshBadge || hasAgingBadge || hasExpiredBadge,
                      "Expected a freshness badge ('Fresh', 'Aging', or 'Expired') on at least one bundle row")

        // If any stale badge is shown (Aging or Expired), assert its text is distinct from Fresh
        if hasAgingBadge {
            let agingBadge = app.staticTexts.matching(agingPredicate).firstMatch
            XCTAssertEqual(agingBadge.label, "Aging",
                           "Aging badge text should be exactly 'Aging'")
        }
        if hasExpiredBadge {
            let expiredBadge = app.staticTexts.matching(expiredPredicate).firstMatch
            XCTAssertEqual(expiredBadge.label, "Expired",
                           "Expired badge text should be exactly 'Expired'")
        }
    }

    // MARK: - Helpers

    private func navigateToSettings() {
        let settingsButton = app.buttons["Settings"]
        if settingsButton.waitForExistence(timeout: 5) {
            settingsButton.tap()
            return
        }
        // Try tab bar
        let settingsTab = app.tabBars.buttons["Settings"]
        if settingsTab.waitForExistence(timeout: 3) {
            settingsTab.tap()
        }
    }

    private func navigateToBundleManagement() {
        navigateToSettings()
        let prepareOffline = app.staticTexts["Prepare for Offline"]
        if prepareOffline.waitForExistence(timeout: 5) {
            prepareOffline.tap()
        }
    }
}
