package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import my.ssdid.wallet.domain.mdoc.StoredMDoc
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import org.junit.Test

class DcqlMatcherMDocTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val matcher = DcqlMatcher()

    private val mdlMdoc = StoredMDoc(
        id = "mdoc-1",
        docType = "org.iso.18013.5.1.mDL",
        issuerSignedCbor = byteArrayOf(0x01),
        deviceKeyId = "key-1",
        issuedAt = 1700000000L,
        nameSpaces = mapOf(
            "org.iso.18013.5.1" to listOf("family_name", "given_name", "birth_date")
        )
    )

    private val sdJwtVc = StoredSdJwtVc(
        id = "vc-1", compact = "eyJ...~disc~",
        issuer = "did:ssdid:issuer1", subject = "did:ssdid:holder1",
        type = "IdentityCredential",
        claims = mapOf("name" to "Ahmad"),
        disclosableClaims = listOf("name"),
        issuedAt = 1700000000L
    )

    @Test
    fun matchMDocByDocTypeValue() {
        val dcql = json.parseToJsonElement("""{
            "credentials": [{
                "id": "mdl-query",
                "format": "mso_mdoc",
                "meta": { "doctype_value": "org.iso.18013.5.1.mDL" },
                "claims": [
                    { "namespace": "org.iso.18013.5.1", "claim_name": "family_name" },
                    { "namespace": "org.iso.18013.5.1", "claim_name": "given_name" }
                ]
            }]
        }""") as JsonObject

        val results = matcher.matchAll(dcql, emptyList(), listOf(mdlMdoc))
        assertThat(results).hasSize(1)
        assertThat(results[0].descriptorId).isEqualTo("mdl-query")
        assertThat(results[0].credentialRef).isInstanceOf(CredentialRef.MDoc::class.java)
        assertThat((results[0].credentialRef as CredentialRef.MDoc).credential.id).isEqualTo("mdoc-1")
        assertThat(results[0].requiredClaims).containsExactly("family_name", "given_name")
    }

    @Test
    fun noMatchWhenDocTypeDiffers() {
        val dcql = json.parseToJsonElement("""{
            "credentials": [{
                "id": "query-1",
                "format": "mso_mdoc",
                "meta": { "doctype_value": "org.iso.18013.5.1.mVR" }
            }]
        }""") as JsonObject

        val results = matcher.matchAll(dcql, emptyList(), listOf(mdlMdoc))
        assertThat(results).isEmpty()
    }

    @Test
    fun matchMDocWithOptionalClaims() {
        val dcql = json.parseToJsonElement("""{
            "credentials": [{
                "id": "mdl-query",
                "format": "mso_mdoc",
                "meta": { "doctype_value": "org.iso.18013.5.1.mDL" },
                "claims": [
                    { "namespace": "org.iso.18013.5.1", "claim_name": "family_name" },
                    { "namespace": "org.iso.18013.5.1", "claim_name": "given_name", "optional": true }
                ]
            }]
        }""") as JsonObject

        val results = matcher.matchAll(dcql, emptyList(), listOf(mdlMdoc))
        assertThat(results).hasSize(1)
        assertThat(results[0].requiredClaims).containsExactly("family_name")
        assertThat(results[0].optionalClaims).containsExactly("given_name")
    }

    @Test
    fun skipSdJwtFormatForMDocMatching() {
        val dcql = json.parseToJsonElement("""{
            "credentials": [{
                "id": "cred-1",
                "format": "vc+sd-jwt",
                "meta": { "vct_values": ["IdentityCredential"] }
            }]
        }""") as JsonObject

        // matchAll with only mdocs should not match sd-jwt specs against mdocs
        val results = matcher.matchAll(dcql, emptyList(), listOf(mdlMdoc))
        assertThat(results).isEmpty()
    }

    @Test
    fun matchAllReturnsBothFormats() {
        val dcql = json.parseToJsonElement("""{
            "credentials": [
                {
                    "id": "sd-jwt-query",
                    "format": "vc+sd-jwt",
                    "meta": { "vct_values": ["IdentityCredential"] },
                    "claims": [{ "path": ["name"] }]
                },
                {
                    "id": "mdl-query",
                    "format": "mso_mdoc",
                    "meta": { "doctype_value": "org.iso.18013.5.1.mDL" },
                    "claims": [
                        { "namespace": "org.iso.18013.5.1", "claim_name": "family_name" }
                    ]
                }
            ]
        }""") as JsonObject

        val results = matcher.matchAll(dcql, listOf(sdJwtVc), listOf(mdlMdoc))
        assertThat(results).hasSize(2)
        val sdJwtResult = results.first { it.credentialRef is CredentialRef.SdJwt }
        val mdocResult = results.first { it.credentialRef is CredentialRef.MDoc }
        assertThat(sdJwtResult.descriptorId).isEqualTo("sd-jwt-query")
        assertThat(mdocResult.descriptorId).isEqualTo("mdl-query")
    }

    @Test
    fun matchMDocWithoutClaimsSpec() {
        val dcql = json.parseToJsonElement("""{
            "credentials": [{
                "id": "mdl-query",
                "format": "mso_mdoc",
                "meta": { "doctype_value": "org.iso.18013.5.1.mDL" }
            }]
        }""") as JsonObject

        val results = matcher.matchAll(dcql, emptyList(), listOf(mdlMdoc))
        assertThat(results).hasSize(1)
        // When no claims specified, all elements from nameSpaces are required
        assertThat(results[0].requiredClaims).containsExactly("family_name", "given_name", "birth_date")
    }

    @Test
    fun matchMDocSkipsClaimsNotInNameSpaces() {
        val dcql = json.parseToJsonElement("""{
            "credentials": [{
                "id": "mdl-query",
                "format": "mso_mdoc",
                "meta": { "doctype_value": "org.iso.18013.5.1.mDL" },
                "claims": [
                    { "namespace": "org.iso.18013.5.1", "claim_name": "family_name" },
                    { "namespace": "org.iso.18013.5.1", "claim_name": "portrait" }
                ]
            }]
        }""") as JsonObject

        val results = matcher.matchAll(dcql, emptyList(), listOf(mdlMdoc))
        // portrait is required but not in nameSpaces, so no match
        assertThat(results).isEmpty()
    }
}
