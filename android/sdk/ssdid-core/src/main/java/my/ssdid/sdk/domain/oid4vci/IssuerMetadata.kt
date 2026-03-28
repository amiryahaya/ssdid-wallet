package my.ssdid.sdk.domain.oid4vci

import kotlinx.serialization.json.JsonObject

data class IssuerMetadata(
    val credentialIssuer: String,
    val credentialEndpoint: String,
    val credentialConfigurationsSupported: Map<String, JsonObject>,
    val tokenEndpoint: String,
    val authorizationEndpoint: String?
)
