package my.ssdid.sdk.api

import my.ssdid.sdk.api.model.PairingStatus
import my.ssdid.sdk.domain.device.DeviceInfo
import my.ssdid.sdk.domain.device.DeviceManager
import my.ssdid.sdk.domain.device.PairingData
import my.ssdid.sdk.domain.model.Identity

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

    suspend fun checkPairingStatus(did: String, pairingId: String): Result<PairingStatus> =
        manager.checkPairingStatus(did, pairingId).map { resp ->
            PairingStatus(
                status = resp.status,
                deviceName = resp.device_name,
                publicKey = resp.public_key
            )
        }

    suspend fun approvePairing(identity: Identity, pairingId: String): Result<Unit> =
        manager.approvePairing(identity, pairingId)

    suspend fun listDevices(identity: Identity): Result<List<DeviceInfo>> =
        manager.listDevices(identity)

    suspend fun revokeDevice(identity: Identity, targetKeyId: String): Result<Unit> =
        manager.revokeDevice(identity, targetKeyId)
}
