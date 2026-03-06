package my.ssdid.mobile.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CredentialStatus(
    val id: String,
    val type: String,
    val statusPurpose: String,
    val statusListIndex: String,
    val statusListCredential: String
)
