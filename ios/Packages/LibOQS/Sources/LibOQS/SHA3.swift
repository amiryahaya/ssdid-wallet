import Foundation
import LibOQSNative

/// SHA3-256 hash using LibOQS (Keccak-based, FIPS 202).
/// This is NOT the same as CryptoKit's SHA256 (SHA-2 family).
public enum SHA3 {

    /// Computes a SHA3-256 digest of the given data.
    /// Returns a 32-byte hash.
    public static func sha256(_ data: Data) -> Data {
        var output = Data(count: 32)
        output.withUnsafeMutableBytes { outPtr in
            data.withUnsafeBytes { inPtr in
                OQS_SHA3_sha3_256(
                    outPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    inPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    data.count
                )
            }
        }
        return output
    }
}
