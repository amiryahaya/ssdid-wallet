package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.mdoc.StoredMDoc
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import my.ssdid.wallet.domain.vault.Vault
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenId4VpHandlerMDocTest {

    private lateinit var transport: OpenId4VpTransport
    private lateinit var peMatcher: PresentationDefinitionMatcher
    private lateinit var dcqlMatcher: DcqlMatcher
    private lateinit var vault: Vault
    private lateinit var handler: OpenId4VpHandler

    private val testSdJwt = StoredSdJwtVc(
        id = "vc-1",
        compact = "eyJ.eyJ.sig~disc1~",
        issuer = "did:ssdid:issuer1",
        subject = "did:ssdid:holder1",
        type = "IdentityCredential",
        claims = mapOf("name" to "Ahmad"),
        disclosableClaims = listOf("name"),
        issuedAt = 1700000000L
    )

    private val testMDoc = StoredMDoc(
        id = "mdoc-1",
        docType = "org.iso.18013.5.1.mDL",
        issuerSignedCbor = byteArrayOf(0x01, 0x02),
        deviceKeyId = "key-1",
        issuedAt = 1700000000L,
        nameSpaces = mapOf(
            "org.iso.18013.5.1" to listOf("family_name", "given_name", "birth_date")
        )
    )

    @Before
    fun setup() {
        transport = mockk(relaxed = true)
        peMatcher = PresentationDefinitionMatcher()
        dcqlMatcher = DcqlMatcher()
        vault = mockk()
        handler = OpenId4VpHandler(transport, peMatcher, dcqlMatcher, vault)
    }

    @Test
    fun processRequestLoadsAndMatchesMDocs() = runTest {
        val pd = """{"id":"pd-1","input_descriptors":[{"id":"mdoc-desc-1","format":{"mso_mdoc":{}},"constraints":{"fields":[{"path":["$.doctype"],"filter":{"const":"org.iso.18013.5.1.mDL"}},{"path":["${'$'}['org.iso.18013.5.1']['family_name']"]}]}}]}"""
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n-1&response_mode=direct_post&presentation_definition=${java.net.URLEncoder.encode(pd, "UTF-8")}"

        coEvery { vault.listStoredSdJwtVcs() } returns emptyList()
        coEvery { vault.listMDocs() } returns listOf(testMDoc)

        val result = handler.processRequest(uri)
        assertThat(result.isSuccess).isTrue()
        val review = result.getOrThrow()
        assertThat(review.matches).hasSize(1)
        assertThat(review.matches[0].credentialRef).isInstanceOf(CredentialRef.MDoc::class.java)
        assertThat(review.matches[0].descriptorId).isEqualTo("mdoc-desc-1")

        coVerify { vault.listMDocs() }
    }

    @Test
    fun submitPresentationWithMDocUsesDeviceResponse() = runTest {
        val mdocMatch = MatchResult(
            credentialRef = CredentialRef.MDoc(testMDoc),
            descriptorId = "mdoc-desc-1",
            requiredClaims = listOf("family_name"),
            optionalClaims = emptyList()
        )

        val authRequest = AuthorizationRequest(
            clientId = "https://v.example.com",
            responseUri = "https://v.example.com/cb",
            nonce = "n-1",
            responseMode = "direct_post",
            presentationDefinition = null,
            dcqlQuery = kotlinx.serialization.json.buildJsonObject {
                put("credentials", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("id", kotlinx.serialization.json.JsonPrimitive("mdoc-desc-1"))
                    })
                })
            },
            state = null,
            requestUri = null
        )

        mockkObject(MDocVpTokenBuilder)
        every {
            MDocVpTokenBuilder.build(
                storedMDoc = testMDoc,
                requestedElements = any(),
                clientId = "https://v.example.com",
                responseUri = "https://v.example.com/cb",
                nonce = "n-1",
                signer = any()
            )
        } returns "mock-device-response-base64"

        val signer: (ByteArray) -> ByteArray = { it }
        val result = handler.submitPresentation(
            authRequest = authRequest,
            matchResult = mdocMatch,
            selectedClaims = listOf("org.iso.18013.5.1/family_name"),
            algorithm = "ES256",
            signer = signer
        )

        assertThat(result.isSuccess).isTrue()
        verify {
            MDocVpTokenBuilder.build(
                storedMDoc = testMDoc,
                requestedElements = mapOf("org.iso.18013.5.1" to listOf("family_name")),
                clientId = "https://v.example.com",
                responseUri = "https://v.example.com/cb",
                nonce = "n-1",
                signer = any()
            )
        }
        verify {
            transport.postVpResponse(
                "https://v.example.com/cb",
                "mock-device-response-base64",
                any(),
                null
            )
        }

        unmockkObject(MDocVpTokenBuilder)
    }

    @Test
    fun processRequestReturnsMatchesFromBothFormats() = runTest {
        // PD with both sd-jwt and mso_mdoc input descriptors
        val pd = """{"id":"pd-dual","input_descriptors":[{"id":"sd-jwt-desc","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"IdentityCredential"}}]}},{"id":"mdoc-desc","format":{"mso_mdoc":{}},"constraints":{"fields":[{"path":["$.doctype"],"filter":{"const":"org.iso.18013.5.1.mDL"}},{"path":["${'$'}['org.iso.18013.5.1']['family_name']"]}]}}]}"""
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n-1&response_mode=direct_post&presentation_definition=${java.net.URLEncoder.encode(pd, "UTF-8")}"

        coEvery { vault.listStoredSdJwtVcs() } returns listOf(testSdJwt)
        coEvery { vault.listMDocs() } returns listOf(testMDoc)

        val result = handler.processRequest(uri)
        assertThat(result.isSuccess).isTrue()
        val review = result.getOrThrow()
        assertThat(review.matches).hasSize(2)

        val sdJwtMatch = review.matches.first { it.credentialRef is CredentialRef.SdJwt }
        val mdocMatch = review.matches.first { it.credentialRef is CredentialRef.MDoc }

        assertThat(sdJwtMatch.descriptorId).isEqualTo("sd-jwt-desc")
        assertThat(mdocMatch.descriptorId).isEqualTo("mdoc-desc")
    }
}
