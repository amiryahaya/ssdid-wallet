package my.ssdid.wallet.domain.device

import my.ssdid.wallet.domain.SsdidClient
import my.ssdid.sdk.domain.crypto.Multibase
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.transport.SsdidHttpClient
import my.ssdid.sdk.domain.transport.dto.PairingApproveRequest
import my.ssdid.sdk.domain.transport.dto.PairingInitRequest
import my.ssdid.sdk.domain.transport.dto.PairingJoinRequest
import my.ssdid.sdk.domain.transport.dto.PairingStatusResponse
import my.ssdid.sdk.domain.vault.Vault
import java.util.UUID

class DeviceManager(
    private val vault: Vault,
    private val httpClient: SsdidHttpClient,
    private val ssdidClient: () -> SsdidClient,
    private val deviceInfo: DeviceInfoProvider
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
        val sig = vault.sign(identity.keyId, "join:$challenge".toByteArray()).getOrThrow()
        val resp = httpClient.registry.joinPairing(
            did = did,
            pairingId = pairingId,
            request = PairingJoinRequest(
                pairing_id = pairingId,
                public_key = identity.publicKeyMultibase,
                signed_challenge = Multibase.encode(sig),
                device_name = deviceName,
                platform = deviceInfo.platform
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
        // Sync DID Document with registry after approval adds the new device key
        ssdidClient().updateDidDocument(identity.keyId).getOrThrow()
    }

    suspend fun listDevices(identity: Identity): Result<List<DeviceInfo>> = runCatching {
        val docResult = vault.buildDidDocument(identity.keyId)
        val doc = docResult.getOrThrow()
        val methods = doc.verificationMethod
        val deviceName = deviceInfo.deviceName
        if (methods.isEmpty()) {
            return@runCatching listOf(
                DeviceInfo(
                    deviceId = identity.keyId,
                    name = deviceName,
                    platform = deviceInfo.platform,
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
                platform = if (keyId == identity.keyId) deviceInfo.platform else "unknown",
                keyId = keyId,
                enrolledAt = identity.createdAt,
                isPrimary = keyId == identity.keyId
            )
        }
    }

    suspend fun revokeDevice(identity: Identity, targetKeyId: String): Result<Unit> = runCatching {
        require(targetKeyId != identity.keyId) { "Cannot revoke primary device key" }
        // Rebuild and publish DID Document — vault only stores the primary key,
        // so the rebuilt document excludes the revoked secondary device key
        ssdidClient().updateDidDocument(identity.keyId).getOrThrow()
    }
}

data class PairingData(
    val pairingId: String,
    val challenge: String,
    val did: String
)
