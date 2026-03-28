import Foundation

public enum Algorithm: String, Codable, CaseIterable, Identifiable {
    // Classical
    case ED25519
    case ECDSA_P256
    case ECDSA_P384

    // KAZ-Sign (hybrid PQC)
    case KAZ_SIGN_128
    case KAZ_SIGN_192
    case KAZ_SIGN_256

    // ML-DSA (FIPS 204)
    case ML_DSA_44
    case ML_DSA_65
    case ML_DSA_87

    // SLH-DSA (FIPS 205) - SHA-2 variants
    case SLH_DSA_SHA2_128S
    case SLH_DSA_SHA2_128F
    case SLH_DSA_SHA2_192S
    case SLH_DSA_SHA2_192F
    case SLH_DSA_SHA2_256S
    case SLH_DSA_SHA2_256F

    // SLH-DSA (FIPS 205) - SHAKE variants
    case SLH_DSA_SHAKE_128S
    case SLH_DSA_SHAKE_128F
    case SLH_DSA_SHAKE_192S
    case SLH_DSA_SHAKE_192F
    case SLH_DSA_SHAKE_256S
    case SLH_DSA_SHAKE_256F

    public var id: String { rawValue }

    public var w3cType: String {
        switch self {
        case .ED25519:           return "Ed25519VerificationKey2020"
        case .ECDSA_P256:        return "EcdsaSecp256r1VerificationKey2019"
        case .ECDSA_P384:        return "EcdsaSecp384VerificationKey2019"
        case .KAZ_SIGN_128,
             .KAZ_SIGN_192,
             .KAZ_SIGN_256:      return "KazSignVerificationKey2024"
        case .ML_DSA_44:         return "MlDsa44VerificationKey2024"
        case .ML_DSA_65:         return "MlDsa65VerificationKey2024"
        case .ML_DSA_87:         return "MlDsa87VerificationKey2024"
        case .SLH_DSA_SHA2_128S: return "SlhDsaSha2128sVerificationKey2024"
        case .SLH_DSA_SHA2_128F: return "SlhDsaSha2128fVerificationKey2024"
        case .SLH_DSA_SHA2_192S: return "SlhDsaSha2192sVerificationKey2024"
        case .SLH_DSA_SHA2_192F: return "SlhDsaSha2192fVerificationKey2024"
        case .SLH_DSA_SHA2_256S: return "SlhDsaSha2256sVerificationKey2024"
        case .SLH_DSA_SHA2_256F: return "SlhDsaSha2256fVerificationKey2024"
        case .SLH_DSA_SHAKE_128S: return "SlhDsaShake128sVerificationKey2024"
        case .SLH_DSA_SHAKE_128F: return "SlhDsaShake128fVerificationKey2024"
        case .SLH_DSA_SHAKE_192S: return "SlhDsaShake192sVerificationKey2024"
        case .SLH_DSA_SHAKE_192F: return "SlhDsaShake192fVerificationKey2024"
        case .SLH_DSA_SHAKE_256S: return "SlhDsaShake256sVerificationKey2024"
        case .SLH_DSA_SHAKE_256F: return "SlhDsaShake256fVerificationKey2024"
        }
    }

    public var proofType: String {
        switch self {
        case .ED25519:           return "Ed25519Signature2020"
        case .ECDSA_P256:        return "EcdsaSecp256r1Signature2019"
        case .ECDSA_P384:        return "EcdsaSecp384Signature2019"
        case .KAZ_SIGN_128,
             .KAZ_SIGN_192,
             .KAZ_SIGN_256:      return "KazSignSignature2024"
        case .ML_DSA_44:         return "MlDsa44Signature2024"
        case .ML_DSA_65:         return "MlDsa65Signature2024"
        case .ML_DSA_87:         return "MlDsa87Signature2024"
        case .SLH_DSA_SHA2_128S: return "SlhDsaSha2128sSignature2024"
        case .SLH_DSA_SHA2_128F: return "SlhDsaSha2128fSignature2024"
        case .SLH_DSA_SHA2_192S: return "SlhDsaSha2192sSignature2024"
        case .SLH_DSA_SHA2_192F: return "SlhDsaSha2192fSignature2024"
        case .SLH_DSA_SHA2_256S: return "SlhDsaSha2256sSignature2024"
        case .SLH_DSA_SHA2_256F: return "SlhDsaSha2256fSignature2024"
        case .SLH_DSA_SHAKE_128S: return "SlhDsaShake128sSignature2024"
        case .SLH_DSA_SHAKE_128F: return "SlhDsaShake128fSignature2024"
        case .SLH_DSA_SHAKE_192S: return "SlhDsaShake192sSignature2024"
        case .SLH_DSA_SHAKE_192F: return "SlhDsaShake192fSignature2024"
        case .SLH_DSA_SHAKE_256S: return "SlhDsaShake256sSignature2024"
        case .SLH_DSA_SHAKE_256F: return "SlhDsaShake256fSignature2024"
        }
    }

    public var isPostQuantum: Bool {
        switch self {
        case .ED25519, .ECDSA_P256, .ECDSA_P384:
            return false
        default:
            return true
        }
    }

    public var kazSignLevel: Int? {
        switch self {
        case .KAZ_SIGN_128: return 128
        case .KAZ_SIGN_192: return 192
        case .KAZ_SIGN_256: return 256
        default: return nil
        }
    }

    public var isKazSign: Bool { kazSignLevel != nil }
    public var isMlDsa: Bool { rawValue.hasPrefix("ML_DSA") }
    public var isSlhDsa: Bool { rawValue.hasPrefix("SLH_DSA") }

    /// Reverse lookup from W3C verification method type.
    /// For KAZ-Sign (shared w3cType), returns KAZ_SIGN_128 as default;
    /// the actual level is detected from key size at runtime.
    public static func fromW3cType(_ type: String) -> Algorithm? {
        allCases.first { $0.w3cType == type }
    }

    /// Disambiguate KAZ-Sign security level from public key size.
    /// KAZ-Sign v2.0 public key sizes: 128->54B, 192->88B, 256->118B
    public static func kazSignFromKeySize(_ keySize: Int) -> Algorithm? {
        switch keySize {
        case 54:  return .KAZ_SIGN_128
        case 88:  return .KAZ_SIGN_192
        case 118: return .KAZ_SIGN_256
        default:  return nil
        }
    }
}
