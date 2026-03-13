package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
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
    private lateinit var handler: OpenId4VpHandler

    private val presentationDefinitionJson = """{"id":"req-1","input_descriptors":[{"id":"emp-cred","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"EmployeeCredential"}},{"path":["$.name"]},{"path":["$.department"]}]}}]}"""

    private val testStoredVc = StoredSdJwtVc(
        id = "vc-1",
        compact = "eyJhbGciOiJFZERTQSJ9.eyJzdWIiOiJ1c2VyMSJ9~WyJzYWx0MSIsIm5hbWUiLCJBbGljZSJd~WyJzYWx0MiIsImRlcGFydG1lbnQiLCJFbmdpbmVlcmluZyJd~",
        issuer = "https://issuer.example.com",
        subject = "user1",
        type = "EmployeeCredential",
        claims = mapOf("name" to "Alice", "department" to "Engineering"),
        disclosableClaims = listOf("name", "department"),
        issuedAt = 1700000000L
    )

    private val testMatchResult = MatchResult(
        descriptorId = "emp-cred",
        credentialId = "vc-1",
        credentialType = "EmployeeCredential",
        availableClaims = mapOf(
            "name" to ClaimInfo("name", required = true, available = true),
            "department" to ClaimInfo("department", required = true, available = true)
        ),
        source = CredentialSource.SD_JWT_VC
    )

    private val testQuery = CredentialQuery(
        descriptors = listOf(
            CredentialQueryDescriptor(
                id = "emp-cred",
                format = "vc+sd-jwt",
                vctFilter = "EmployeeCredential",
                requiredClaims = listOf("name", "department"),
                optionalClaims = emptyList()
            )
        )
    )

    @Before
    fun setUp() {
        transport = mockk(relaxed = true)
        peMatcher = mockk()
        dcqlMatcher = mockk()
        handler = OpenId4VpHandler(transport, peMatcher, dcqlMatcher)
    }

    @Test
    fun `processRequest parses and matches PE credential`() {
        every { peMatcher.match(any(), any()) } returns listOf(testMatchResult)
        every { peMatcher.toCredentialQuery(any()) } returns testQuery

        val uri = "openid4vp://?client_id=https://verifier.example.com" +
            "&response_type=vp_token" +
            "&response_mode=direct_post" +
            "&response_uri=https://verifier.example.com/response" +
            "&nonce=test-nonce" +
            "&state=state-1" +
            "&presentation_definition=$presentationDefinitionJson"

        val result = handler.processRequest(uri, listOf(testStoredVc))
        assertThat(result.isSuccess).isTrue()

        val processed = result.getOrThrow()
        assertThat(processed.authRequest.clientId).isEqualTo("https://verifier.example.com")
        assertThat(processed.matchResults).hasSize(1)
        assertThat(processed.matchResults[0].descriptorId).isEqualTo("emp-cred")
        assertThat(processed.query.descriptors).hasSize(1)

        // Transport should NOT be called for by-value request
        verify(exactly = 0) { transport.fetchRequestObject(any()) }
    }

    @Test
    fun `processRequest fetches by-reference request`() {
        val fetchedRequest = AuthorizationRequest(
            clientId = "https://verifier.example.com",
            responseType = "vp_token",
            responseMode = "direct_post",
            responseUri = "https://verifier.example.com/response",
            nonce = "fetched-nonce",
            state = "state-2",
            presentationDefinition = presentationDefinitionJson
        )
        every { transport.fetchRequestObject("https://verifier.example.com/request/abc") } returns
            Result.success(fetchedRequest)
        every { peMatcher.match(any(), any()) } returns listOf(testMatchResult)
        every { peMatcher.toCredentialQuery(any()) } returns testQuery

        val uri = "openid4vp://?client_id=https://verifier.example.com" +
            "&request_uri=https://verifier.example.com/request/abc"

        val result = handler.processRequest(uri, listOf(testStoredVc))
        assertThat(result.isSuccess).isTrue()

        val processed = result.getOrThrow()
        assertThat(processed.authRequest.nonce).isEqualTo("fetched-nonce")

        verify(exactly = 1) { transport.fetchRequestObject("https://verifier.example.com/request/abc") }
    }

    @Test
    fun `processRequest posts error when no credentials match`() {
        every { peMatcher.match(any(), any()) } returns emptyList()
        every { peMatcher.toCredentialQuery(any()) } returns testQuery
        every { transport.postError(any(), any(), any()) } returns Result.success(Unit)

        val uri = "openid4vp://?client_id=https://verifier.example.com" +
            "&response_type=vp_token" +
            "&response_mode=direct_post" +
            "&response_uri=https://verifier.example.com/response" +
            "&nonce=test-nonce" +
            "&state=state-3" +
            "&presentation_definition=$presentationDefinitionJson"

        val result = handler.processRequest(uri, listOf(testStoredVc))
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(NoMatchingCredentialsException::class.java)

        verify { transport.postError("https://verifier.example.com/response", "no_credentials_available", "state-3") }
    }

    @Test
    fun `submitPresentation builds and posts VP token`() {
        every { transport.postVpResponse(any(), any(), any(), any()) } returns Result.success(Unit)

        val authRequest = AuthorizationRequest(
            clientId = "https://verifier.example.com",
            responseType = "vp_token",
            responseMode = "direct_post",
            responseUri = "https://verifier.example.com/response",
            nonce = "submit-nonce",
            state = "state-4",
            presentationDefinition = presentationDefinitionJson
        )

        val signer: (ByteArray) -> ByteArray = { it }

        val result = handler.submitPresentation(
            authRequest = authRequest,
            matchResult = testMatchResult,
            storedVc = testStoredVc,
            selectedClaims = setOf("name", "department"),
            algorithm = "EdDSA",
            signer = signer
        )
        assertThat(result.isSuccess).isTrue()

        verify {
            transport.postVpResponse(
                responseUri = "https://verifier.example.com/response",
                vpToken = any(),
                presentationSubmission = match { it != null && it.definitionId == "req-1" },
                state = "state-4"
            )
        }
    }

    @Test
    fun `declineRequest posts access_denied`() {
        every { transport.postError(any(), any(), any()) } returns Result.success(Unit)

        val authRequest = AuthorizationRequest(
            clientId = "https://verifier.example.com",
            responseUri = "https://verifier.example.com/response",
            state = "state-5"
        )

        val result = handler.declineRequest(authRequest)
        assertThat(result.isSuccess).isTrue()

        verify { transport.postError("https://verifier.example.com/response", "access_denied", "state-5") }
    }
}
