import XCTest
@testable import SsdidWallet

final class ShamirSecretSharingTests: XCTestCase {

    // MARK: - Split and Combine

    func testSplitAndCombine2Of3ReconstructsSecret() throws {
        let secret = Data("my secret data".utf8)
        let shares = try ShamirSecretSharing.split(secret: secret, threshold: 2, shares: 3)
        let recovered = try ShamirSecretSharing.combine(shares: Array(shares[0..<2]))
        XCTAssertEqual(recovered, secret)
    }

    func testSplitAndCombine3Of5ReconstructsSecret() throws {
        let secret = Data("another secret".utf8)
        let shares = try ShamirSecretSharing.split(secret: secret, threshold: 3, shares: 5)
        let recovered = try ShamirSecretSharing.combine(shares: Array(shares[0..<3]))
        XCTAssertEqual(recovered, secret)
    }

    func testAnyKSubsetOf5SharesReconstructsSecret() throws {
        let secret = Data("test secret".utf8)
        let shares = try ShamirSecretSharing.split(secret: secret, threshold: 3, shares: 5)

        // Test all C(5,3) = 10 combinations
        let indices = [0, 1, 2, 3, 4]
        for i in 0..<indices.count {
            for j in (i + 1)..<indices.count {
                for k in (j + 1)..<indices.count {
                    let subset = [shares[indices[i]], shares[indices[j]], shares[indices[k]]]
                    let recovered = try ShamirSecretSharing.combine(shares: subset)
                    XCTAssertEqual(recovered, secret, "Failed for subset [\(i), \(j), \(k)]")
                }
            }
        }
    }

    func testFewerThanKSharesProducesWrongResult() throws {
        let secret = Data("secret".utf8)
        let shares = try ShamirSecretSharing.split(secret: secret, threshold: 3, shares: 5)

        // Only 2 shares when threshold is 3 — should produce wrong result
        let recovered = try ShamirSecretSharing.combine(shares: Array(shares[0..<2]))
        XCTAssertNotEqual(recovered, secret)
    }

    func testSingleByteSecretWorks() throws {
        let secret = Data([0x42])
        let shares = try ShamirSecretSharing.split(secret: secret, threshold: 2, shares: 3)
        let recovered = try ShamirSecretSharing.combine(shares: Array(shares[0..<2]))
        XCTAssertEqual(recovered, secret)
    }

    func testLargeSecretWorks() throws {
        var secret = Data(count: 256)
        for i in 0..<256 {
            secret[i] = UInt8(i & 0xFF)
        }
        let shares = try ShamirSecretSharing.split(secret: secret, threshold: 3, shares: 5)
        let recovered = try ShamirSecretSharing.combine(shares: Array(shares[0..<3]))
        XCTAssertEqual(recovered, secret)
    }

    func test2Of2MinimumThresholdWorks() throws {
        let secret = Data("minimum".utf8)
        let shares = try ShamirSecretSharing.split(secret: secret, threshold: 2, shares: 2)
        XCTAssertEqual(shares.count, 2)
        let recovered = try ShamirSecretSharing.combine(shares: shares)
        XCTAssertEqual(recovered, secret)
    }

    // MARK: - Share Properties

    func testShareIndicesAre1Indexed() throws {
        let secret = Data("index test".utf8)
        let shares = try ShamirSecretSharing.split(secret: secret, threshold: 2, shares: 3)
        let indices = shares.map { $0.index }
        XCTAssertEqual(indices, [1, 2, 3])
    }

    func testSharesHaveSameLengthAsSecret() throws {
        let secret = Data("length check".utf8)
        let shares = try ShamirSecretSharing.split(secret: secret, threshold: 2, shares: 3)
        for share in shares {
            XCTAssertEqual(share.data.count, secret.count)
        }
    }

    // MARK: - Validation Errors

    func testSplitRejectsEmptySecret() {
        XCTAssertThrowsError(try ShamirSecretSharing.split(secret: Data(), threshold: 2, shares: 3)) { error in
            guard let shamirError = error as? ShamirSecretSharing.ShamirError else {
                return XCTFail("Expected ShamirError, got \(error)")
            }
            guard case .emptySecret = shamirError else {
                return XCTFail("Expected .emptySecret, got \(shamirError)")
            }
        }
    }

    func testSplitRejectsThresholdLessThan2() {
        let secret = Data("test".utf8)
        XCTAssertThrowsError(try ShamirSecretSharing.split(secret: secret, threshold: 1, shares: 3)) { error in
            guard let shamirError = error as? ShamirSecretSharing.ShamirError else {
                return XCTFail("Expected ShamirError, got \(error)")
            }
            guard case .thresholdTooLow = shamirError else {
                return XCTFail("Expected .thresholdTooLow, got \(shamirError)")
            }
        }
    }

    func testSplitRejectsNLessThanK() {
        let secret = Data("test".utf8)
        XCTAssertThrowsError(try ShamirSecretSharing.split(secret: secret, threshold: 5, shares: 3)) { error in
            guard let shamirError = error as? ShamirSecretSharing.ShamirError else {
                return XCTFail("Expected ShamirError, got \(error)")
            }
            guard case .insufficientShares = shamirError else {
                return XCTFail("Expected .insufficientShares, got \(shamirError)")
            }
        }
    }

    func testSplitRejectsMoreThan255Shares() {
        let secret = Data("test".utf8)
        XCTAssertThrowsError(try ShamirSecretSharing.split(secret: secret, threshold: 2, shares: 256)) { error in
            guard let shamirError = error as? ShamirSecretSharing.ShamirError else {
                return XCTFail("Expected ShamirError, got \(error)")
            }
            guard case .tooManyShares = shamirError else {
                return XCTFail("Expected .tooManyShares, got \(shamirError)")
            }
        }
    }

    // MARK: - GF(256) Arithmetic

    func testGF256MultiplicationIdentity() {
        // a * 1 = a for all a
        for a in 0...255 {
            XCTAssertEqual(ShamirSecretSharing.gfMul(a, 1), a, "gfMul(\(a), 1) should equal \(a)")
        }
    }

    func testGF256MultiplicationByZero() {
        // a * 0 = 0 for all a
        for a in 0...255 {
            XCTAssertEqual(ShamirSecretSharing.gfMul(a, 0), 0, "gfMul(\(a), 0) should equal 0")
        }
    }

    func testGF256InverseRoundTrip() {
        // a * a^(-1) = 1 for all a in 1..255
        for a in 1...255 {
            let inv = ShamirSecretSharing.gfInverse(a)
            let product = ShamirSecretSharing.gfMul(a, inv)
            XCTAssertEqual(product, 1, "gfMul(\(a), gfInverse(\(a))) should equal 1")
        }
    }
}
