package my.ssdid.wallet.ui.offline

import my.ssdid.sdk.domain.model.DidDocument
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.verifier.Verifier
import java.io.IOException

class FakeOnlineVerifier(
    var shouldThrow: Throwable? = IOException("simulated network failure"),
    var shouldReturn: Boolean = true
) : Verifier {
    var verifyCallCount = 0
        private set

    override suspend fun resolveDid(did: String): Result<DidDocument> {
        shouldThrow?.let { return Result.failure(it) }
        return Result.failure(UnsupportedOperationException("Not implemented in fake"))
    }

    override suspend fun verifySignature(did: String, keyId: String, signature: ByteArray, data: ByteArray): Result<Boolean> {
        shouldThrow?.let { return Result.failure(it) }
        return Result.success(shouldReturn)
    }

    override suspend fun verifyChallengeResponse(did: String, keyId: String, challenge: String, signedChallenge: String): Result<Boolean> {
        shouldThrow?.let { return Result.failure(it) }
        return Result.success(shouldReturn)
    }

    override suspend fun verifyCredential(credential: VerifiableCredential): Result<Boolean> {
        verifyCallCount++
        shouldThrow?.let { return Result.failure(it) }
        return Result.success(shouldReturn)
    }
}
