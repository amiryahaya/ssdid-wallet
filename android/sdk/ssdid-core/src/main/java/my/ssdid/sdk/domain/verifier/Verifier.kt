package my.ssdid.sdk.domain.verifier

import my.ssdid.sdk.domain.model.DidDocument
import my.ssdid.sdk.domain.model.VerifiableCredential

interface Verifier {
    suspend fun resolveDid(did: String): Result<DidDocument>
    suspend fun verifySignature(did: String, keyId: String, signature: ByteArray, data: ByteArray): Result<Boolean>
    suspend fun verifyChallengeResponse(did: String, keyId: String, challenge: String, signedChallenge: String): Result<Boolean>
    suspend fun verifyCredential(credential: VerifiableCredential): Result<Boolean>
}
