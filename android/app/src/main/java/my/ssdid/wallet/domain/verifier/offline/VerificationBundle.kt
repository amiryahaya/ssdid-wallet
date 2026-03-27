package my.ssdid.wallet.domain.verifier.offline

import kotlinx.serialization.Serializable
import my.ssdid.sdk.domain.model.DidDocument
import my.ssdid.wallet.domain.revocation.StatusListCredential

/**
 * A self-contained verification bundle that enables offline credential verification.
 * Contains the issuer's DID Document and optionally a cached status list snapshot.
 */
@Serializable
data class VerificationBundle(
    val issuerDid: String,
    val didDocument: DidDocument,
    val statusList: StatusListCredential? = null,
    val fetchedAt: String,
    val expiresAt: String
)
