import XCTest
@testable import SsdidWallet

final class TtlProviderTests: XCTestCase {

    private var provider: TtlProvider!
    private var defaults: UserDefaults!

    override func setUp() {
        super.setUp()
        // Isolated UserDefaults to avoid shared state between tests
        defaults = UserDefaults(suiteName: "TtlProviderTestSuite") ?? .standard
        defaults.removeObject(forKey: "ssdid_bundle_ttl_days")
        provider = TtlProvider(userDefaults: defaults)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: "TtlProviderTestSuite")
        super.tearDown()
    }

    // MARK: - freshnessRatio tests

    func testFreshnessRatio_nearZeroForJustFetchedBundle() {
        // Fetched just now — ratio should be near 0
        let fetchedAt = ISO8601DateFormatter().string(from: Date())
        let ratio = provider.freshnessRatio(fetchedAt: fetchedAt)
        XCTAssertLessThan(ratio, 0.01, "Expected near-zero ratio for a just-fetched bundle")
    }

    func testFreshnessRatio_approximately0_5AtHalfTtl() {
        // 7-day TTL, fetched 3.5 days ago → ratio ≈ 0.5
        let halfTtl: TimeInterval = TimeInterval(7) * 86400 / 2
        let fetchedAt = ISO8601DateFormatter().string(from: Date().addingTimeInterval(-halfTtl))
        let ratio = provider.freshnessRatio(fetchedAt: fetchedAt)
        XCTAssertEqual(ratio, 0.5, accuracy: 0.02, "Expected ratio ≈ 0.5 at half TTL")
    }

    func testFreshnessRatio_1_0OrGreaterForExpiredBundle() {
        // Fetched 8 days ago with default 7-day TTL → expired, ratio ≥ 1.0
        let expiredAge: TimeInterval = TimeInterval(8) * 86400
        let fetchedAt = ISO8601DateFormatter().string(from: Date().addingTimeInterval(-expiredAge))
        let ratio = provider.freshnessRatio(fetchedAt: fetchedAt)
        XCTAssertGreaterThanOrEqual(ratio, 1.0, "Expected ratio ≥ 1.0 for an expired bundle")
    }

    func testFreshnessRatio_returns1_0ForUnparseableFetchedAt() {
        let ratio = provider.freshnessRatio(fetchedAt: "not-a-valid-timestamp")
        XCTAssertEqual(ratio, 1.0, "Expected ratio 1.0 when fetchedAt cannot be parsed")
    }

    // MARK: - isExpired tests

    func testIsExpired_returnsTrueWhenBundleAgeExceedsTtl() {
        // Fetched 8 days ago, TTL is 7 days → expired
        let expiredAge: TimeInterval = TimeInterval(8) * 86400
        let fetchedAt = ISO8601DateFormatter().string(from: Date().addingTimeInterval(-expiredAge))
        XCTAssertTrue(provider.isExpired(fetchedAt: fetchedAt))
    }

    func testIsExpired_returnsFalseWhenBundleAgeWithinTtl() {
        // Fetched 3 days ago, TTL is 7 days → not expired
        let age: TimeInterval = TimeInterval(3) * 86400
        let fetchedAt = ISO8601DateFormatter().string(from: Date().addingTimeInterval(-age))
        XCTAssertFalse(provider.isExpired(fetchedAt: fetchedAt))
    }

    func testIsExpired_returnsTrueForUnparseableTimestamp() {
        XCTAssertTrue(provider.isExpired(fetchedAt: "bad-timestamp"))
    }

    // MARK: - ttlDays tests

    func testTtlDays_returnsDefaultSevenWhenUnset() {
        XCTAssertEqual(provider.ttlDays, 7)
    }

    func testTtlDays_returnsUserConfiguredValue() {
        defaults.set(14, forKey: "ssdid_bundle_ttl_days")
        let fresh = TtlProvider(userDefaults: defaults)
        XCTAssertEqual(fresh.ttlDays, 14)
    }
}
