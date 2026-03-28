import Foundation

/// SHA3-256 (Keccak) implementation for SSDID.
///
/// This is a pure-Swift Keccak-f[1600] implementation conforming to FIPS 202.
/// Used for DID document proofs, pre-commitment hashes, and transaction binding.
public enum SHA3 {

    /// Computes a SHA3-256 digest of the given data. Returns 32 bytes.
    public static func sha256(_ data: Data) -> Data {
        let bytes = Array(data)
        let hash = keccak256(bytes)
        return Data(hash)
    }

    // MARK: - Keccak-f[1600] internals

    /// SHA3-256: rate = 1088 bits (136 bytes), capacity = 512 bits, output = 256 bits (32 bytes).
    private static let rate = 136
    private static let outputLength = 32

    private static func keccak256(_ input: [UInt8]) -> [UInt8] {
        // Pad to SHA3 (Keccak with domain separation 0x06)
        var padded = input
        // SHA3 domain separation: append 0x06, then pad with zeros, then set last byte OR 0x80
        let blockSize = rate
        let lastBlockLen = padded.count % blockSize
        let padLen = blockSize - lastBlockLen
        if padLen == 1 {
            padded.append(0x06 | 0x80)
        } else {
            padded.append(0x06)
            padded.append(contentsOf: [UInt8](repeating: 0, count: padLen - 2))
            padded.append(0x80)
        }

        // State: 5x5 array of 64-bit words = 25 words = 200 bytes
        var state = [UInt64](repeating: 0, count: 25)

        // Absorb
        let blocks = padded.count / blockSize
        for block in 0..<blocks {
            let offset = block * blockSize
            for i in 0..<(blockSize / 8) {
                let wordOffset = offset + i * 8
                var word: UInt64 = 0
                for b in 0..<8 {
                    word |= UInt64(padded[wordOffset + b]) << (b * 8)
                }
                state[i] ^= word
            }
            keccakF1600(&state)
        }

        // Squeeze (output <= rate, so single squeeze)
        var output = [UInt8](repeating: 0, count: outputLength)
        for i in 0..<(outputLength / 8) {
            let word = state[i]
            for b in 0..<8 {
                let idx = i * 8 + b
                if idx < outputLength {
                    output[idx] = UInt8((word >> (b * 8)) & 0xFF)
                }
            }
        }
        return output
    }

    private static func keccakF1600(_ state: inout [UInt64]) {
        for round in 0..<24 {
            // theta
            var c = [UInt64](repeating: 0, count: 5)
            for x in 0..<5 {
                c[x] = state[x] ^ state[x + 5] ^ state[x + 10] ^ state[x + 15] ^ state[x + 20]
            }
            var d = [UInt64](repeating: 0, count: 5)
            for x in 0..<5 {
                d[x] = c[(x + 4) % 5] ^ rotl64(c[(x + 1) % 5], 1)
            }
            for x in 0..<5 {
                for y in 0..<5 {
                    state[x + 5 * y] ^= d[x]
                }
            }

            // rho and pi
            var b = [UInt64](repeating: 0, count: 25)
            for x in 0..<5 {
                for y in 0..<5 {
                    let idx = x + 5 * y
                    let newIdx = y + 5 * ((2 * x + 3 * y) % 5)
                    b[newIdx] = rotl64(state[idx], rotationOffsets[idx])
                }
            }

            // chi
            for x in 0..<5 {
                for y in 0..<5 {
                    let idx = x + 5 * y
                    state[idx] = b[idx] ^ (~b[((x + 1) % 5) + 5 * y] & b[((x + 2) % 5) + 5 * y])
                }
            }

            // iota
            state[0] ^= roundConstants[round]
        }
    }

    private static func rotl64(_ x: UInt64, _ n: Int) -> UInt64 {
        guard n > 0 else { return x }
        return (x << n) | (x >> (64 - n))
    }

    /// Rotation offsets for rho step, indexed as [x + 5*y].
    private static let rotationOffsets: [Int] = [
         0,  1, 62, 28, 27,
        36, 44,  6, 55, 20,
         3, 10, 43, 25, 39,
        41, 45, 15, 21,  8,
        18,  2, 61, 56, 14
    ]

    /// Round constants for iota step.
    private static let roundConstants: [UInt64] = [
        0x0000000000000001, 0x0000000000008082, 0x800000000000808A, 0x8000000080008000,
        0x000000000000808B, 0x0000000080000001, 0x8000000080008081, 0x8000000000008009,
        0x000000000000008A, 0x0000000000000088, 0x0000000080008009, 0x000000008000000A,
        0x000000008000808B, 0x800000000000008B, 0x8000000000008089, 0x8000000000008003,
        0x8000000000008002, 0x8000000000000080, 0x000000000000800A, 0x800000008000000A,
        0x8000000080008081, 0x8000000000008080, 0x0000000080000001, 0x8000000080008008
    ]
}
