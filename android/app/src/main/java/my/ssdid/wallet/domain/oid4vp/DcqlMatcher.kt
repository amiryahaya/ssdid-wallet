package my.ssdid.wallet.domain.oid4vp

import kotlinx.serialization.json.*
import my.ssdid.wallet.domain.mdoc.StoredMDoc
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc

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

                results.add(MatchResult(CredentialRef.SdJwt(cred), id, required, optional))
            }
        }
        return results
    }

    fun matchAll(
        dcql: JsonObject,
        sdJwtVcs: List<StoredSdJwtVc>,
        mdocs: List<StoredMDoc>
    ): List<MatchResult> {
        val credSpecs = dcql["credentials"]?.jsonArray ?: return emptyList()
        val results = mutableListOf<MatchResult>()

        for (spec in credSpecs) {
            val obj = spec.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: continue
            val format = obj["format"]?.jsonPrimitive?.contentOrNull

            when (format) {
                "mso_mdoc" -> {
                    val docTypeValue = obj["meta"]?.jsonObject
                        ?.get("doctype_value")?.jsonPrimitive?.contentOrNull
                    val claimsSpec = obj["claims"]?.jsonArray

                    for (mdoc in mdocs) {
                        if (docTypeValue != null && mdoc.docType != docTypeValue) continue
                        val (required, optional) = if (claimsSpec != null) {
                            if (!matchesMDocClaims(claimsSpec, mdoc)) continue
                            extractMDocClaims(claimsSpec, mdoc)
                        } else {
                            mdoc.nameSpaces.values.flatten() to emptyList()
                        }
                        results.add(MatchResult(CredentialRef.MDoc(mdoc), id, required, optional))
                    }
                }
                "vc+sd-jwt", null -> {
                    val vctValues = obj["meta"]?.jsonObject
                        ?.get("vct_values")?.jsonArray
                        ?.map { it.jsonPrimitive.content }?.toSet()
                    val claimsSpec = obj["claims"]?.jsonArray

                    for (cred in sdJwtVcs) {
                        if (vctValues != null && cred.type !in vctValues) continue
                        val (required, optional) = if (claimsSpec != null) {
                            extractClaims(claimsSpec, cred)
                        } else {
                            cred.disclosableClaims to emptyList<String>()
                        }
                        results.add(MatchResult(CredentialRef.SdJwt(cred), id, required, optional))
                    }
                }
            }
        }
        return results
    }

    private fun matchesMDocClaims(claimsSpec: JsonArray, mdoc: StoredMDoc): Boolean {
        for (claim in claimsSpec) {
            val obj = claim.jsonObject
            val isOptional = obj["optional"]?.jsonPrimitive?.booleanOrNull == true
            if (isOptional) continue
            val namespace = obj["namespace"]?.jsonPrimitive?.contentOrNull ?: continue
            val claimName = obj["claim_name"]?.jsonPrimitive?.contentOrNull ?: continue
            val elements = mdoc.nameSpaces[namespace] ?: return false
            if (claimName !in elements) return false
        }
        return true
    }

    private fun extractMDocClaims(
        claimsSpec: JsonArray,
        mdoc: StoredMDoc
    ): Pair<List<String>, List<String>> {
        val required = mutableListOf<String>()
        val optional = mutableListOf<String>()
        for (claim in claimsSpec) {
            val obj = claim.jsonObject
            val namespace = obj["namespace"]?.jsonPrimitive?.contentOrNull ?: continue
            val claimName = obj["claim_name"]?.jsonPrimitive?.contentOrNull ?: continue
            val isOptional = obj["optional"]?.jsonPrimitive?.booleanOrNull == true
            val elements = mdoc.nameSpaces[namespace] ?: continue
            if (claimName in elements) {
                if (isOptional) optional.add(claimName) else required.add(claimName)
            }
        }
        return required to optional
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
