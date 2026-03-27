package my.ssdid.wallet.domain.oid4vci

import kotlinx.serialization.json.*
import my.ssdid.sdk.domain.util.parseQueryParam

data class TxCodeRequirement(
    val inputMode: String,
    val length: Int,
    val description: String?
)

data class CredentialOffer(
    val credentialIssuer: String,
    val credentialConfigurationIds: List<String>,
    val preAuthorizedCode: String? = null,
    val txCode: TxCodeRequirement? = null,
    val authorizationCodeGrant: Boolean = false,
    val issuerState: String? = null
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(jsonString: String): Result<CredentialOffer> = runCatching {
            val obj = json.parseToJsonElement(jsonString).jsonObject

            val issuer = obj["credential_issuer"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing credential_issuer")
            require(issuer.startsWith("https://")) { "credential_issuer must be HTTPS: $issuer" }

            val configIds = obj["credential_configuration_ids"]?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?: throw IllegalArgumentException("Missing credential_configuration_ids")
            require(configIds.isNotEmpty()) { "credential_configuration_ids must not be empty" }

            val grants = obj["grants"]?.jsonObject
                ?: throw IllegalArgumentException("Missing grants")

            val preAuthGrant = grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"]?.jsonObject
            val authCodeGrant = grants["authorization_code"]?.jsonObject

            require(preAuthGrant != null || authCodeGrant != null) { "Must have at least one grant type" }

            val preAuthCode = preAuthGrant?.get("pre-authorized_code")?.jsonPrimitive?.contentOrNull
            val txCodeObj = preAuthGrant?.get("tx_code")?.jsonObject
            val txCode = txCodeObj?.let {
                TxCodeRequirement(
                    inputMode = it["input_mode"]?.jsonPrimitive?.content ?: "numeric",
                    length = it["length"]?.jsonPrimitive?.int ?: 6,
                    description = it["description"]?.jsonPrimitive?.contentOrNull
                )
            }

            val issuerState = authCodeGrant?.get("issuer_state")?.jsonPrimitive?.contentOrNull

            CredentialOffer(
                credentialIssuer = issuer,
                credentialConfigurationIds = configIds,
                preAuthorizedCode = preAuthCode,
                txCode = txCode,
                authorizationCodeGrant = authCodeGrant != null,
                issuerState = issuerState
            )
        }

        fun parseFromUri(uriString: String): Result<CredentialOffer> = runCatching {
            val offerJson = parseQueryParam(uriString, "credential_offer")
            val offerUri = parseQueryParam(uriString, "credential_offer_uri")

            when {
                offerJson != null -> parse(offerJson).getOrThrow()
                offerUri != null -> {
                    require(offerUri.startsWith("https://")) { "credential_offer_uri must be HTTPS" }
                    throw UnsupportedOperationException("By-reference offers require network fetch")
                }
                else -> throw IllegalArgumentException("Missing credential_offer or credential_offer_uri")
            }
        }
    }
}
