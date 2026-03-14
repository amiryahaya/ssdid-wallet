package my.ssdid.wallet.domain.oid4vp

import kotlinx.serialization.json.*
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc

class DcqlMatcher {

    fun match(
        dcqlJson: String,
        storedCredentials: List<StoredSdJwtVc>
    ): List<MatchResult> {
        val query = toCredentialQuery(dcqlJson)
        return PresentationDefinitionMatcher.matchQuery(query, storedCredentials)
    }

    fun toCredentialQuery(dcqlJson: String): CredentialQuery {
        val root = Json.parseToJsonElement(dcqlJson).jsonObject
        val credentials = root["credentials"]?.jsonArray ?: return CredentialQuery(emptyList())

        val descriptors = credentials.map { cred ->
            val obj = cred.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("DCQL credential missing required 'id' field")
            val format = obj["format"]?.jsonPrimitive?.content ?: "vc+sd-jwt"
            val vctFilter = obj["meta"]?.jsonObject
                ?.get("vct_values")?.jsonArray
                ?.firstOrNull()?.jsonPrimitive?.content

            val requiredClaims = mutableListOf<String>()
            val optionalClaims = mutableListOf<String>()

            obj["claims"]?.jsonArray?.forEach { claim ->
                val claimObj = claim.jsonObject
                val path = claimObj["path"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content ?: return@forEach
                val optional = claimObj["optional"]?.jsonPrimitive?.booleanOrNull ?: false
                if (optional) optionalClaims.add(path) else requiredClaims.add(path)
            }

            CredentialQueryDescriptor(
                id = id, format = format, vctFilter = vctFilter,
                requiredClaims = requiredClaims, optionalClaims = optionalClaims
            )
        }

        return CredentialQuery(descriptors)
    }
}
