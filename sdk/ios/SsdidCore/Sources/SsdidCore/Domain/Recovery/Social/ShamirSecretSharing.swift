import Foundation
import Security

/// Shamir's Secret Sharing over GF(256) using the irreducible polynomial x^8 + x^4 + x^3 + x + 1.
/// Splits a byte array secret into N shares with threshold K (any K shares can reconstruct).
enum ShamirSecretSharing {

    struct Share: Equatable {
        let index: Int
        let data: Data
    }

    enum ShamirError: Error {
        case emptySecret
        case thresholdTooLow(Int)
        case insufficientShares(total: Int, threshold: Int)
        case tooManyShares(Int)
        case needAtLeastTwoShares(Int)
        case unequalShareLengths
        case duplicateShareIndices
        case randomGenerationFailed(OSStatus)
        case cannotInvertZero
    }

    /// Split `secret` into `n` shares requiring `k` shares to reconstruct.
    /// - Parameters:
    ///   - secret: The secret bytes to split
    ///   - k: Minimum shares needed (threshold), must be >= 2
    ///   - n: Total shares to generate, must be >= k and <= 255
    static func split(secret: Data, threshold k: Int, shares n: Int) throws -> [Share] {
        guard !secret.isEmpty else { throw ShamirError.emptySecret }
        guard k >= 2 else { throw ShamirError.thresholdTooLow(k) }
        guard n >= k else { throw ShamirError.insufficientShares(total: n, threshold: k) }
        guard n <= 255 else { throw ShamirError.tooManyShares(n) }

        var shareArrays = Array(repeating: [UInt8](repeating: 0, count: secret.count), count: n)
        let secretBytes = [UInt8](secret)

        for byteIdx in secretBytes.indices {
            // Build random polynomial: coefficients[0] = secret byte, rest random
            var coefficients = [UInt8](repeating: 0, count: k)
            coefficients[0] = secretBytes[byteIdx]

            // Generate random coefficients using SecRandomCopyBytes
            var randomBytes = [UInt8](repeating: 0, count: k - 1)
            let status = SecRandomCopyBytes(kSecRandomDefault, randomBytes.count, &randomBytes)
            guard status == errSecSuccess else {
                throw ShamirError.randomGenerationFailed(status)
            }
            for i in 1 ..< k {
                coefficients[i] = randomBytes[i - 1]
            }

            // Evaluate polynomial at x = 1..n
            for shareIdx in 0 ..< n {
                let x = shareIdx + 1 // x values are 1-indexed
                shareArrays[shareIdx][byteIdx] = evaluatePolynomial(coefficients, x: x)
            }
        }

        return shareArrays.enumerated().map { idx, data in
            Share(index: idx + 1, data: Data(data))
        }
    }

    /// Reconstruct the secret from `k` or more shares.
    /// - Parameter shares: At least `k` shares (the threshold used during split)
    static func combine(shares: [Share]) throws -> Data {
        guard shares.count >= 2 else {
            throw ShamirError.needAtLeastTwoShares(shares.count)
        }
        let secretSize = shares[0].data.count
        guard shares.allSatisfy({ $0.data.count == secretSize }) else {
            throw ShamirError.unequalShareLengths
        }
        let indices = shares.map { $0.index }
        guard Set(indices).count == indices.count else {
            throw ShamirError.duplicateShareIndices
        }

        var result = [UInt8](repeating: 0, count: secretSize)

        for byteIdx in 0 ..< secretSize {
            // Lagrange interpolation at x = 0
            var value: Int = 0
            for i in shares.indices {
                let xi = shares[i].index
                let yi = Int(shares[i].data[byteIdx])
                var lagrange: Int = 1
                for j in shares.indices {
                    if i == j { continue }
                    let xj = shares[j].index
                    // lagrange *= (0 - xj) / (xi - xj) in GF(256)
                    lagrange = gfMul(lagrange, gfMul(xj, gfInverse(xi ^ xj)))
                }
                value = value ^ gfMul(yi, lagrange)
            }
            result[byteIdx] = UInt8(value & 0xFF)
        }
        return Data(result)
    }

    // MARK: - GF(256) arithmetic with irreducible polynomial 0x11B (x^8 + x^4 + x^3 + x + 1)

    private static func evaluatePolynomial(_ coefficients: [UInt8], x: Int) -> UInt8 {
        // Horner's method: result = c[k-1]*x + c[k-2], etc.
        var result: Int = 0
        for i in stride(from: coefficients.count - 1, through: 0, by: -1) {
            result = gfMul(result, x) ^ Int(coefficients[i])
        }
        return UInt8(result & 0xFF)
    }

    static func gfMul(_ a: Int, _ b: Int) -> Int {
        var aa = a
        var bb = b
        var result = 0
        while bb > 0 {
            if bb & 1 != 0 { result = result ^ aa }
            aa = aa << 1
            if aa & 0x100 != 0 { aa = aa ^ 0x11B }
            bb = bb >> 1
        }
        return result
    }

    static func gfInverse(_ a: Int) -> Int {
        precondition(a != 0, "Cannot invert zero in GF(256)")
        // a^254 = a^(-1) in GF(256) by Fermat's little theorem
        var result = a
        for _ in 0 ..< 6 {
            result = gfMul(result, result) // square
            result = gfMul(result, a)      // multiply by a
        }
        result = gfMul(result, result) // final square: total exponent = 254
        return result
    }
}
