package my.ssdid.wallet.domain.oid4vp

import kotlinx.serialization.json.*
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc

class PresentationDefinitionMatcher {

    fun match(
        presentationDefinitionJson: String,
        storedCredentials: List<StoredSdJwtVc>
    ): List<MatchResult> {
        val query = toCredentialQuery(presentationDefinitionJson)
        return matchQuery(query, storedCredentials)
    }

    fun toCredentialQuery(presentationDefinitionJson: String): CredentialQuery {
        val pd = Json.parseToJsonElement(presentationDefinitionJson).jsonObject
        val descriptors = pd["input_descriptors"]?.jsonArray?.map { desc ->
            val obj = desc.jsonObject
            val id = obj["id"]!!.jsonPrimitive.content
            val format = obj["format"]?.jsonObject?.keys?.firstOrNull() ?: "vc+sd-jwt"
            val fields = obj["constraints"]?.jsonObject
                ?.get("fields")?.jsonArray ?: JsonArray(emptyList())

            var vctFilter: String? = null
            val requiredClaims = mutableListOf<String>()
            val optionalClaims = mutableListOf<String>()

            for (field in fields) {
                val fieldObj = field.jsonObject
                val path = fieldObj["path"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content ?: continue
                val filter = fieldObj["filter"]?.jsonObject
                val optional = fieldObj["optional"]?.jsonPrimitive?.booleanOrNull ?: false

                if (path == "$.vct" && filter != null) {
                    vctFilter = filter["const"]?.jsonPrimitive?.content
                    continue
                }

                val claimName = path.removePrefix("$.")
                if (optional) {
                    optionalClaims.add(claimName)
                } else {
                    requiredClaims.add(claimName)
                }
            }

            CredentialQueryDescriptor(
                id = id, format = format, vctFilter = vctFilter,
                requiredClaims = requiredClaims, optionalClaims = optionalClaims
            )
        } ?: emptyList()

        return CredentialQuery(descriptors)
    }

    companion object {
        fun matchQuery(
            query: CredentialQuery,
            storedCredentials: List<StoredSdJwtVc>
        ): List<MatchResult> {
            val results = mutableListOf<MatchResult>()
            for (descriptor in query.descriptors) {
                for (credential in storedCredentials) {
                    if (descriptor.vctFilter != null && credential.type != descriptor.vctFilter) continue
                    val allClaims = credential.claims.keys
                    val hasAllRequired = descriptor.requiredClaims.all { it in allClaims }
                    if (!hasAllRequired) continue

                    val claimInfoMap = mutableMapOf<String, ClaimInfo>()
                    for (claim in descriptor.requiredClaims) {
                        claimInfoMap[claim] = ClaimInfo(claim, required = true, available = claim in allClaims)
                    }
                    for (claim in descriptor.optionalClaims) {
                        claimInfoMap[claim] = ClaimInfo(claim, required = false, available = claim in allClaims)
                    }

                    results.add(MatchResult(
                        descriptorId = descriptor.id,
                        credentialId = credential.id,
                        credentialType = credential.type,
                        availableClaims = claimInfoMap,
                        source = CredentialSource.SD_JWT_VC
                    ))
                    break
                }
            }
            return results
        }
    }
}
