package my.ssdid.sdk.api

import my.ssdid.sdk.domain.model.Algorithm
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.recovery.RecoveryManager

class RecoveryApi internal constructor(private val manager: RecoveryManager) {
    suspend fun generateRecoveryKey(identity: Identity): Result<ByteArray> =
        manager.generateRecoveryKey(identity)
    suspend fun hasRecoveryKey(keyId: String): Boolean =
        manager.hasRecoveryKey(keyId)
    suspend fun restoreWithRecoveryKey(
        did: String,
        recoveryPrivateKeyBase64: String,
        name: String,
        algorithm: Algorithm
    ): Result<Identity> =
        manager.restoreWithRecoveryKey(did, recoveryPrivateKeyBase64, name, algorithm)
}
