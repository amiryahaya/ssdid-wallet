import Foundation
import SsdidCore
import KazSign
import KazSignNative
import LibOQS

final class PqcProvider: CryptoProvider {

    func supportsAlgorithm(_ algorithm: Algorithm) -> Bool {
        return algorithm.isPostQuantum
    }

    func generateKeyPair(algorithm: Algorithm) throws -> KeyPairResult {
        guard algorithm.isPostQuantum else {
            throw CryptoError.unsupportedAlgorithm(algorithm)
        }

        if algorithm.isKazSign {
            let level = securityLevel(for: algorithm)
            let signer = try KazSigner(level: level)
            let keyPair = try signer.generateKeyPair()
            // Store DER-encoded keys to match Android and registry expectations
            // C library v2.0.2+ uses correct SIGN arc OID (62395.1.x) natively
            let derPublicKey = try signer.publicKeyToDer(keyPair.publicKey)
            let derPrivateKey = try signer.privateKeyToDer(keyPair.secretKey)
            return KeyPairResult(publicKey: derPublicKey, privateKey: derPrivateKey)
        }

        if let oqsAlg = algorithm.oqsSigAlgorithm {
            let sig = try OQSSig(algorithm: oqsAlg)
            let keyPair = try sig.generateKeyPair()
            return KeyPairResult(publicKey: keyPair.publicKey, privateKey: keyPair.secretKey)
        }

        throw CryptoError.unsupportedAlgorithm(algorithm)
    }

    func sign(algorithm: Algorithm, privateKey: Data, data: Data) throws -> Data {
        guard algorithm.isPostQuantum else {
            throw CryptoError.unsupportedAlgorithm(algorithm)
        }

        if algorithm.isKazSign {
            let level = securityLevel(for: algorithm)
            let signer = try KazSigner(level: level)

            // Decode DER private key back to raw for signing
            let rawPrivateKey = try signer.privateKeyFromDer(privateKey)

            // Use non-detached sign (single SHA-256, not double like signDetached)
            let result = try signer.sign(message: data, secretKey: rawPrivateKey)

            // Extract only the signature overhead (S1||S2||S3), excluding the appended message
            let sigOnly = result.signature.prefix(level.signatureOverhead)

            // Wrap in KazWire format (magic 0x67 0x52 + alg + type + version + raw sig)
            let wireSignature = try signatureToWire(level: level, signature: Data(sigOnly))
            return wireSignature
        }

        if let oqsAlg = algorithm.oqsSigAlgorithm {
            let sig = try OQSSig(algorithm: oqsAlg)
            return try sig.sign(message: data, secretKey: privateKey)
        }

        throw CryptoError.unsupportedAlgorithm(algorithm)
    }

    func verify(algorithm: Algorithm, publicKey: Data, signature: Data, data: Data) throws -> Bool {
        guard algorithm.isPostQuantum else {
            throw CryptoError.unsupportedAlgorithm(algorithm)
        }

        if algorithm.isKazSign {
            let level = securityLevel(for: algorithm)
            let signer = try KazSigner(level: level)

            // C library v2.0.2+ uses correct SIGN arc OID natively — no patching needed
            let rawPublicKey = try signer.publicKeyFromDer(publicKey)

            // Strip KazWire header if present
            let rawSignature = stripWireHeader(signature)

            // Reconstruct non-detached signature: S1||S2||S3 || message
            var fullSignature = rawSignature
            fullSignature.append(data)

            let result = try signer.verify(signature: fullSignature, publicKey: rawPublicKey)
            return result.isValid
        }

        if let oqsAlg = algorithm.oqsSigAlgorithm {
            let sig = try OQSSig(algorithm: oqsAlg)
            return try sig.verify(message: data, signature: signature, publicKey: publicKey)
        }

        throw CryptoError.unsupportedAlgorithm(algorithm)
    }

    // MARK: - Private

    private func securityLevel(for algorithm: Algorithm) -> SecurityLevel {
        switch algorithm {
        case .KAZ_SIGN_128: return .level128
        case .KAZ_SIGN_192: return .level192
        case .KAZ_SIGN_256: return .level256
        default: fatalError("Not a KAZ-Sign algorithm: \(algorithm.rawValue)")
        }
    }

    // MARK: - OID Patch

    /// The iOS xcframework C library encodes the public key OID as
    /// `1.3.6.1.4.1.62395.2.1.2` (KEM arc) instead of the correct
    /// `1.3.6.1.4.1.62395.1.1.2` (SIGN arc) expected by the registry.
    /// This patches the single differing byte in the DER-encoded SPKI.
    private static let wrongOidFragment: [UInt8] = [
        0x2B, 0x06, 0x01, 0x04, 0x01, 0x83, 0xE7, 0x3B, 0x02, 0x01, 0x02
    ]
    private static let correctOidFragment: [UInt8] = [
        0x2B, 0x06, 0x01, 0x04, 0x01, 0x83, 0xE7, 0x3B, 0x01, 0x01, 0x02
    ]

    /// Patches the wrong KEM OID to the correct SIGN OID in a DER-encoded public key.
    private func patchPublicKeyOid(_ derKey: Data) -> Data {
        var patched = derKey
        if let range = patched.range(of: Data(Self.wrongOidFragment)) {
            patched.replaceSubrange(range, with: Self.correctOidFragment)
        }
        return patched
    }

    /// Reverses the OID patch so the C library can decode a corrected DER key.
    private func unpatchPublicKeyOid(_ derKey: Data) -> Data {
        var unpatched = derKey
        if let range = unpatched.range(of: Data(Self.correctOidFragment)) {
            unpatched.replaceSubrange(range, with: Self.wrongOidFragment)
        }
        return unpatched
    }

    // MARK: - KazWire Format

    /// Wraps a raw signature in KazWire format using the C library function.
    private func signatureToWire(level: SecurityLevel, signature: Data) throws -> Data {
        let cLevel = kaz_sign_level_t(rawValue: UInt32(level.rawValue))
        // KazWire header is 5 bytes + raw signature
        let maxOutLen = Int(KAZ_WIRE_HEADER_LEN) + signature.count
        var out = Data(count: maxOutLen)
        var outLen = maxOutLen

        let result = out.withUnsafeMutableBytes { outPtr in
            signature.withUnsafeBytes { sigPtr in
                kaz_sign_sig_to_wire(
                    cLevel,
                    sigPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    sigPtr.count,
                    outPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    &outLen
                )
            }
        }

        guard result == 0 else {
            throw CryptoError.signingFailed("KazWire encoding failed with code \(result)")
        }

        return out.prefix(outLen)
    }

    /// Strips KazWire header (magic 0x67 0x52) if present, returning raw signature.
    private func stripWireHeader(_ signature: Data) -> Data {
        if signature.count > Int(KAZ_WIRE_HEADER_LEN)
            && signature[signature.startIndex] == UInt8(KAZ_WIRE_MAGIC_HI)
            && signature[signature.startIndex + 1] == UInt8(KAZ_WIRE_MAGIC_LO) {
            return signature.dropFirst(Int(KAZ_WIRE_HEADER_LEN))
        }
        return signature
    }
}

// MARK: - Algorithm → OQSSig.Algorithm mapping

extension Algorithm {

    var oqsSigAlgorithm: OQSSig.Algorithm? {
        switch self {
        case .ML_DSA_44: return .mlDsa44
        case .ML_DSA_65: return .mlDsa65
        case .ML_DSA_87: return .mlDsa87
        case .SLH_DSA_SHA2_128S: return .slhDsaSha2_128s
        case .SLH_DSA_SHA2_128F: return .slhDsaSha2_128f
        case .SLH_DSA_SHA2_192S: return .slhDsaSha2_192s
        case .SLH_DSA_SHA2_192F: return .slhDsaSha2_192f
        case .SLH_DSA_SHA2_256S: return .slhDsaSha2_256s
        case .SLH_DSA_SHA2_256F: return .slhDsaSha2_256f
        case .SLH_DSA_SHAKE_128S: return .slhDsaShake_128s
        case .SLH_DSA_SHAKE_128F: return .slhDsaShake_128f
        case .SLH_DSA_SHAKE_192S: return .slhDsaShake_192s
        case .SLH_DSA_SHAKE_192F: return .slhDsaShake_192f
        case .SLH_DSA_SHAKE_256S: return .slhDsaShake_256s
        case .SLH_DSA_SHAKE_256F: return .slhDsaShake_256f
        default: return nil
        }
    }
}
