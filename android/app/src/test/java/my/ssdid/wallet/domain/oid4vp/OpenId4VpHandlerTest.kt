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
class OpenId4VpHandlerTest {

    private lateinit var transport: OpenId4VpTransport
    private lateinit var peMatcher: PresentationDefinitionMatcher
    private lateinit var dcqlMatcher: DcqlMatcher
    private lateinit var vault: Vault
    private lateinit var handler: OpenId4VpHandler

    private val testCredential = StoredSdJwtVc(
        id = "vc-1",
        compact = "eyJ.eyJ.sig~disc1~",
        issuer = "did:ssdid:issuer1",
        subject = "did:ssdid:holder1",
        type = "IdentityCredential",
        claims = mapOf("name" to "Ahmad"),
        disclosableClaims = listOf("name"),
        issuedAt = 1700000000L
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
    fun processRequestByValueWithPd() = runTest {
        val pd = """{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"IdentityCredential"}}]}}]}"""
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n-1&response_mode=direct_post&presentation_definition=${java.net.URLEncoder.encode(pd, "UTF-8")}"

        coEvery { vault.listStoredSdJwtVcs() } returns listOf(testCredential)
        coEvery { vault.listMDocs() } returns emptyList()

        val result = handler.processRequest(uri)
        assertThat(result.isSuccess).isTrue()
        val review = result.getOrThrow()
        assertThat(review.authRequest.clientId).isEqualTo("https://v.example.com")
        assertThat(review.matches).hasSize(1)
    }

    @Test
    fun processRequestByReference() = runTest {
        val requestJson = """{"client_id":"https://v.example.com","response_uri":"https://v.example.com/cb","nonce":"n-1","response_mode":"direct_post","presentation_definition":{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"IdentityCredential"}}]}}]}}"""
        every { transport.fetchRequestObject("https://v.example.com/request/123") } returns requestJson
        coEvery { vault.listStoredSdJwtVcs() } returns listOf(testCredential)
        coEvery { vault.listMDocs() } returns emptyList()

        val uri = "openid4vp://?client_id=https://v.example.com&request_uri=https://v.example.com/request/123"
        val result = handler.processRequest(uri)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun processRequestNoMatchPostsError() = runTest {
        val pd = """{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"DriverLicense"}}]}}]}"""
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n-1&response_mode=direct_post&presentation_definition=${java.net.URLEncoder.encode(pd, "UTF-8")}"
        coEvery { vault.listStoredSdJwtVcs() } returns listOf(testCredential)
        coEvery { vault.listMDocs() } returns emptyList()

        val result = handler.processRequest(uri)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(NoMatchingCredentialsException::class.java)
        verify { transport.postError("https://v.example.com/cb", "access_denied", null) }
    }
}
