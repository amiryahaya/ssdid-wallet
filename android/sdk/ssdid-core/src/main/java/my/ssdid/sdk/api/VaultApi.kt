package my.ssdid.sdk.api

import my.ssdid.sdk.domain.model.Proof
import my.ssdid.sdk.domain.vault.Vault
import kotlinx.serialization.json.JsonObject

class VaultApi internal constructor(private val vault: Vault) {
    suspend fun sign(keyId: String, data: ByteArray): Result<ByteArray> = vault.sign(keyId, data)
    suspend fun createProof(
        keyId: String,
        document: JsonObject,
        proofPurpose: String,
        challenge: String? = null,
        domain: String? = null
    ): Result<Proof> = vault.createProof(keyId, document, proofPurpose, challenge, domain)
}
