# Phase 2a: OpenID4VP Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add OpenID4VP 1.0 support to the SSDID Wallet on both Android and iOS, enabling standard credential presentation to any verifier.

**Architecture:** Layered protocol engine — Transport (HTTP), Protocol (Authorization Request parse/validate), Query (PE 2.0 + DCQL credential matching), and UI (extended ConsentScreen). Each layer is independently testable. VP Token built using existing SD-JWT VC + KB-JWT infrastructure from Phase 1.

**Tech Stack:** Kotlin + Jetpack Compose (Android), Swift + SwiftUI (iOS), kotlinx-serialization, Retrofit/OkHttp (Android), URLSession (iOS), existing SD-JWT VC stack from Phase 1.

**Design doc:** `docs/plans/2026-03-14-phase2a-openid4vp-design.md`

---

## Task Group 1: Shared Test Vectors

### Task 1: Authorization Request Test Vectors

**Files:**
- Create: `test-vectors/oid4vp/authorization-request-by-value.json`
- Create: `test-vectors/oid4vp/authorization-request-by-reference.json`
- Create: `test-vectors/oid4vp/authorization-request-invalid.json`

**Step 1: Create by-value test vector**

```json
{
  "description": "Valid authorization request with presentation_definition by value",
  "input": {
    "uri": "openid4vp://?response_type=vp_token&client_id=https://verifier.example.com&nonce=n-0S6_WzA2Mj&response_mode=direct_post&response_uri=https://verifier.example.com/response&state=af0ifjsldkj&presentation_definition=%7B%22id%22%3A%22request-1%22%2C%22input_descriptors%22%3A%5B%7B%22id%22%3A%22employee-cred%22%2C%22format%22%3A%7B%22vc%2Bsd-jwt%22%3A%7B%22alg%22%3A%5B%22EdDSA%22%5D%7D%7D%2C%22constraints%22%3A%7B%22fields%22%3A%5B%7B%22path%22%3A%5B%22%24.vct%22%5D%2C%22filter%22%3A%7B%22const%22%3A%22VerifiedEmployee%22%7D%7D%2C%7B%22path%22%3A%5B%22%24.name%22%5D%7D%2C%7B%22path%22%3A%5B%22%24.department%22%5D%2C%22optional%22%3Atrue%7D%5D%7D%7D%5D%7D"
  },
  "expected": {
    "valid": true,
    "response_type": "vp_token",
    "client_id": "https://verifier.example.com",
    "nonce": "n-0S6_WzA2Mj",
    "response_mode": "direct_post",
    "response_uri": "https://verifier.example.com/response",
    "state": "af0ifjsldkj",
    "has_presentation_definition": true,
    "has_dcql_query": false
  }
}
```

**Step 2: Create by-reference test vector**

```json
{
  "description": "Valid authorization request with request_uri reference",
  "input": {
    "uri": "openid4vp://?client_id=https://verifier.example.com&request_uri=https://verifier.example.com/request/abc123"
  },
  "expected": {
    "valid": true,
    "client_id": "https://verifier.example.com",
    "request_uri": "https://verifier.example.com/request/abc123",
    "requires_fetch": true
  }
}
```

**Step 3: Create invalid request test vectors**

```json
{
  "description": "Invalid authorization requests",
  "vectors": [
    {
      "description": "Missing response_type",
      "uri": "openid4vp://?client_id=https://v.example.com&nonce=abc&response_mode=direct_post&response_uri=https://v.example.com/r",
      "expected_error": "missing_response_type"
    },
    {
      "description": "Missing nonce",
      "uri": "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_mode=direct_post&response_uri=https://v.example.com/r",
      "expected_error": "missing_nonce"
    },
    {
      "description": "Missing client_id",
      "uri": "openid4vp://?response_type=vp_token&nonce=abc&response_mode=direct_post&response_uri=https://v.example.com/r",
      "expected_error": "missing_client_id"
    },
    {
      "description": "Non-HTTPS response_uri",
      "uri": "openid4vp://?response_type=vp_token&client_id=https://v.example.com&nonce=abc&response_mode=direct_post&response_uri=http://v.example.com/r",
      "expected_error": "invalid_response_uri"
    },
    {
      "description": "Both presentation_definition and dcql_query present",
      "uri": "openid4vp://?response_type=vp_token&client_id=https://v.example.com&nonce=abc&response_mode=direct_post&response_uri=https://v.example.com/r&presentation_definition=%7B%7D&dcql_query=%7B%7D",
      "expected_error": "ambiguous_query"
    }
  ]
}
```

**Step 4: Commit**

```bash
git add test-vectors/oid4vp/
git commit -m "feat: add OpenID4VP authorization request test vectors"
```

### Task 2: Presentation Exchange & DCQL Test Vectors

**Files:**
- Create: `test-vectors/oid4vp/pe-matching.json`
- Create: `test-vectors/oid4vp/dcql-matching.json`

**Step 1: Create PE 2.0 matching test vectors**

```json
{
  "description": "Presentation Exchange 2.0 credential matching scenarios",
  "vectors": [
    {
      "description": "Single SD-JWT VC matches by vct and format",
      "presentation_definition": {
        "id": "req-1",
        "input_descriptors": [{
          "id": "emp-cred",
          "format": { "vc+sd-jwt": { "alg": ["EdDSA"] } },
          "constraints": {
            "fields": [
              { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
              { "path": ["$.name"] },
              { "path": ["$.department"], "optional": true }
            ]
          }
        }]
      },
      "stored_credentials": [
        {
          "type": "sd-jwt-vc",
          "vct": "VerifiedEmployee",
          "claims": { "name": "Ahmad", "department": "Engineering", "employeeId": "EMP-1234" },
          "disclosable_claims": ["name", "department"]
        }
      ],
      "expected": {
        "matched": true,
        "matched_credential_index": 0,
        "required_claims": ["name"],
        "optional_claims": ["department"]
      }
    },
    {
      "description": "No credential matches vct filter",
      "presentation_definition": {
        "id": "req-2",
        "input_descriptors": [{
          "id": "gov-id",
          "format": { "vc+sd-jwt": {} },
          "constraints": {
            "fields": [
              { "path": ["$.vct"], "filter": { "const": "GovernmentId" } }
            ]
          }
        }]
      },
      "stored_credentials": [
        {
          "type": "sd-jwt-vc",
          "vct": "VerifiedEmployee",
          "claims": { "name": "Ahmad" },
          "disclosable_claims": ["name"]
        }
      ],
      "expected": { "matched": false }
    },
    {
      "description": "Multiple credentials, one matches",
      "presentation_definition": {
        "id": "req-3",
        "input_descriptors": [{
          "id": "emp-cred",
          "format": { "vc+sd-jwt": {} },
          "constraints": {
            "fields": [
              { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
              { "path": ["$.name"] }
            ]
          }
        }]
      },
      "stored_credentials": [
        { "type": "sd-jwt-vc", "vct": "UniversityDegree", "claims": { "degree": "BSc" }, "disclosable_claims": ["degree"] },
        { "type": "sd-jwt-vc", "vct": "VerifiedEmployee", "claims": { "name": "Ahmad" }, "disclosable_claims": ["name"] }
      ],
      "expected": { "matched": true, "matched_credential_index": 1 }
    }
  ]
}
```

**Step 2: Create DCQL matching test vectors**

```json
{
  "description": "DCQL credential matching scenarios",
  "vectors": [
    {
      "description": "Single credential match with optional claim",
      "dcql_query": {
        "credentials": [{
          "id": "emp-cred",
          "format": "vc+sd-jwt",
          "meta": { "vct_values": ["VerifiedEmployee"] },
          "claims": [
            { "path": ["name"] },
            { "path": ["department"], "optional": true }
          ]
        }]
      },
      "stored_credentials": [
        {
          "type": "sd-jwt-vc",
          "vct": "VerifiedEmployee",
          "claims": { "name": "Ahmad", "department": "Engineering" },
          "disclosable_claims": ["name", "department"]
        }
      ],
      "expected": {
        "matched": true,
        "matched_credential_index": 0,
        "required_claims": ["name"],
        "optional_claims": ["department"]
      }
    },
    {
      "description": "No match — required claim not available",
      "dcql_query": {
        "credentials": [{
          "id": "id-cred",
          "format": "vc+sd-jwt",
          "meta": { "vct_values": ["VerifiedEmployee"] },
          "claims": [
            { "path": ["national_id"] }
          ]
        }]
      },
      "stored_credentials": [
        {
          "type": "sd-jwt-vc",
          "vct": "VerifiedEmployee",
          "claims": { "name": "Ahmad" },
          "disclosable_claims": ["name"]
        }
      ],
      "expected": { "matched": false }
    }
  ]
}
```

**Step 3: Commit**

```bash
git add test-vectors/oid4vp/
git commit -m "feat: add PE 2.0 and DCQL matching test vectors"
```

---

## Task Group 2: Models & Authorization Request Parser (Android)

### Task 3: CredentialQuery Model (Android)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/CredentialQuery.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/CredentialQueryTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CredentialQueryTest {

    @Test
    fun `credential query descriptor has required and optional claims`() {
        val descriptor = CredentialQueryDescriptor(
            id = "emp-cred",
            format = "vc+sd-jwt",
            vctFilter = "VerifiedEmployee",
            requiredClaims = listOf("name"),
            optionalClaims = listOf("department")
        )
        assertThat(descriptor.id).isEqualTo("emp-cred")
        assertThat(descriptor.requiredClaims).containsExactly("name")
        assertThat(descriptor.optionalClaims).containsExactly("department")
    }

    @Test
    fun `credential query contains multiple descriptors`() {
        val query = CredentialQuery(
            descriptors = listOf(
                CredentialQueryDescriptor("d1", "vc+sd-jwt", "TypeA", listOf("a"), emptyList()),
                CredentialQueryDescriptor("d2", "vc+sd-jwt", "TypeB", listOf("b"), listOf("c"))
            )
        )
        assertThat(query.descriptors).hasSize(2)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.CredentialQueryTest" -q`
Expected: FAIL — class not found.

**Step 3: Write minimal implementation**

```kotlin
package my.ssdid.wallet.domain.oid4vp

/**
 * Normalized credential query — bridges PE 2.0 and DCQL into a common model.
 */
data class CredentialQuery(
    val descriptors: List<CredentialQueryDescriptor>
)

/**
 * A single credential request within a query.
 */
data class CredentialQueryDescriptor(
    val id: String,
    val format: String,
    val vctFilter: String?,
    val requiredClaims: List<String>,
    val optionalClaims: List<String>
)

/**
 * Result of matching stored credentials against a query.
 */
data class MatchResult(
    val descriptorId: String,
    val credentialId: String,
    val credentialType: String,
    val availableClaims: Map<String, ClaimInfo>,
    val source: CredentialSource
)

data class ClaimInfo(
    val name: String,
    val required: Boolean,
    val available: Boolean
)

enum class CredentialSource {
    SD_JWT_VC,
    IDENTITY
}
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.CredentialQueryTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/CredentialQuery.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/CredentialQueryTest.kt
git commit -m "feat(android): add CredentialQuery normalized model for OpenID4VP"
```

### Task 4: AuthorizationRequest Model & Parser (Android)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/AuthorizationRequest.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/AuthorizationRequestTest.kt`

**Step 1: Write the failing tests**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AuthorizationRequestTest {

    @Test
    fun `parse valid by-value request with presentation_definition`() {
        val uri = "openid4vp://?response_type=vp_token" +
            "&client_id=https://verifier.example.com" +
            "&nonce=n-0S6_WzA2Mj" +
            "&response_mode=direct_post" +
            "&response_uri=https://verifier.example.com/response" +
            "&state=af0ifjsldkj" +
            "&presentation_definition=%7B%22id%22%3A%22req-1%22%2C%22input_descriptors%22%3A%5B%5D%7D"

        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isSuccess).isTrue()
        val req = result.getOrThrow()
        assertThat(req.responseType).isEqualTo("vp_token")
        assertThat(req.clientId).isEqualTo("https://verifier.example.com")
        assertThat(req.nonce).isEqualTo("n-0S6_WzA2Mj")
        assertThat(req.responseMode).isEqualTo("direct_post")
        assertThat(req.responseUri).isEqualTo("https://verifier.example.com/response")
        assertThat(req.state).isEqualTo("af0ifjsldkj")
        assertThat(req.presentationDefinition).isNotNull()
        assertThat(req.dcqlQuery).isNull()
        assertThat(req.requestUri).isNull()
    }

    @Test
    fun `parse by-reference request extracts request_uri`() {
        val uri = "openid4vp://?client_id=https://verifier.example.com" +
            "&request_uri=https://verifier.example.com/request/abc123"

        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isSuccess).isTrue()
        val req = result.getOrThrow()
        assertThat(req.clientId).isEqualTo("https://verifier.example.com")
        assertThat(req.requestUri).isEqualTo("https://verifier.example.com/request/abc123")
    }

    @Test
    fun `parse rejects missing response_type for by-value request`() {
        val uri = "openid4vp://?client_id=https://v.example.com&nonce=abc" +
            "&response_mode=direct_post&response_uri=https://v.example.com/r" +
            "&presentation_definition=%7B%7D"

        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("response_type")
    }

    @Test
    fun `parse rejects missing nonce for by-value request`() {
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com" +
            "&response_mode=direct_post&response_uri=https://v.example.com/r" +
            "&presentation_definition=%7B%7D"

        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("nonce")
    }

    @Test
    fun `parse rejects missing client_id`() {
        val uri = "openid4vp://?response_type=vp_token&nonce=abc" +
            "&response_mode=direct_post&response_uri=https://v.example.com/r" +
            "&presentation_definition=%7B%7D"

        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("client_id")
    }

    @Test
    fun `parse rejects non-HTTPS response_uri`() {
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com" +
            "&nonce=abc&response_mode=direct_post&response_uri=http://v.example.com/r" +
            "&presentation_definition=%7B%7D"

        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("response_uri")
    }

    @Test
    fun `parse rejects both presentation_definition and dcql_query`() {
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com" +
            "&nonce=abc&response_mode=direct_post&response_uri=https://v.example.com/r" +
            "&presentation_definition=%7B%7D&dcql_query=%7B%7D"

        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("ambiguous")
    }

    @Test
    fun `parse accepts DID as client_id`() {
        val uri = "openid4vp://?response_type=vp_token" +
            "&client_id=did:web:verifier.example.com" +
            "&nonce=abc&response_mode=direct_post" +
            "&response_uri=https://verifier.example.com/r" +
            "&presentation_definition=%7B%7D"

        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().clientId).isEqualTo("did:web:verifier.example.com")
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.AuthorizationRequestTest" -q`
Expected: FAIL

**Step 3: Write implementation**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import android.net.Uri

/**
 * OpenID4VP Authorization Request model and parser.
 *
 * Supports by-value (all params in URI) and by-reference (request_uri).
 * Per OpenID4VP 1.0 spec.
 */
data class AuthorizationRequest(
    val clientId: String,
    val responseType: String? = null,
    val responseMode: String? = null,
    val responseUri: String? = null,
    val nonce: String? = null,
    val state: String? = null,
    val presentationDefinition: String? = null,
    val dcqlQuery: String? = null,
    val requestUri: String? = null
) {
    companion object {
        fun parse(uriString: String): Result<AuthorizationRequest> = runCatching {
            val uri = Uri.parse(uriString)

            val clientId = uri.getQueryParameter("client_id")
                ?: throw IllegalArgumentException("Missing required parameter: client_id")

            val requestUri = uri.getQueryParameter("request_uri")

            // By-reference: only client_id and request_uri needed
            if (requestUri != null) {
                return@runCatching AuthorizationRequest(
                    clientId = clientId,
                    requestUri = requestUri
                )
            }

            // By-value: validate all required parameters
            val responseType = uri.getQueryParameter("response_type")
                ?: throw IllegalArgumentException("Missing required parameter: response_type")
            require(responseType == "vp_token") { "Unsupported response_type: $responseType" }

            val nonce = uri.getQueryParameter("nonce")
                ?: throw IllegalArgumentException("Missing required parameter: nonce")

            val responseMode = uri.getQueryParameter("response_mode") ?: "direct_post"
            require(responseMode == "direct_post") { "Unsupported response_mode: $responseMode" }

            val responseUri = uri.getQueryParameter("response_uri")
                ?: throw IllegalArgumentException("Missing required parameter: response_uri")
            require(responseUri.startsWith("https://")) {
                "response_uri must be HTTPS: $responseUri"
            }

            val presentationDefinition = uri.getQueryParameter("presentation_definition")
            val dcqlQuery = uri.getQueryParameter("dcql_query")

            if (presentationDefinition != null && dcqlQuery != null) {
                throw IllegalArgumentException(
                    "Request is ambiguous: both presentation_definition and dcql_query present"
                )
            }

            val state = uri.getQueryParameter("state")

            // Validate client_id is HTTPS URL or DID
            validateClientId(clientId)

            AuthorizationRequest(
                clientId = clientId,
                responseType = responseType,
                responseMode = responseMode,
                responseUri = responseUri,
                nonce = nonce,
                state = state,
                presentationDefinition = presentationDefinition,
                dcqlQuery = dcqlQuery
            )
        }

        private fun validateClientId(clientId: String) {
            val isHttps = clientId.startsWith("https://")
            val isDid = clientId.startsWith("did:")
            require(isHttps || isDid) { "client_id must be HTTPS URL or DID: $clientId" }
        }
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.AuthorizationRequestTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/AuthorizationRequest.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/AuthorizationRequestTest.kt
git commit -m "feat(android): add AuthorizationRequest parser for OpenID4VP"
```

### Task 5: AuthorizationRequest Model & Parser (iOS)

**Files:**
- Create: `ios/SsdidWallet/Domain/Oid4vp/AuthorizationRequest.swift`
- Create: `ios/SsdidWallet/Domain/Oid4vp/CredentialQuery.swift`
- Create: `ios/SsdidWallet/Domain/Oid4vp/OpenId4VpError.swift`
- Create: `ios/SsdidWalletTests/AuthorizationRequestTests.swift`

**Step 1: Write the failing tests**

Mirror the 8 Android tests from Task 4 in Swift XCTest format. Test the same parse behaviors: valid by-value, by-reference, missing response_type, missing nonce, missing client_id, non-HTTPS response_uri, ambiguous query, DID client_id.

**Step 2: Write implementations**

Mirror the Android models (`AuthorizationRequest`, `CredentialQuery`, `CredentialQueryDescriptor`, `MatchResult`, `ClaimInfo`, `CredentialSource`) and parser logic in Swift. Use `URLComponents` for URI parsing instead of `android.net.Uri`. Add `OpenId4VpError` enum for error cases.

**Step 3: Add files to Xcode project**

Update `ios/SsdidWallet.xcodeproj/project.pbxproj` to include new source and test files.

**Step 4: Run tests and verify**

```bash
cd /Users/amirrudinyahaya/Workspace/ssdid-wallet && xcodebuild test -project ios/SsdidWallet.xcodeproj -scheme SsdidWallet -destination 'platform=iOS Simulator,id=FAE6A82B-C907-4FDA-9306-226FFCEA3B26' CODE_SIGNING_ALLOWED=NO -only-testing:SsdidWalletTests/AuthorizationRequestTests
```

**Step 5: Commit**

```bash
git add ios/SsdidWallet/Domain/Oid4vp/ ios/SsdidWalletTests/AuthorizationRequestTests.swift ios/SsdidWallet.xcodeproj/project.pbxproj
git commit -m "feat(ios): add AuthorizationRequest parser for OpenID4VP"
```

---

## Task Group 3: Presentation Exchange 2.0 Matcher

### Task 6: PresentationDefinitionMatcher (Android)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/PresentationDefinitionMatcher.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/PresentationDefinitionMatcherTest.kt`

**Step 1: Write the failing tests**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import org.junit.Test

class PresentationDefinitionMatcherTest {

    private val matcher = PresentationDefinitionMatcher()

    private val employeeVc = StoredSdJwtVc(
        id = "vc-1",
        compact = "eyJ...",
        issuer = "did:ssdid:issuer1",
        subject = "did:ssdid:holder1",
        type = "VerifiedEmployee",
        claims = mapOf("name" to "Ahmad", "department" to "Engineering", "employeeId" to "EMP-1234"),
        disclosableClaims = listOf("name", "department"),
        issuedAt = 1719792000
    )

    @Test
    fun `matches credential by vct and returns required and optional claims`() {
        val pd = """
        {
          "id": "req-1",
          "input_descriptors": [{
            "id": "emp-cred",
            "format": { "vc+sd-jwt": { "alg": ["EdDSA"] } },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
                { "path": ["$.name"] },
                { "path": ["$.department"], "optional": true }
              ]
            }
          }]
        }
        """.trimIndent()

        val results = matcher.match(pd, listOf(employeeVc))
        assertThat(results).hasSize(1)
        assertThat(results[0].descriptorId).isEqualTo("emp-cred")
        assertThat(results[0].credentialId).isEqualTo("vc-1")

        val claims = results[0].availableClaims
        assertThat(claims["name"]?.required).isTrue()
        assertThat(claims["name"]?.available).isTrue()
        assertThat(claims["department"]?.required).isFalse()
        assertThat(claims["department"]?.available).isTrue()
    }

    @Test
    fun `returns empty when no credential matches vct filter`() {
        val pd = """
        {
          "id": "req-2",
          "input_descriptors": [{
            "id": "gov-id",
            "format": { "vc+sd-jwt": {} },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "GovernmentId" } }
              ]
            }
          }]
        }
        """.trimIndent()

        val results = matcher.match(pd, listOf(employeeVc))
        assertThat(results).isEmpty()
    }

    @Test
    fun `returns empty when required claim not available`() {
        val pd = """
        {
          "id": "req-3",
          "input_descriptors": [{
            "id": "id-cred",
            "format": { "vc+sd-jwt": {} },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
                { "path": ["$.national_id"] }
              ]
            }
          }]
        }
        """.trimIndent()

        val results = matcher.match(pd, listOf(employeeVc))
        assertThat(results).isEmpty()
    }

    @Test
    fun `selects correct credential from multiple stored`() {
        val degreeVc = StoredSdJwtVc(
            id = "vc-2", compact = "eyJ...", issuer = "did:ssdid:uni",
            subject = "did:ssdid:holder1", type = "UniversityDegree",
            claims = mapOf("degree" to "BSc"), disclosableClaims = listOf("degree"),
            issuedAt = 1719792000
        )

        val pd = """
        {
          "id": "req-4",
          "input_descriptors": [{
            "id": "emp-cred",
            "format": { "vc+sd-jwt": {} },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
                { "path": ["$.name"] }
              ]
            }
          }]
        }
        """.trimIndent()

        val results = matcher.match(pd, listOf(degreeVc, employeeVc))
        assertThat(results).hasSize(1)
        assertThat(results[0].credentialId).isEqualTo("vc-1")
    }

    @Test
    fun `converts to CredentialQuery`() {
        val pd = """
        {
          "id": "req-1",
          "input_descriptors": [{
            "id": "emp-cred",
            "format": { "vc+sd-jwt": {} },
            "constraints": {
              "fields": [
                { "path": ["$.vct"], "filter": { "const": "VerifiedEmployee" } },
                { "path": ["$.name"] },
                { "path": ["$.department"], "optional": true }
              ]
            }
          }]
        }
        """.trimIndent()

        val query = matcher.toCredentialQuery(pd)
        assertThat(query.descriptors).hasSize(1)
        assertThat(query.descriptors[0].vctFilter).isEqualTo("VerifiedEmployee")
        assertThat(query.descriptors[0].requiredClaims).containsExactly("name")
        assertThat(query.descriptors[0].optionalClaims).containsExactly("department")
    }
}
```

**Step 2: Run tests — expected FAIL**

**Step 3: Write implementation**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import kotlinx.serialization.json.*
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc

/**
 * Matches stored SD-JWT VCs against a Presentation Exchange 2.0
 * Presentation Definition.
 */
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

                // $.vct filter extracts credential type constraint
                if (path == "$.vct" && filter != null) {
                    vctFilter = filter["const"]?.jsonPrimitive?.content
                    continue
                }

                // Extract claim name from JSONPath ($.name -> name)
                val claimName = path.removePrefix("$.")
                if (optional) {
                    optionalClaims.add(claimName)
                } else {
                    requiredClaims.add(claimName)
                }
            }

            CredentialQueryDescriptor(
                id = id,
                format = format,
                vctFilter = vctFilter,
                requiredClaims = requiredClaims,
                optionalClaims = optionalClaims
            )
        } ?: emptyList()

        return CredentialQuery(descriptors)
    }

    private fun matchQuery(
        query: CredentialQuery,
        storedCredentials: List<StoredSdJwtVc>
    ): List<MatchResult> {
        val results = mutableListOf<MatchResult>()

        for (descriptor in query.descriptors) {
            for (credential in storedCredentials) {
                // Check type match
                if (descriptor.vctFilter != null && credential.type != descriptor.vctFilter) continue

                // Check all required claims are available
                val allClaims = credential.claims.keys
                val hasAllRequired = descriptor.requiredClaims.all { it in allClaims }
                if (!hasAllRequired) continue

                // Build claim info map
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
                break // First matching credential per descriptor
            }
        }
        return results
    }
}
```

**Step 4: Run tests — expected PASS**

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/PresentationDefinitionMatcher.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/PresentationDefinitionMatcherTest.kt
git commit -m "feat(android): add Presentation Exchange 2.0 matcher for OpenID4VP"
```

### Task 7: DcqlMatcher (Android)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/DcqlMatcher.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/DcqlMatcherTest.kt`

**Step 1: Write tests** — Mirror the PE matcher tests but with DCQL JSON format. Test: match by vct_values + claims, no match when required claim missing, conversion to CredentialQuery.

**Step 2: Write implementation** — Parse DCQL JSON `{"credentials":[...]}`, extract `meta.vct_values`, `claims[].path` and `claims[].optional`, convert to `CredentialQuery`, reuse same `matchQuery` logic. Extract `matchQuery` to a shared utility or companion.

**Step 3: Run tests — expected PASS**

**Step 4: Commit**

```bash
git commit -m "feat(android): add DCQL matcher for OpenID4VP"
```

### Task 8: PE + DCQL Matchers (iOS)

**Files:**
- Create: `ios/SsdidWallet/Domain/Oid4vp/PresentationDefinitionMatcher.swift`
- Create: `ios/SsdidWallet/Domain/Oid4vp/DcqlMatcher.swift`
- Create: `ios/SsdidWalletTests/PresentationDefinitionMatcherTests.swift`
- Create: `ios/SsdidWalletTests/DcqlMatcherTests.swift`

Mirror Tasks 6 + 7 logic in Swift. Use `JSONSerialization` for parsing. Same test patterns. Add to Xcode project.

**Commit:**

```bash
git commit -m "feat(ios): add PE 2.0 and DCQL matchers for OpenID4VP"
```

---

## Task Group 4: VP Token Builder

### Task 9: VpTokenBuilder (Android)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/VpTokenBuilder.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/PresentationSubmission.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/VpTokenBuilderTest.kt`

**Step 1: Write tests**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import my.ssdid.wallet.domain.sdjwt.*
import kotlinx.serialization.json.*
import org.junit.Test

class VpTokenBuilderTest {

    private val testSigner: (ByteArray) -> ByteArray = { "test-sig".toByteArray() }

    @Test
    fun `builds VP token with selected disclosures and KB-JWT`() {
        val issuer = SdJwtIssuer(signer = testSigner, algorithm = "EdDSA")
        val sdJwt = issuer.issue(
            issuer = "did:ssdid:issuer1",
            subject = "did:ssdid:holder1",
            type = listOf("VerifiableCredential", "VerifiedEmployee"),
            claims = mapOf("name" to JsonPrimitive("Ahmad"), "dept" to JsonPrimitive("Eng")),
            disclosable = setOf("name", "dept"),
            issuedAt = 1719792000
        )

        val builder = VpTokenBuilder()
        val vpToken = builder.build(
            sdJwtVc = sdJwt,
            selectedClaimNames = setOf("name"),
            audience = "https://verifier.example.com",
            nonce = "n-0S6_WzA2Mj",
            algorithm = "EdDSA",
            signer = testSigner
        )

        // VP token should contain issuer JWT + 1 disclosure + KB-JWT
        val parts = vpToken.split("~").filter { it.isNotEmpty() }
        assertThat(parts).hasSize(3) // issuerJwt, disclosure, kbJwt
        assertThat(parts[0]).contains(".") // JWT structure
        assertThat(parts[2]).contains(".") // KB-JWT structure

        // Verify KB-JWT has correct aud and nonce
        val kbPayloadB64 = parts[2].split(".")[1]
        val kbPayload = Json.parseToJsonElement(
            String(java.util.Base64.getUrlDecoder().decode(kbPayloadB64))
        ).jsonObject
        assertThat(kbPayload["aud"]?.jsonPrimitive?.content).isEqualTo("https://verifier.example.com")
        assertThat(kbPayload["nonce"]?.jsonPrimitive?.content).isEqualTo("n-0S6_WzA2Mj")
    }

    @Test
    fun `builds presentation submission for PE response`() {
        val builder = VpTokenBuilder()
        val submission = builder.buildPresentationSubmission(
            definitionId = "req-1",
            descriptorId = "emp-cred"
        )

        assertThat(submission.definitionId).isEqualTo("req-1")
        assertThat(submission.descriptorMap).hasSize(1)
        assertThat(submission.descriptorMap[0].id).isEqualTo("emp-cred")
        assertThat(submission.descriptorMap[0].format).isEqualTo("vc+sd-jwt")
        assertThat(submission.descriptorMap[0].path).isEqualTo("$")
    }

    @Test
    fun `selects only matching disclosures by claim name`() {
        val issuer = SdJwtIssuer(signer = testSigner, algorithm = "EdDSA")
        val sdJwt = issuer.issue(
            issuer = "did:ssdid:issuer1",
            subject = "did:ssdid:holder1",
            type = listOf("VerifiableCredential"),
            claims = mapOf(
                "a" to JsonPrimitive("1"),
                "b" to JsonPrimitive("2"),
                "c" to JsonPrimitive("3")
            ),
            disclosable = setOf("a", "b", "c")
        )

        val builder = VpTokenBuilder()
        val vpToken = builder.build(
            sdJwtVc = sdJwt,
            selectedClaimNames = setOf("a", "c"),
            audience = "https://v.example.com",
            nonce = "abc",
            algorithm = "EdDSA",
            signer = testSigner
        )

        val parts = vpToken.split("~").filter { it.isNotEmpty() }
        // issuerJwt + 2 disclosures + kbJwt = 4 parts
        assertThat(parts).hasSize(4)
    }
}
```

**Step 2: Write implementation**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import my.ssdid.wallet.domain.sdjwt.KeyBindingJwt
import my.ssdid.wallet.domain.sdjwt.SdJwtVc

class VpTokenBuilder {

    fun build(
        sdJwtVc: SdJwtVc,
        selectedClaimNames: Set<String>,
        audience: String,
        nonce: String,
        algorithm: String,
        signer: (ByteArray) -> ByteArray
    ): String {
        // Filter disclosures to selected claims
        val selectedDisclosures = sdJwtVc.disclosures.filter { it.claimName in selectedClaimNames }

        // Build SD-JWT without KB-JWT first (needed for sd_hash)
        val sdJwtWithDisclosures = sdJwtVc.present(selectedDisclosures)

        // Create KB-JWT
        val kbJwt = KeyBindingJwt.create(
            sdJwtWithDisclosures = sdJwtWithDisclosures,
            audience = audience,
            nonce = nonce,
            algorithm = algorithm,
            signer = signer
        )

        // Assemble final presentation: issuerJwt~disc1~disc2~kbJwt
        return sdJwtVc.present(selectedDisclosures, kbJwt)
    }

    fun buildPresentationSubmission(
        definitionId: String,
        descriptorId: String
    ): PresentationSubmission {
        return PresentationSubmission(
            id = "submission-${System.currentTimeMillis()}",
            definitionId = definitionId,
            descriptorMap = listOf(
                DescriptorMapEntry(
                    id = descriptorId,
                    format = "vc+sd-jwt",
                    path = "$"
                )
            )
        )
    }
}

@Serializable
data class PresentationSubmission(
    val id: String,
    val definitionId: String,
    val descriptorMap: List<DescriptorMapEntry>
) {
    fun toJson(): String = Json.encodeToString(this)
}

@Serializable
data class DescriptorMapEntry(
    val id: String,
    val format: String,
    val path: String
)
```

**Step 3: Run tests — expected PASS**

**Step 4: Commit**

```bash
git commit -m "feat(android): add VpTokenBuilder and PresentationSubmission for OpenID4VP"
```

### Task 10: VpTokenBuilder (iOS)

Mirror Task 9 in Swift. Same test patterns, same logic using existing `SdJwtVc.present()` and `KeyBindingJwt.create()`.

**Commit:**
```bash
git commit -m "feat(ios): add VpTokenBuilder and PresentationSubmission for OpenID4VP"
```

---

## Task Group 5: Transport Layer

### Task 11: OpenId4VpTransport (Android)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpTransport.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpTransportTest.kt`

**Step 1: Write tests** — Mock OkHttpClient. Test: fetchRequestObject GET to request_uri returns JSON, postVpResponse POSTs form-encoded body to response_uri, postError sends error response, handles HTTP errors.

**Step 2: Write implementation** — Uses OkHttp directly (not Retrofit, since verifier endpoints vary). `fetchRequestObject(requestUri: String): Result<AuthorizationRequest>` does GET and parses response. `postVpResponse(responseUri, vpToken, presentationSubmission, state)` does form-encoded POST. `postError(responseUri, error, state)` sends error.

**Step 3: Commit**

```bash
git commit -m "feat(android): add OpenId4VpTransport for request fetch and response POST"
```

### Task 12: OpenId4VpTransport (iOS)

Mirror Task 11 in Swift using URLSession. Same API surface.

**Commit:**
```bash
git commit -m "feat(ios): add OpenId4VpTransport for request fetch and response POST"
```

---

## Task Group 6: Handler (Orchestrator)

### Task 13: OpenId4VpHandler (Android)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpHandler.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpHandlerTest.kt`

**Step 1: Write tests** — Mock transport, matchers, vault. Test the full orchestration:
1. `processRequest(uri)` → parses authorization request, fetches if by-reference, runs matcher, returns match results for UI
2. `submitPresentation(matchResult, selectedClaims, signer)` → builds VP token, POSTs response
3. `declineRequest(authRequest)` → POSTs error=access_denied
4. Error cases: no matching credentials, network failure on fetch

**Step 2: Write implementation**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import my.ssdid.wallet.domain.sdjwt.SdJwtParser
import my.ssdid.wallet.domain.vault.VaultStorage

class OpenId4VpHandler(
    private val transport: OpenId4VpTransport,
    private val peMatcher: PresentationDefinitionMatcher,
    private val dcqlMatcher: DcqlMatcher,
    private val vpTokenBuilder: VpTokenBuilder,
    private val vaultStorage: VaultStorage
) {
    data class ProcessedRequest(
        val authRequest: AuthorizationRequest,
        val matchResults: List<MatchResult>,
        val query: CredentialQuery
    )

    suspend fun processRequest(uri: String): Result<ProcessedRequest> = runCatching {
        var authRequest = AuthorizationRequest.parse(uri).getOrThrow()

        // Fetch request object if by-reference
        if (authRequest.requestUri != null) {
            authRequest = transport.fetchRequestObject(authRequest.requestUri).getOrThrow()
        }

        // Load stored credentials
        val storedVcs = vaultStorage.listSdJwtVcs()

        // Match using appropriate query language
        val (matchResults, query) = when {
            authRequest.presentationDefinition != null -> {
                val q = peMatcher.toCredentialQuery(authRequest.presentationDefinition)
                peMatcher.match(authRequest.presentationDefinition, storedVcs) to q
            }
            authRequest.dcqlQuery != null -> {
                val q = dcqlMatcher.toCredentialQuery(authRequest.dcqlQuery)
                dcqlMatcher.match(authRequest.dcqlQuery, storedVcs) to q
            }
            else -> throw IllegalStateException("No query in authorization request")
        }

        if (matchResults.isEmpty()) {
            // POST error to verifier
            authRequest.responseUri?.let { uri ->
                transport.postError(uri, "no_credentials_available", authRequest.state)
            }
            throw NoMatchingCredentialsException("No stored credentials match the request")
        }

        ProcessedRequest(authRequest, matchResults, query)
    }

    suspend fun submitPresentation(
        authRequest: AuthorizationRequest,
        matchResult: MatchResult,
        selectedClaims: Set<String>,
        algorithm: String,
        signer: (ByteArray) -> ByteArray
    ): Result<Unit> = runCatching {
        // Load the matched stored credential and parse it
        val storedVcs = vaultStorage.listSdJwtVcs()
        val storedVc = storedVcs.find { it.id == matchResult.credentialId }
            ?: throw IllegalStateException("Matched credential no longer available")

        val sdJwtVc = SdJwtParser.parse(storedVc.compact)

        // Build VP Token
        val vpToken = vpTokenBuilder.build(
            sdJwtVc = sdJwtVc,
            selectedClaimNames = selectedClaims,
            audience = authRequest.clientId,
            nonce = authRequest.nonce ?: throw IllegalStateException("Missing nonce"),
            algorithm = algorithm,
            signer = signer
        )

        // Build presentation_submission if PE was used
        val submission = if (authRequest.presentationDefinition != null) {
            val pd = kotlinx.serialization.json.Json.parseToJsonElement(
                authRequest.presentationDefinition
            ).jsonObject
            val definitionId = pd["id"]?.jsonPrimitive?.content ?: "unknown"
            vpTokenBuilder.buildPresentationSubmission(definitionId, matchResult.descriptorId)
        } else null

        // POST response
        transport.postVpResponse(
            responseUri = authRequest.responseUri!!,
            vpToken = vpToken,
            presentationSubmission = submission,
            state = authRequest.state
        )
    }

    suspend fun declineRequest(authRequest: AuthorizationRequest): Result<Unit> = runCatching {
        authRequest.responseUri?.let { uri ->
            transport.postError(uri, "access_denied", authRequest.state)
        }
    }
}

class NoMatchingCredentialsException(message: String) : Exception(message)
```

**Step 3: Run tests — expected PASS**

**Step 4: Commit**

```bash
git commit -m "feat(android): add OpenId4VpHandler orchestrator"
```

### Task 14: OpenId4VpHandler (iOS)

Mirror Task 13 in Swift. Same orchestration pattern.

**Commit:**
```bash
git commit -m "feat(ios): add OpenId4VpHandler orchestrator"
```

---

## Task Group 7: Deep Link & Navigation

### Task 15: Register openid4vp:// Deep Link (Android)

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml` — Add intent-filter for `openid4vp` scheme
- Modify: `android/app/src/main/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandler.kt` — Add openid4vp parsing
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt` — Add PresentationRequest screen
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt` — Add PresentationRequest route

**Step 1: Add intent-filter to AndroidManifest.xml**

After the existing `ssdid` intent-filter (line 33), add:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="openid4vp" />
</intent-filter>
```

**Step 2: Update DeepLinkHandler**

Add `"openid4vp"` scheme detection. When scheme is `openid4vp`, create a new `DeepLinkAction` with action `"presentation-request"` and pass the full URI string as a parameter.

**Step 3: Add Screen.PresentationRequest**

In `Screen.kt`, add:
```kotlin
object PresentationRequest : Screen("presentation_request?uri={uri}") {
    fun createRoute(encodedUri: String): String =
        "presentation_request?uri=${Uri.encode(encodedUri)}"
}
```

**Step 4: Add NavGraph route**

Wire `Screen.PresentationRequest` to a new `PresentationRequestScreen` composable (created in Task 17).

**Step 5: Commit**

```bash
git commit -m "feat(android): register openid4vp:// deep link and navigation route"
```

### Task 16: Register openid4vp:// Deep Link (iOS)

**Files:**
- Modify: `ios/SsdidWallet/Info.plist` — Add `openid4vp` to URL schemes
- Modify: `ios/SsdidWallet/Platform/DeepLink/DeepLinkHandler.swift` — Add openid4vp parsing
- Modify: `ios/SsdidWallet/UI/Navigation/AppRouter.swift` — Add PresentationRequest route

**Step 1: Add to Info.plist** — Add `openid4vp` string to CFBundleURLSchemes array.

**Step 2: Update DeepLinkHandler** — Add `.presentationRequest` case to DeepLinkAction enum. Parse `openid4vp://` URIs.

**Step 3: Add Route** — Add `case presentationRequest(uri: String)` to Route enum.

**Step 4: Commit**

```bash
git commit -m "feat(ios): register openid4vp:// deep link and navigation route"
```

---

## Task Group 8: UI — Presentation Request Screen

### Task 17: PresentationRequestScreen + ViewModel (Android)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/presentation/PresentationRequestScreen.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/presentation/PresentationRequestViewModel.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt` — Provide OpenId4VpHandler

**Step 1: Create ViewModel**

```kotlin
@HiltViewModel
class PresentationRequestViewModel @Inject constructor(
    private val handler: OpenId4VpHandler,
    private val vault: Vault,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class CredentialMatch(
            val verifierId: String,
            val matchResults: List<MatchResult>,
            val selectedClaims: Map<String, Boolean>  // claimName -> selected
        ) : UiState()
        object Submitting : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
        object NoCredentials : UiState()
    }

    private val uri: String = savedStateHandle["uri"] ?: ""
    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state = _state.asStateFlow()

    init { processRequest() }

    private fun processRequest() { /* call handler.processRequest(uri) */ }
    fun toggleClaim(claimName: String) { /* toggle in selectedClaims */ }
    fun approve() { /* call handler.submitPresentation() */ }
    fun decline() { /* call handler.declineRequest() */ }
}
```

**Step 2: Create Screen** — Compose UI showing:
- Verifier identity (`client_id`)
- Matched credential card with type badge
- Claim toggles (required locked, optional toggleable)
- Approve / Decline buttons
- Loading, error, success states

**Step 3: Wire DI** — Add `provideOpenId4VpHandler()` to AppModule.

**Step 4: Wire NavGraph** — Connect PresentationRequestScreen to the route added in Task 15.

**Step 5: Commit**

```bash
git commit -m "feat(android): add PresentationRequestScreen and ViewModel for OpenID4VP"
```

### Task 18: PresentationRequestScreen (iOS)

**Files:**
- Create: `ios/SsdidWallet/Feature/Presentation/PresentationRequestScreen.swift`
- Modify: `ios/SsdidWallet/App/ServiceContainer.swift` — Add OpenId4VpHandler
- Modify: `ios/SsdidWallet/UI/Navigation/AppRouter.swift` — Wire route to screen

Mirror Task 17 in SwiftUI. Use `@State`/`@Observable` for state management. Wire `OpenId4VpHandler` via `ServiceContainer`.

**Commit:**
```bash
git commit -m "feat(ios): add PresentationRequestScreen for OpenID4VP"
```

---

## Task Group 9: QR Code Integration

### Task 19: Route openid4vp:// from QR Scanner (Android + iOS)

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt` — QR payload routing
- Modify: `ios/SsdidWallet/Feature/Scan/ScanQrScreen.swift` — QR content routing

**Step 1: Android** — In NavGraph.kt's QR payload handling section (around line 138-173), add a case for `openid4vp://` scheme that navigates to `Screen.PresentationRequest.createRoute(encodedUri)`.

**Step 2: iOS** — In ScanQrScreen's `handleQrContent()`, add routing for `openid4vp://` URIs to `router.push(.presentationRequest(uri: content))`.

**Step 3: Commit**

```bash
git commit -m "feat: route openid4vp:// URIs from QR scanner on both platforms"
```

---

## Task Group 10: Integration Testing

### Task 20: End-to-End Flow Test (Android)

**Files:**
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpIntegrationTest.kt`

**Step 1: Write integration test**

Test the full flow: parse authorization request → match credential → build VP token → verify VP token structure. Uses real `SdJwtIssuer` to create a credential, real `PresentationDefinitionMatcher` to match, real `VpTokenBuilder` to build. Only mocks transport (no real HTTP).

Tests:
1. Full PE 2.0 flow: issue credential → parse auth request → match → build VP token → verify KB-JWT
2. Full DCQL flow: same with DCQL query
3. Decline flow: parse → decline → verify error POST
4. No match flow: parse → match returns empty → verify error POST

**Step 2: Run tests — expected PASS**

**Step 3: Commit**

```bash
git commit -m "test(android): add OpenID4VP end-to-end integration tests"
```

### Task 21: End-to-End Flow Test (iOS)

Mirror Task 20 in Swift XCTest.

**Commit:**
```bash
git commit -m "test(ios): add OpenID4VP end-to-end integration tests"
```

---

## Execution Dependencies

```
Task 1, 2 (test vectors) — independent, do first
Task 3 (CredentialQuery model) — required by Tasks 6, 7, 9
Tasks 4, 5 (AuthRequest parser) — independent per platform, can parallel
Tasks 6, 7 (PE + DCQL matcher Android) — requires Task 3
Task 8 (PE + DCQL matcher iOS) — independent of Android, can parallel with 6+7
Task 9, 10 (VpTokenBuilder) — requires existing SD-JWT stack
Tasks 11, 12 (Transport) — independent
Tasks 13, 14 (Handler) — requires Tasks 4-12
Tasks 15, 16 (Deep link) — independent of domain code
Tasks 17, 18 (UI) — requires Tasks 13-16
Task 19 (QR) — requires Tasks 15-16
Tasks 20, 21 (Integration) — requires all above
```

**Parallelization opportunities:**
- Tasks 1+2 together
- Tasks 4+5 (Android + iOS parsers in parallel)
- Tasks 6+7+8 (all matchers in parallel)
- Tasks 9+10 (VP builders in parallel)
- Tasks 11+12 (transport in parallel)
- Tasks 13+14 (handlers in parallel)
- Tasks 15+16 (deep links in parallel)
- Tasks 17+18 (UI in parallel)
- Tasks 20+21 (integration tests in parallel)
