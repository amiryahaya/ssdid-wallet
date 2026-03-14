package my.ssdid.wallet.domain.oid4vp

data class CredentialQuery(
    val descriptors: List<CredentialQueryDescriptor>
)

data class CredentialQueryDescriptor(
    val id: String,
    val format: String,
    val vctFilter: String?,
    val requiredClaims: List<String>,
    val optionalClaims: List<String>
)

data class MatchResult(
    val descriptorId: String,
    val credentialId: String,
    val credentialType: String,
    val availableClaims: Map<String, ClaimInfo>,
    val source: CredentialSource
)

data class ClaimInfo(
    val name: String,
    val required: Boolean,
    val available: Boolean
)

enum class CredentialSource {
    SD_JWT_VC,
    IDENTITY
}
