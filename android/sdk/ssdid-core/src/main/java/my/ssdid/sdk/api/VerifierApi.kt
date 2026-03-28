package my.ssdid.sdk.api

import my.ssdid.sdk.domain.model.DidDocument
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.verifier.Verifier

class VerifierApi internal constructor(private val verifier: Verifier) {
    suspend fun verifyCredential(credential: VerifiableCredential): Result<Boolean> =
        verifier.verifyCredential(credential)
    suspend fun verifySignature(did: String, keyId: String, signature: ByteArray, data: ByteArray): Result<Boolean> =
        verifier.verifySignature(did, keyId, signature, data)
    suspend fun resolveDid(did: String): Result<DidDocument> = verifier.resolveDid(did)
}
