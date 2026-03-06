package my.ssdid.mobile.domain.verifier

import my.ssdid.mobile.domain.model.DidDocument
import my.ssdid.mobile.domain.model.VerifiableCredential

interface Verifier {
    suspend fun resolveDid(did: String): Result<DidDocument>
    suspend fun verifySignature(did: String, keyId: String, signature: ByteArray, data: ByteArray): Result<Boolean>
    suspend fun verifyChallengeResponse(did: String, keyId: String, challenge: String, signedChallenge: String): Result<Boolean>
    suspend fun verifyCredential(credential: VerifiableCredential): Result<Boolean>
}
