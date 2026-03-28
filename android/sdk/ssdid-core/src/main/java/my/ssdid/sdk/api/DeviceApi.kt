package my.ssdid.sdk.api

import my.ssdid.sdk.domain.device.DeviceInfo
import my.ssdid.sdk.domain.device.DeviceManager
import my.ssdid.sdk.domain.device.PairingData
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.transport.dto.PairingStatusResponse

class DeviceApi internal constructor(private val manager: DeviceManager) {
    suspend fun initiatePairing(identity: Identity): Result<PairingData> =
        manager.initiatePairing(identity)
    suspend fun joinPairing(
        did: String,
        pairingId: String,
        challenge: String,
        identity: Identity,
        deviceName: String
    ): Result<String> = manager.joinPairing(did, pairingId, challenge, identity, deviceName)
    suspend fun checkPairingStatus(did: String, pairingId: String): Result<PairingStatusResponse> =
        manager.checkPairingStatus(did, pairingId)
    suspend fun approvePairing(identity: Identity, pairingId: String): Result<Unit> =
        manager.approvePairing(identity, pairingId)
    suspend fun listDevices(identity: Identity): Result<List<DeviceInfo>> =
        manager.listDevices(identity)
    suspend fun revokeDevice(identity: Identity, targetKeyId: String): Result<Unit> =
        manager.revokeDevice(identity, targetKeyId)
}
