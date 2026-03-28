package my.ssdid.sdk.api

import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.rotation.KeyRotationManager
import my.ssdid.sdk.domain.rotation.RotationStatus

class RotationApi internal constructor(private val manager: KeyRotationManager) {
    suspend fun prepare(identity: Identity): Result<String> = manager.prepareRotation(identity)
    suspend fun execute(identity: Identity): Result<Identity> = manager.executeRotation(identity)
    suspend fun getStatus(identity: Identity): RotationStatus = manager.getRotationStatus(identity)
}
