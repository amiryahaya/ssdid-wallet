import Foundation
import SsdidCore

/// Post-quantum cryptography provider for SSDID.
///
/// Supports KAZ-Sign (128/192/256), ML-DSA (FIPS 204), and SLH-DSA (FIPS 205) algorithms.
///
/// **Native dependencies:** The full implementation requires the KazSign, KazSignNative, and
/// LibOQS xcframeworks to be linked into the host application. When those frameworks are not
/// available, operations will throw `CryptoError.signingFailed` with a descriptive message.
///
/// To integrate the native libraries, add the corresponding xcframework binary targets to
/// the consuming project and replace the stub calls below with the real native bridge calls.
public final class PqcProvider: CryptoProvider {

    public init() {}

    public func supportsAlgorithm(_ algorithm: Algorithm) -> Bool {
        return algorithm.isPostQuantum
    }

    public func generateKeyPair(algorithm: Algorithm) throws -> KeyPairResult {
        guard algorithm.isPostQuantum else {
            throw CryptoError.unsupportedAlgorithm(algorithm)
        }

        if algorithm.isKazSign {
            return try generateKazSignKeyPair(algorithm: algorithm)
        }

        if algorithm.isMlDsa || algorithm.isSlhDsa {
            return try generateOqsKeyPair(algorithm: algorithm)
        }

        throw CryptoError.unsupportedAlgorithm(algorithm)
    }

    public func sign(algorithm: Algorithm, privateKey: Data, data: Data) throws -> Data {
        guard algorithm.isPostQuantum else {
            throw CryptoError.unsupportedAlgorithm(algorithm)
        }

        if algorithm.isKazSign {
            return try kazSignSign(algorithm: algorithm, privateKey: privateKey, data: data)
        }

        if algorithm.isMlDsa || algorithm.isSlhDsa {
            return try oqsSign(algorithm: algorithm, privateKey: privateKey, data: data)
        }

        throw CryptoError.unsupportedAlgorithm(algorithm)
    }

    public func verify(algorithm: Algorithm, publicKey: Data, signature: Data, data: Data) throws -> Bool {
        guard algorithm.isPostQuantum else {
            throw CryptoError.unsupportedAlgorithm(algorithm)
        }

        if algorithm.isKazSign {
            return try kazSignVerify(algorithm: algorithm, publicKey: publicKey, signature: signature, data: data)
        }

        if algorithm.isMlDsa || algorithm.isSlhDsa {
            return try oqsVerify(algorithm: algorithm, publicKey: publicKey, signature: signature, data: data)
        }

        throw CryptoError.unsupportedAlgorithm(algorithm)
    }

    // MARK: - KAZ-Sign Operations

    /// Security level mapping for KAZ-Sign algorithms.
    private func kazSignSecurityLevel(for algorithm: Algorithm) -> Int {
        switch algorithm {
        case .KAZ_SIGN_128: return 128
        case .KAZ_SIGN_192: return 192
        case .KAZ_SIGN_256: return 256
        default: fatalError("Not a KAZ-Sign algorithm: \(algorithm.rawValue)")
        }
    }

    /// KAZ-Sign key pair generation.
    /// Requires KazSign and KazSignNative xcframeworks.
    private func generateKazSignKeyPair(algorithm: Algorithm) throws -> KeyPairResult {
        // Native implementation requires:
        //   import KazSign
        //   import KazSignNative
        //   let signer = try KazSigner(level: securityLevel)
        //   let keyPair = try signer.generateKeyPair()
        //   let derPublicKey = try signer.publicKeyToDer(keyPair.publicKey)
        //   let derPrivateKey = try signer.privateKeyToDer(keyPair.secretKey)
        //   return KeyPairResult(publicKey: derPublicKey, privateKey: derPrivateKey)
        let level = kazSignSecurityLevel(for: algorithm)
        throw CryptoError.keyGenerationFailed(
            "KAZ-Sign \(level) requires native KazSign xcframework. Link KazSign and KazSignNative to enable."
        )
    }

    /// KAZ-Sign signing.
    /// Requires KazSign and KazSignNative xcframeworks.
    private func kazSignSign(algorithm: Algorithm, privateKey: Data, data: Data) throws -> Data {
        let level = kazSignSecurityLevel(for: algorithm)
        throw CryptoError.signingFailed(
            "KAZ-Sign \(level) requires native KazSign xcframework. Link KazSign and KazSignNative to enable."
        )
    }

    /// KAZ-Sign verification.
    /// Requires KazSign and KazSignNative xcframeworks.
    private func kazSignVerify(algorithm: Algorithm, publicKey: Data, signature: Data, data: Data) throws -> Bool {
        let level = kazSignSecurityLevel(for: algorithm)
        throw CryptoError.verificationFailed(
            "KAZ-Sign \(level) requires native KazSign xcframework. Link KazSign and KazSignNative to enable."
        )
    }

    // MARK: - OQS Operations (ML-DSA / SLH-DSA)

    /// OQS key pair generation.
    /// Requires LibOQS xcframework.
    private func generateOqsKeyPair(algorithm: Algorithm) throws -> KeyPairResult {
        // Native implementation requires:
        //   import LibOQS
        //   let sig = try OQSSig(algorithm: algorithm.oqsSigAlgorithm)
        //   let keyPair = try sig.generateKeyPair()
        //   return KeyPairResult(publicKey: keyPair.publicKey, privateKey: keyPair.secretKey)
        throw CryptoError.keyGenerationFailed(
            "\(algorithm.rawValue) requires native LibOQS xcframework. Link LibOQS to enable."
        )
    }

    /// OQS signing.
    /// Requires LibOQS xcframework.
    private func oqsSign(algorithm: Algorithm, privateKey: Data, data: Data) throws -> Data {
        throw CryptoError.signingFailed(
            "\(algorithm.rawValue) requires native LibOQS xcframework. Link LibOQS to enable."
        )
    }

    /// OQS verification.
    /// Requires LibOQS xcframework.
    private func oqsVerify(algorithm: Algorithm, publicKey: Data, signature: Data, data: Data) throws -> Bool {
        throw CryptoError.verificationFailed(
            "\(algorithm.rawValue) requires native LibOQS xcframework. Link LibOQS to enable."
        )
    }

    // MARK: - OID Patch Utilities

    /// The iOS xcframework C library may encode the public key OID as
    /// `1.3.6.1.4.1.62395.2.1.2` (KEM arc) instead of the correct
    /// `1.3.6.1.4.1.62395.1.1.2` (SIGN arc) expected by the registry.
    /// These utilities patch the single differing byte in DER-encoded SPKI.

    private static let wrongOidFragment: [UInt8] = [
        0x2B, 0x06, 0x01, 0x04, 0x01, 0x83, 0xE7, 0x3B, 0x02, 0x01, 0x02
    ]
    private static let correctOidFragment: [UInt8] = [
        0x2B, 0x06, 0x01, 0x04, 0x01, 0x83, 0xE7, 0x3B, 0x01, 0x01, 0x02
    ]

    /// Patches the wrong KEM OID to the correct SIGN OID in a DER-encoded public key.
    func patchPublicKeyOid(_ derKey: Data) -> Data {
        var patched = derKey
        if let range = patched.range(of: Data(Self.wrongOidFragment)) {
            patched.replaceSubrange(range, with: Self.correctOidFragment)
        }
        return patched
    }

    /// Reverses the OID patch so the C library can decode a corrected DER key.
    func unpatchPublicKeyOid(_ derKey: Data) -> Data {
        var unpatched = derKey
        if let range = unpatched.range(of: Data(Self.correctOidFragment)) {
            unpatched.replaceSubrange(range, with: Self.wrongOidFragment)
        }
        return unpatched
    }
}
