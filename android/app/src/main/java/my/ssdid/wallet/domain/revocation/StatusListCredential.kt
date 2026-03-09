package my.ssdid.wallet.domain.revocation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import my.ssdid.wallet.domain.model.Proof

@Serializable
data class StatusListCredential(
    @SerialName("@context") val context: List<String> = emptyList(),
    val id: String? = null,
    val type: List<String>,
    val issuer: String,
    val credentialSubject: StatusListSubject,
    val proof: Proof? = null
)

@Serializable
data class StatusListSubject(
    val type: String,
    val statusPurpose: String,
    val encodedList: String
)
