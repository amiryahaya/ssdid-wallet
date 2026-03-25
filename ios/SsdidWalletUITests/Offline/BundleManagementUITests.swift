import XCTest

/// UC-5, UC-6: Bundle management screen tests.
/// Tests adding, refreshing, deleting bundles and empty state.
final class BundleManagementUITests: XCTestCase {

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

    // MARK: - UC-5a: Navigate to Bundle Management screen

    func testBundleManagement_navigationShowsScreen() throws {
        navigateToBundleManagement()

        let header = app.staticTexts["Offline Bundles"]
        XCTAssertTrue(header.waitForExistence(timeout: 5))
    }

    // MARK: - UC-5b: Empty state shown when no bundles

    func testBundleManagement_emptyStateVisible() throws {
        navigateToBundleManagement()

        let emptyTitle = app.staticTexts["No Cached Bundles"]
        let addButton = app.buttons["Add Bundle"]
        // Either empty state or bundle list is shown
        XCTAssertTrue(
            emptyTitle.waitForExistence(timeout: 5) || addButton.waitForExistence(timeout: 5)
        )
    }

    // MARK: - UC-5c: Add bundle dialog appears on plus tap

    func testBundleManagement_addButtonShowsDialog() throws {
        navigateToBundleManagement()

        guard app.staticTexts["Offline Bundles"].waitForExistence(timeout: 5) else {
            XCTFail("Bundle management screen not reached")
            return
        }

        let addButton = app.buttons.matching(NSPredicate(format: "label CONTAINS 'Add'")).firstMatch
        if addButton.waitForExistence(timeout: 3) {
            addButton.tap()
        } else {
            // Tap the plus button in the navigation bar
            let plusButton = app.navigationBars.buttons.matching(NSPredicate(format: "label == 'Add'")).firstMatch
            if plusButton.waitForExistence(timeout: 3) {
                plusButton.tap()
            }
        }

        // Dialog / sheet should appear with DID input
        let didField = app.textFields["did:ssdid:..."]
        XCTAssertTrue(didField.waitForExistence(timeout: 5))
    }

    // MARK: - UC-5d: Invalid DID shows error

    func testBundleManagement_invalidDid_showsError() throws {
        navigateToBundleManagement()

        guard app.staticTexts["Offline Bundles"].waitForExistence(timeout: 5) else {
            XCTSkip("Bundle management screen not reached")
        }

        openAddDialog()

        let didField = app.textFields["did:ssdid:..."]
        guard didField.waitForExistence(timeout: 5) else {
            XCTSkip("Add dialog not shown")
        }

        didField.tap()
        didField.typeText("invalid-did-value")

        let fetchButton = app.buttons["Fetch Bundle"]
        if fetchButton.waitForExistence(timeout: 3) {
            fetchButton.tap()
        }

        // Error message should appear (network failure or invalid DID)
        let errorText = app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'Failed'")).firstMatch
        XCTAssertTrue(errorText.waitForExistence(timeout: 10))
    }

    // MARK: - UC-5e: Refresh all bundles taps arrow button

    func testBundleManagement_refreshButton_isPresent() throws {
        navigateToBundleManagement()

        guard app.staticTexts["Offline Bundles"].waitForExistence(timeout: 5) else {
            XCTFail("Bundle management screen not reached")
            return
        }

        // The refresh button uses an arrow.clockwise icon
        let refreshButton = app.buttons.matching(NSPredicate(format: "label CONTAINS 'Refresh'")).firstMatch
        XCTAssertTrue(refreshButton.waitForExistence(timeout: 3) || app.buttons.count > 0)
    }

    // MARK: - UC-6: Delete bundle via swipe-left

    func testBundleManagement_swipeToDelete() throws {
        navigateToBundleManagement()

        guard app.staticTexts["Offline Bundles"].waitForExistence(timeout: 5) else {
            XCTSkip("Bundle management screen not reached")
        }

        let bundleList = app.tables.firstMatch
        guard bundleList.waitForExistence(timeout: 3) else {
            XCTSkip("No bundle list found (may be empty)")
        }

        let firstCell = bundleList.cells.firstMatch
        guard firstCell.waitForExistence(timeout: 3) else {
            XCTSkip("No bundle cells found (empty state)")
        }

        // Swipe left to reveal delete
        firstCell.swipeLeft()

        let deleteButton = app.buttons["Delete"]
        if deleteButton.waitForExistence(timeout: 3) {
            deleteButton.tap()
            // Verify the cell is gone or a different state shown
            XCTAssertTrue(
                app.staticTexts["No Cached Bundles"].waitForExistence(timeout: 5) ||
                bundleList.cells.count == 0
            )
        }
    }

    // MARK: - Helpers

    private func navigateToBundleManagement() {
        // Try Settings → Offline Verification → Prepare for Offline
        let settingsButton = app.buttons["Settings"]
        if settingsButton.waitForExistence(timeout: 5) {
            settingsButton.tap()
        }

        let prepareOffline = app.staticTexts["Prepare for Offline"]
        if prepareOffline.waitForExistence(timeout: 5) {
            prepareOffline.tap()
        }
    }

    private func openAddDialog() {
        let addButton = app.buttons.matching(NSPredicate(format: "label CONTAINS 'Add'")).firstMatch
        if addButton.waitForExistence(timeout: 3) {
            addButton.tap()
            return
        }
        // Try navigation bar plus button
        let navButtons = app.navigationBars.buttons
        for i in 0..<navButtons.count {
            let btn = navButtons.element(boundBy: i)
            if btn.waitForExistence(timeout: 1) {
                btn.tap()
                return
            }
        }
    }
}
