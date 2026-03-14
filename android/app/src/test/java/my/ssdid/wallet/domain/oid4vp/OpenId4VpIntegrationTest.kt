package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import my.ssdid.wallet.domain.sdjwt.SdJwtIssuer
import my.ssdid.wallet.domain.sdjwt.SdJwtParser
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLEncoder
import java.util.Base64

/**
 * End-to-end integration tests for the OpenID4VP flow.
 *
 * Uses real domain components (SdJwtIssuer, SdJwtParser, PresentationDefinitionMatcher,
 * DcqlMatcher, VpTokenBuilder) and only mocks the HTTP transport via Mockk.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenId4VpIntegrationTest {

    private lateinit var transport: OpenId4VpTransport
    private lateinit var handler: OpenId4VpHandler

    private val testSigner: (ByteArray) -> ByteArray = { "test-sig".toByteArray() }
    private val testAlgorithm = "EdDSA"
    private val verifierResponseUri = "https://verifier.example.com/response"

    private val issuer = SdJwtIssuer(
        signer = testSigner,
        algorithm = testAlgorithm
    )

    // Slots to capture transport calls
    private val vpTokenSlot = slot<String>()
    private val submissionSlot = slot<PresentationSubmission?>()
    private val stateSlot = slot<String?>()
    private val errorSlot = slot<String>()
    private val errorStateSlot = slot<String?>()

    @Before
    fun setUp() {
        transport = mockk()
        handler = OpenId4VpHandler(
            transport = transport,
            peMatcher = PresentationDefinitionMatcher(),
            dcqlMatcher = DcqlMatcher()
        )
    }

    // -- Helpers --

    private fun issueVerifiedEmployeeVc(): StoredSdJwtVc {
        val claims = mapOf(
            "name" to JsonPrimitive("Alice Smith"),
            "department" to JsonPrimitive("Engineering"),
            "employeeId" to JsonPrimitive("EMP-12345")
        )
        val disclosable = setOf("name", "department", "employeeId")

        val sdJwtVc = issuer.issue(
            issuer = "did:ssdid:issuer123",
            subject = "did:ssdid:holder456",
            type = listOf("VerifiableCredential", "VerifiedEmployee"),
            claims = claims,
            disclosable = disclosable,
            issuedAt = 1700000000L
        )

        val compact = sdJwtVc.present(sdJwtVc.disclosures)

        return StoredSdJwtVc(
            id = "vc-001",
            compact = compact,
            issuer = "did:ssdid:issuer123",
            subject = "did:ssdid:holder456",
            type = "VerifiedEmployee",
            claims = mapOf(
                "name" to "Alice Smith",
                "department" to "Engineering",
                "employeeId" to "EMP-12345"
            ),
            disclosableClaims = listOf("name", "department", "employeeId"),
            issuedAt = 1700000000L
        )
    }

    private fun buildPeAuthUri(vctFilter: String = "VerifiedEmployee"): String {
        val pd = """
        {
            "id": "verified-employee-pd",
            "input_descriptors": [{
                "id": "employee_desc",
                "format": {"vc+sd-jwt": {}},
                "constraints": {
                    "fields": [
                        {"path": ["$.vct"], "filter": {"type": "string", "const": "$vctFilter"}},
                        {"path": ["$.name"]},
                        {"path": ["$.department"]},
                        {"path": ["$.employeeId"], "optional": true}
                    ]
                }
            }]
        }
        """.trimIndent()

        val encodedPd = URLEncoder.encode(pd, "UTF-8")
        val encodedResponseUri = URLEncoder.encode(verifierResponseUri, "UTF-8")

        return "openid4vp://authorize?" +
            "client_id=${URLEncoder.encode("https://verifier.example.com", "UTF-8")}" +
            "&response_type=vp_token" +
            "&response_mode=direct_post" +
            "&response_uri=$encodedResponseUri" +
            "&nonce=test-nonce-123" +
            "&state=test-state-456" +
            "&presentation_definition=$encodedPd"
    }

    private fun buildDcqlAuthUri(vctFilter: String = "VerifiedEmployee"): String {
        val dcql = """
        {
            "credentials": [{
                "id": "employee_cred",
                "format": "vc+sd-jwt",
                "meta": {"vct_values": ["$vctFilter"]},
                "claims": [
                    {"path": ["name"]},
                    {"path": ["department"]},
                    {"path": ["employeeId"], "optional": true}
                ]
            }]
        }
        """.trimIndent()

        val encodedDcql = URLEncoder.encode(dcql, "UTF-8")
        val encodedResponseUri = URLEncoder.encode(verifierResponseUri, "UTF-8")

        return "openid4vp://authorize?" +
            "client_id=${URLEncoder.encode("https://verifier.example.com", "UTF-8")}" +
            "&response_type=vp_token" +
            "&response_mode=direct_post" +
            "&response_uri=$encodedResponseUri" +
            "&nonce=test-nonce-123" +
            "&state=test-state-456" +
            "&dcql_query=$encodedDcql"
    }

    private fun decodeBase64Url(s: String): String {
        return String(Base64.getUrlDecoder().decode(s), Charsets.UTF_8)
    }

    private fun stubTransportPostVpResponse() {
        every {
            transport.postVpResponse(
                responseUri = any(),
                vpToken = capture(vpTokenSlot),
                presentationSubmission = captureNullable(submissionSlot),
                state = captureNullable(stateSlot)
            )
        } returns Result.success(Unit)
    }

    private fun stubTransportPostError() {
        every {
            transport.postError(
                responseUri = any(),
                error = capture(errorSlot),
                state = captureNullable(errorStateSlot)
            )
        } returns Result.success(Unit)
    }

    // -- Test 1: Full PE 2.0 flow --

    @Test
    fun fullPresentationExchangeFlow() {
        val storedVc = issueVerifiedEmployeeVc()
        val uri = buildPeAuthUri()

        stubTransportPostVpResponse()

        // Step 1: Process the authorization request
        val processResult = handler.processRequest(uri, listOf(storedVc))
        assertThat(processResult.isSuccess).isTrue()

        val processed = processResult.getOrThrow()
        assertThat(processed.matchResults).hasSize(1)

        val match = processed.matchResults[0]
        assertThat(match.descriptorId).isEqualTo("employee_desc")
        assertThat(match.credentialId).isEqualTo("vc-001")
        assertThat(match.credentialType).isEqualTo("VerifiedEmployee")
        assertThat(match.availableClaims).containsKey("name")
        assertThat(match.availableClaims).containsKey("department")
        assertThat(match.availableClaims["name"]!!.required).isTrue()
        assertThat(match.availableClaims["employeeId"]!!.required).isFalse()

        // Step 2: Submit with selected claims (name and department only)
        val selectedClaims = setOf("name", "department")
        val submitResult = handler.submitPresentation(
            authRequest = processed.authRequest,
            matchResult = match,
            storedVc = storedVc,
            selectedClaims = selectedClaims,
            algorithm = testAlgorithm,
            signer = testSigner
        )
        assertThat(submitResult.isSuccess).isTrue()

        // Step 3: Verify VP token was posted with correct parameters
        assertThat(vpTokenSlot.isCaptured).isTrue()
        assertThat(stateSlot.captured).isEqualTo("test-state-456")
        assertThat(submissionSlot.captured).isNotNull()
        assertThat(submissionSlot.captured!!.definitionId).isEqualTo("verified-employee-pd")
        assertThat(submissionSlot.captured!!.descriptorMap[0].id).isEqualTo("employee_desc")
        assertThat(submissionSlot.captured!!.descriptorMap[0].format).isEqualTo("vc+sd-jwt")

        // Step 4: Parse the VP token and verify structure
        val vpToken = vpTokenSlot.captured
        val parsedVp = SdJwtParser.parse(vpToken)

        // Should have exactly 2 disclosures (name, department) — not employeeId
        assertThat(parsedVp.disclosures).hasSize(2)
        val disclosedNames = parsedVp.disclosures.map { it.claimName }.toSet()
        assertThat(disclosedNames).containsExactly("name", "department")

        // Should have a KB-JWT
        assertThat(parsedVp.keyBindingJwt).isNotNull()

        // Verify KB-JWT header
        val kbParts = parsedVp.keyBindingJwt!!.split(".")
        assertThat(kbParts).hasSize(3)

        val kbHeader = Json.parseToJsonElement(decodeBase64Url(kbParts[0])).jsonObject
        assertThat(kbHeader["typ"]?.jsonPrimitive?.content).isEqualTo("kb+jwt")
        assertThat(kbHeader["alg"]?.jsonPrimitive?.content).isEqualTo("EdDSA")

        // Verify KB-JWT payload
        val kbPayload = Json.parseToJsonElement(decodeBase64Url(kbParts[1])).jsonObject
        assertThat(kbPayload["aud"]?.jsonPrimitive?.content).isEqualTo("https://verifier.example.com")
        assertThat(kbPayload["nonce"]?.jsonPrimitive?.content).isEqualTo("test-nonce-123")
        assertThat(kbPayload["sd_hash"]).isNotNull()
    }

    // -- Test 2: Full DCQL flow --

    @Test
    fun fullDcqlFlow() {
        val storedVc = issueVerifiedEmployeeVc()
        val uri = buildDcqlAuthUri()

        stubTransportPostVpResponse()

        // Step 1: Process request
        val processResult = handler.processRequest(uri, listOf(storedVc))
        assertThat(processResult.isSuccess).isTrue()

        val processed = processResult.getOrThrow()
        assertThat(processed.matchResults).hasSize(1)

        val match = processed.matchResults[0]
        assertThat(match.descriptorId).isEqualTo("employee_cred")
        assertThat(match.credentialType).isEqualTo("VerifiedEmployee")

        // Step 2: Submit with all claims
        val selectedClaims = setOf("name", "department", "employeeId")
        val submitResult = handler.submitPresentation(
            authRequest = processed.authRequest,
            matchResult = match,
            storedVc = storedVc,
            selectedClaims = selectedClaims,
            algorithm = testAlgorithm,
            signer = testSigner
        )
        assertThat(submitResult.isSuccess).isTrue()

        // Step 3: Verify VP token
        assertThat(vpTokenSlot.isCaptured).isTrue()
        val vpToken = vpTokenSlot.captured
        val parsedVp = SdJwtParser.parse(vpToken)

        // All 3 disclosures should be present
        assertThat(parsedVp.disclosures).hasSize(3)
        val disclosedNames = parsedVp.disclosures.map { it.claimName }.toSet()
        assertThat(disclosedNames).containsExactly("name", "department", "employeeId")

        // KB-JWT present with correct claims
        assertThat(parsedVp.keyBindingJwt).isNotNull()
        val kbParts = parsedVp.keyBindingJwt!!.split(".")
        val kbPayload = Json.parseToJsonElement(decodeBase64Url(kbParts[1])).jsonObject
        assertThat(kbPayload["aud"]?.jsonPrimitive?.content).isEqualTo("https://verifier.example.com")
        assertThat(kbPayload["nonce"]?.jsonPrimitive?.content).isEqualTo("test-nonce-123")

        // DCQL flow should not include presentation_submission
        assertThat(submissionSlot.captured).isNull()
    }

    // -- Test 3: Decline flow --

    @Test
    fun declineRequestPostsAccessDeniedError() {
        val storedVc = issueVerifiedEmployeeVc()
        val uri = buildPeAuthUri()

        stubTransportPostError()

        // Parse the request first (no transport needed for parsing)
        val processed = handler.processRequest(uri, listOf(storedVc)).getOrThrow()

        // Decline it
        val declineResult = handler.declineRequest(processed.authRequest)
        assertThat(declineResult.isSuccess).isTrue()

        // Verify error=access_denied was posted
        assertThat(errorSlot.captured).isEqualTo("access_denied")
        assertThat(errorStateSlot.captured).isEqualTo("test-state-456")
    }

    // -- Test 4: No match flow --

    @Test
    fun noMatchingCredentialsPostsErrorAndFails() {
        // Request a type that doesn't match our stored VC
        val uri = buildPeAuthUri(vctFilter = "UniversityDegree")
        val storedVc = issueVerifiedEmployeeVc()

        stubTransportPostError()

        val result = handler.processRequest(uri, listOf(storedVc))
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(NoMatchingCredentialsException::class.java)

        // Verify the no_credentials_available error was posted
        assertThat(errorSlot.captured).isEqualTo("no_credentials_available")
        assertThat(errorStateSlot.captured).isEqualTo("test-state-456")
    }
}
