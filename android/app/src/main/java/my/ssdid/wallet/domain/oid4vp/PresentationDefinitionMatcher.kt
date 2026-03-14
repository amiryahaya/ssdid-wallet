package my.ssdid.wallet.domain.oid4vp

import kotlinx.serialization.json.*
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc

data class MatchResult(
    val credentialRef: CredentialRef,
    val descriptorId: String,
    val requiredClaims: List<String>,
    val optionalClaims: List<String>
)

class PresentationDefinitionMatcher {

    fun match(pd: JsonObject, credentials: List<StoredSdJwtVc>): List<MatchResult> {
        val descriptors = pd["input_descriptors"]?.jsonArray ?: return emptyList()
        val results = mutableListOf<MatchResult>()

        for (desc in descriptors) {
            val obj = desc.jsonObject
            val descriptorId = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
            val format = obj["format"]?.jsonObject
            if (format != null && !format.containsKey("vc+sd-jwt")) continue
            val fields = obj["constraints"]?.jsonObject?.get("fields")?.jsonArray ?: continue

            for (cred in credentials) {
                if (matchesConstraints(cred, fields)) {
                    val (required, optional) = extractClaims(fields, cred)
                    results.add(MatchResult(CredentialRef.SdJwt(cred), descriptorId, required, optional))
                }
            }
        }
        return results
    }

    private fun matchesConstraints(cred: StoredSdJwtVc, fields: JsonArray): Boolean {
        for (field in fields) {
            val obj = field.jsonObject
            val isOptional = obj["optional"]?.jsonPrimitive?.booleanOrNull == true
            if (isOptional) continue
            val paths = obj["path"]?.jsonArray?.map { it.jsonPrimitive.content } ?: continue
            val filterConst = obj["filter"]?.jsonObject?.get("const")?.jsonPrimitive?.contentOrNull

            for (path in paths) {
                if (path == "$.vct" && filterConst != null) {
                    if (cred.type != filterConst) return false
                } else {
                    val claimName = path.removePrefix("$.")
                    if (claimName !in cred.claims && claimName !in cred.disclosableClaims) return false
                }
            }
        }
        return true
    }

    private fun extractClaims(fields: JsonArray, cred: StoredSdJwtVc): Pair<List<String>, List<String>> {
        val required = mutableListOf<String>()
        val optional = mutableListOf<String>()
        for (field in fields) {
            val obj = field.jsonObject
            val paths = obj["path"]?.jsonArray?.map { it.jsonPrimitive.content } ?: continue
            val isOptional = obj["optional"]?.jsonPrimitive?.booleanOrNull == true
            for (path in paths) {
                if (path == "$.vct") continue
                val claimName = path.removePrefix("$.")
                if (claimName in cred.claims || claimName in cred.disclosableClaims) {
                    if (isOptional) optional.add(claimName) else required.add(claimName)
                }
            }
        }
        return required to optional
    }
}
