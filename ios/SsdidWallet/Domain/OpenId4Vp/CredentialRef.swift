import Foundation

/// Unified reference to a stored credential, either SD-JWT VC or mdoc.
enum CredentialRef {
    case sdJwt(StoredSdJwtVc)
    case mdoc(StoredMDoc)
}
