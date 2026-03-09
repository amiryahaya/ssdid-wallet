package my.ssdid.wallet.domain.device

import android.os.Build
import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.model.Identity
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import my.ssdid.wallet.domain.transport.dto.PairingApproveRequest
import my.ssdid.wallet.domain.transport.dto.PairingInitRequest
import my.ssdid.wallet.domain.transport.dto.PairingJoinRequest
import my.ssdid.wallet.domain.transport.dto.PairingStatusResponse
import my.ssdid.wallet.domain.vault.Vault
import java.util.UUID

class DeviceManager(
    private val vault: Vault,
    private val httpClient: SsdidHttpClient
) {
    suspend fun initiatePairing(identity: Identity): Result<PairingData> = runCatching {
        val challenge = UUID.randomUUID().toString()
        val resp = httpClient.registry.initPairing(
            did = identity.did,
            request = PairingInitRequest(
                did = identity.did,
                challenge = challenge,
                primary_key_id = identity.keyId
            )
        )
        PairingData(pairingId = resp.pairing_id, challenge = challenge, did = identity.did)
    }

    suspend fun joinPairing(
        did: String,
        pairingId: String,
        challenge: String,
        identity: Identity,
        deviceName: String
    ): Result<String> = runCatching {
        val sig = vault.sign(identity.keyId, challenge.toByteArray()).getOrThrow()
        val resp = httpClient.registry.joinPairing(
            did = did,
            pairingId = pairingId,
            request = PairingJoinRequest(
                pairing_id = pairingId,
                public_key = identity.publicKeyMultibase,
                signed_challenge = Multibase.encode(sig),
                device_name = deviceName,
                platform = "android"
            )
        )
        resp.status
    }

    suspend fun checkPairingStatus(did: String, pairingId: String): Result<PairingStatusResponse> = runCatching {
        httpClient.registry.getPairingStatus(did, pairingId)
    }

    suspend fun approvePairing(
        identity: Identity,
        pairingId: String
    ): Result<Unit> = runCatching {
        val approval = "approve:$pairingId".toByteArray()
        val sig = vault.sign(identity.keyId, approval).getOrThrow()
        httpClient.registry.approvePairing(
            did = identity.did,
            pairingId = pairingId,
            request = PairingApproveRequest(
                did = identity.did,
                key_id = identity.keyId,
                signed_approval = Multibase.encode(sig)
            )
        )
    }

    suspend fun listDevices(identity: Identity): Result<List<DeviceInfo>> = runCatching {
        val docResult = vault.buildDidDocument(identity.keyId)
        val doc = docResult.getOrThrow()
        val methods = doc.verificationMethod
        val deviceName = Build.MODEL ?: "This Device"
        if (methods.isEmpty()) {
            return@runCatching listOf(
                DeviceInfo(
                    deviceId = identity.keyId,
                    name = deviceName,
                    platform = "android",
                    keyId = identity.keyId,
                    enrolledAt = identity.createdAt,
                    isPrimary = true
                )
            )
        }
        methods.map { method ->
            val keyId = method.id
            DeviceInfo(
                deviceId = keyId,
                name = if (keyId == identity.keyId) deviceName else "Unknown Device",
                platform = if (keyId == identity.keyId) "android" else "unknown",
                keyId = keyId,
                enrolledAt = identity.createdAt,
                isPrimary = keyId == identity.keyId
            )
        }
    }

    suspend fun revokeDevice(identity: Identity, targetKeyId: String): Result<Unit> = runCatching {
        require(targetKeyId != identity.keyId) { "Cannot revoke primary device key" }
        // Remove the key from DID Document and publish update
        // The actual DID Document update would be handled by SsdidClient.updateDidDocument()
    }
}

data class PairingData(
    val pairingId: String,
    val challenge: String,
    val did: String
)
