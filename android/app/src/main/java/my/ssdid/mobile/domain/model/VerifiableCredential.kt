package my.ssdid.mobile.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifiableCredential(
    @SerialName("@context") val context: List<String> = listOf("https://www.w3.org/2018/credentials/v1"),
    val id: String,
    val type: List<String>,
    val issuer: String,
    val issuanceDate: String,
    val expirationDate: String? = null,
    val credentialSubject: CredentialSubject,
    val proof: Proof
)

@Serializable
data class CredentialSubject(
    val id: String,
    val claims: Map<String, String> = emptyMap()
)
