import Foundation
import KazSign
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
            return KeyPairResult(publicKey: keyPair.publicKey, privateKey: keyPair.secretKey)
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
            return try signer.signDetached(data: data, secretKey: privateKey)
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
            return try signer.verifyDetached(data: data, signature: signature, publicKey: publicKey)
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
