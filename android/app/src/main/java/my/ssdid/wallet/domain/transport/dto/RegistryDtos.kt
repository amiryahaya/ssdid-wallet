package my.ssdid.wallet.domain.transport.dto

import kotlinx.serialization.Serializable
import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.model.Proof

@Serializable
data class RegisterDidRequest(
    val did_document: DidDocument,
    val proof: Proof
)

@Serializable
data class RegisterDidResponse(
    val did: String,
    val status: String
)

@Serializable
data class UpdateDidRequest(
    val did_document: DidDocument,
    val proof: Proof
)

@Serializable
data class DeactivateDidRequest(
    val proof: Proof
)

@Serializable
data class ChallengeResponse(
    val challenge: String,
    val expires_at: String? = null,
    val domain: String? = null
)

@Serializable
data class RegistryInfoResponse(
    val name: String,
    val version: String,
    val did_method: String,
    val supported_algorithms: List<String> = emptyList(),
    val supported_proof_types: List<String> = emptyList(),
    val policies: RegistryPolicies? = null
)

@Serializable
data class RegistryPolicies(
    val proof_max_age_seconds: Int? = null
)
