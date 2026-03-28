package my.ssdid.sdk.domain.oid4vp

import kotlinx.serialization.json.*
import my.ssdid.sdk.domain.sdjwt.StoredSdJwtVc

class DcqlMatcher {

    fun match(dcql: JsonObject, credentials: List<StoredSdJwtVc>): List<MatchResult> {
        val credSpecs = dcql["credentials"]?.jsonArray ?: return emptyList()
        val results = mutableListOf<MatchResult>()

        for (spec in credSpecs) {
            val obj = spec.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
            val format = obj["format"]?.jsonPrimitive?.contentOrNull
            if (format != null && format != "vc+sd-jwt") continue

            val vctValues = obj["meta"]?.jsonObject
                ?.get("vct_values")?.jsonArray
                ?.map { it.jsonPrimitive.content }?.toSet()

            val claimsSpec = obj["claims"]?.jsonArray

            for (cred in credentials) {
                if (vctValues != null && cred.type !in vctValues) continue

                val (required, optional) = if (claimsSpec != null) {
                    extractClaims(claimsSpec, cred)
                } else {
                    cred.disclosableClaims to emptyList<String>()
                }

                results.add(MatchResult(cred, id, required, optional))
            }
        }
        return results
    }

    private fun extractClaims(
        claimsSpec: JsonArray,
        cred: StoredSdJwtVc
    ): Pair<List<String>, List<String>> {
        val required = mutableListOf<String>()
        val optional = mutableListOf<String>()
        for (claim in claimsSpec) {
            val obj = claim.jsonObject
            val paths = obj["path"]?.jsonArray?.map { it.jsonPrimitive.content } ?: continue
            val isOptional = obj["optional"]?.jsonPrimitive?.booleanOrNull == true
            val claimName = paths.firstOrNull() ?: continue
            if (claimName in cred.claims || claimName in cred.disclosableClaims) {
                if (isOptional) optional.add(claimName) else required.add(claimName)
            }
        }
        return required to optional
    }
}
