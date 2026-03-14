package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import my.ssdid.wallet.domain.mdoc.StoredMDoc
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import org.junit.Test

class PresentationDefinitionMatcherMDocTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val matcher = PresentationDefinitionMatcher()

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
        id = "vc-1", compact = "eyJ...~disc1~disc2~",
        issuer = "did:ssdid:issuer1", subject = "did:ssdid:holder1",
        type = "IdentityCredential",
        claims = mapOf("name" to "Ahmad", "email" to "ahmad@example.com"),
        disclosableClaims = listOf("name", "email"),
        issuedAt = 1700000000L
    )

    @Test
    fun matchMDocByDocType() {
        val pd = json.parseToJsonElement("""{
            "id": "mdl-request",
            "input_descriptors": [{
                "id": "org.iso.18013.5.1.mDL",
                "format": { "mso_mdoc": {} },
                "constraints": {
                    "fields": [
                        { "path": ["${'$'}.doctype"], "filter": { "const": "org.iso.18013.5.1.mDL" } },
                        { "path": ["${'$'}['org.iso.18013.5.1']['family_name']"], "intent_to_retain": false },
                        { "path": ["${'$'}['org.iso.18013.5.1']['given_name']"], "intent_to_retain": false }
                    ]
                }
            }]
        }""") as JsonObject

        val results = matcher.matchMDoc(pd, listOf(mdlMdoc))
        assertThat(results).hasSize(1)
        assertThat(results[0].descriptorId).isEqualTo("org.iso.18013.5.1.mDL")
        assertThat(results[0].credentialRef).isInstanceOf(CredentialRef.MDoc::class.java)
        assertThat((results[0].credentialRef as CredentialRef.MDoc).credential.id).isEqualTo("mdoc-1")
        assertThat(results[0].requiredClaims).containsExactly("family_name", "given_name")
    }

    @Test
    fun noMatchWhenDocTypeDiffers() {
        val pd = json.parseToJsonElement("""{
            "id": "request-1",
            "input_descriptors": [{
                "id": "age-verify",
                "format": { "mso_mdoc": {} },
                "constraints": {
                    "fields": [
                        { "path": ["${'$'}.doctype"], "filter": { "const": "org.iso.18013.5.1.mVR" } }
                    ]
                }
            }]
        }""") as JsonObject

        val results = matcher.matchMDoc(pd, listOf(mdlMdoc))
        assertThat(results).isEmpty()
    }

    @Test
    fun skipDescriptorWithSdJwtFormatOnly() {
        val pd = json.parseToJsonElement("""{
            "id": "pd-1",
            "input_descriptors": [{
                "id": "id-1",
                "format": { "vc+sd-jwt": {} },
                "constraints": {
                    "fields": [
                        { "path": ["${'$'}.vct"], "filter": { "const": "IdentityCredential" } }
                    ]
                }
            }]
        }""") as JsonObject

        val results = matcher.matchMDoc(pd, listOf(mdlMdoc))
        assertThat(results).isEmpty()
    }

    @Test
    fun matchAllReturnsBothSdJwtAndMDocResults() {
        val pd = json.parseToJsonElement("""{
            "id": "combined-request",
            "input_descriptors": [
                {
                    "id": "id-cred",
                    "format": { "vc+sd-jwt": {} },
                    "constraints": {
                        "fields": [
                            { "path": ["${'$'}.vct"], "filter": { "const": "IdentityCredential" } },
                            { "path": ["${'$'}.name"] }
                        ]
                    }
                },
                {
                    "id": "mdl-cred",
                    "format": { "mso_mdoc": {} },
                    "constraints": {
                        "fields": [
                            { "path": ["${'$'}.doctype"], "filter": { "const": "org.iso.18013.5.1.mDL" } },
                            { "path": ["${'$'}['org.iso.18013.5.1']['family_name']"] }
                        ]
                    }
                }
            ]
        }""") as JsonObject

        val results = matcher.matchAll(pd, listOf(sdJwtVc), listOf(mdlMdoc))
        assertThat(results).hasSize(2)
        val sdJwtResult = results.first { it.credentialRef is CredentialRef.SdJwt }
        val mdocResult = results.first { it.credentialRef is CredentialRef.MDoc }
        assertThat(sdJwtResult.descriptorId).isEqualTo("id-cred")
        assertThat(mdocResult.descriptorId).isEqualTo("mdl-cred")
    }

    @Test
    fun matchMDocWithOptionalClaims() {
        val pd = json.parseToJsonElement("""{
            "id": "mdl-request",
            "input_descriptors": [{
                "id": "mdl",
                "format": { "mso_mdoc": {} },
                "constraints": {
                    "fields": [
                        { "path": ["${'$'}.doctype"], "filter": { "const": "org.iso.18013.5.1.mDL" } },
                        { "path": ["${'$'}['org.iso.18013.5.1']['family_name']"] },
                        { "path": ["${'$'}['org.iso.18013.5.1']['birth_date']"], "optional": true }
                    ]
                }
            }]
        }""") as JsonObject

        val results = matcher.matchMDoc(pd, listOf(mdlMdoc))
        assertThat(results).hasSize(1)
        assertThat(results[0].requiredClaims).containsExactly("family_name")
        assertThat(results[0].optionalClaims).containsExactly("birth_date")
    }

    @Test
    fun matchMDocNoFormatSpecified() {
        // When no format is specified, mdocs can still match
        val pd = json.parseToJsonElement("""{
            "id": "request",
            "input_descriptors": [{
                "id": "mdl",
                "constraints": {
                    "fields": [
                        { "path": ["${'$'}.doctype"], "filter": { "const": "org.iso.18013.5.1.mDL" } },
                        { "path": ["${'$'}['org.iso.18013.5.1']['given_name']"] }
                    ]
                }
            }]
        }""") as JsonObject

        val results = matcher.matchMDoc(pd, listOf(mdlMdoc))
        assertThat(results).hasSize(1)
    }

    @Test
    fun matchMDocSkipsElementsNotInNameSpaces() {
        val pd = json.parseToJsonElement("""{
            "id": "request",
            "input_descriptors": [{
                "id": "mdl",
                "format": { "mso_mdoc": {} },
                "constraints": {
                    "fields": [
                        { "path": ["${'$'}.doctype"], "filter": { "const": "org.iso.18013.5.1.mDL" } },
                        { "path": ["${'$'}['org.iso.18013.5.1']['portrait']"] }
                    ]
                }
            }]
        }""") as JsonObject

        val results = matcher.matchMDoc(pd, listOf(mdlMdoc))
        // portrait is not in nameSpaces, so no match (required field missing)
        assertThat(results).isEmpty()
    }
}
