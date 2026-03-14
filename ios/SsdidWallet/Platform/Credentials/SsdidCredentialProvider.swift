import Foundation
import AuthenticationServices

/// Result of matching stored credentials against a presentation request.
struct MatchedCredential {
    let id: String
    let title: String
    let credentialRef: CredentialRef
}

/// Credential provider for system-mediated credential presentation.
/// Uses ASAuthorizationController for credential selection on iOS 18+.
/// Marked experimental — the iOS Digital Credentials API is evolving.
@available(iOS 16.0, *)
class SsdidCredentialProvider {

    private let vault: Vault

    init(vault: Vault) {
        self.vault = vault
    }

    /// Match stored credentials against a presentation request.
    /// Returns all SD-JWT VCs and mdocs currently held in the vault.
    func matchCredentials(
        sdJwtVcs: [StoredSdJwtVc] = [],
        request: [String: Any] = [:]
    ) async -> [MatchedCredential] {
        var matches: [MatchedCredential] = []

        // Match SD-JWT VCs passed explicitly (vault doesn't store these yet)
        for vc in sdJwtVcs {
            matches.append(MatchedCredential(
                id: vc.id,
                title: vc.type,
                credentialRef: .sdJwt(vc)
            ))
        }

        // Match mdocs from vault
        let mdocs = await vault.listMDocs()
        for mdoc in mdocs {
            matches.append(MatchedCredential(
                id: mdoc.id,
                title: formatDocType(mdoc.docType),
                credentialRef: .mdoc(mdoc)
            ))
        }

        return matches
    }

    /// Convert an ISO mdoc docType URN into a human-readable display name.
    func formatDocType(_ docType: String) -> String {
        switch docType {
        case "org.iso.18013.5.1.mDL":
            return "Mobile Driver's License"
        case "org.iso.23220.2.1.mID":
            return "Mobile Identity Document"
        default:
            return docType.components(separatedBy: ".").last ?? docType
        }
    }
}
