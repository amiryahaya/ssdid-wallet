import Foundation

public enum Multicodec {
    public static let ed25519Pub = 0xed
    public static let p256Pub = 0x1200
    public static let p384Pub = 0x1201

    public static func decode(_ data: Data) throws -> (codec: Int, keyBytes: Data) {
        guard data.count >= 2 else {
            throw DidResolutionError.dataTooShort
        }
        let first = Int(data[0])
        if first & 0x80 == 0 {
            return (first, data.dropFirst())
        } else {
            guard data.count >= 3 else {
                throw DidResolutionError.dataTooShort
            }
            let second = Int(data[1])
            let codec = (first & 0x7F) | (second << 7)
            return (codec, data.dropFirst(2))
        }
    }
}
