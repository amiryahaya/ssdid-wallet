import Foundation
import zlib

enum BitstringParser {
    static func isRevoked(encodedList: String, index: Int) throws -> Bool {
        guard index >= 0 else {
            throw RevocationError.invalidIndex(index)
        }
        guard let compressed = Data(base64URLEncoded: encodedList) else {
            throw RevocationError.decodingFailed("Invalid Base64URL encoding")
        }
        let bitstring = try decompress(compressed)
        let bytePos = index / 8
        guard bytePos < bitstring.count else {
            throw RevocationError.invalidIndex(index)
        }
        let bitPos = 7 - (index % 8) // MSB first per W3C spec
        return (bitstring[bytePos] >> bitPos) & 1 == 1
    }

    private static func decompress(_ data: Data) throws -> Data {
        // GZIP decompression using zlib (available on all Apple platforms)
        guard data.count >= 2, data[data.startIndex] == 0x1f, data[data.startIndex + 1] == 0x8b else {
            throw RevocationError.decodingFailed("Data is not GZIP compressed")
        }
        return try data.gunzipped()
    }
}

enum RevocationError: Error, LocalizedError {
    case invalidIndex(Int)
    case decodingFailed(String)
    case fetchFailed(String)

    var errorDescription: String? {
        switch self {
        case .invalidIndex(let idx):
            return "Status list index out of range: \(idx)"
        case .decodingFailed(let reason):
            return "Bitstring decoding failed: \(reason)"
        case .fetchFailed(let reason):
            return "Status list fetch failed: \(reason)"
        }
    }
}

// MARK: - GZIP decompression

private extension Data {
    func gunzipped() throws -> Data {
        guard count > 0 else {
            throw RevocationError.decodingFailed("Empty data")
        }
        var stream = z_stream()
        let nsData = self as NSData
        stream.next_in = UnsafeMutablePointer<UInt8>(mutating: nsData.bytes.assumingMemoryBound(to: UInt8.self))
        stream.avail_in = uInt(count)

        // windowBits 15 + 32 enables automatic gzip/zlib header detection
        guard inflateInit2_(&stream, 15 + 32, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size)) == Z_OK else {
            throw RevocationError.decodingFailed("Failed to initialize zlib")
        }
        defer { inflateEnd(&stream) }

        var output = Data(capacity: count * 4)
        let bufferSize = 4096
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer { buffer.deallocate() }

        repeat {
            stream.next_out = buffer
            stream.avail_out = uInt(bufferSize)
            let status = inflate(&stream, Z_NO_FLUSH)
            guard status == Z_OK || status == Z_STREAM_END else {
                throw RevocationError.decodingFailed("zlib inflate error: \(status)")
            }
            let bytesWritten = bufferSize - Int(stream.avail_out)
            output.append(buffer, count: bytesWritten)
            if status == Z_STREAM_END { break }
        } while stream.avail_out == 0

        return output
    }
}
