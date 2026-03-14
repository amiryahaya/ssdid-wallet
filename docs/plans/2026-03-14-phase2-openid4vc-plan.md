# OpenID4VC Implementation Plan (Phase 2a + 2b Combined)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add OpenID4VP 1.0 (presentation) and OpenID4VCI 1.0 (issuance) support to the SSDID wallet for both Android and iOS.

**Architecture:** New `domain/oid4vp/` and `domain/oid4vci/` packages using handler → transport → builder pattern. Shared infrastructure (Algorithm JWA mapping, deep link routing) built first, then VP, then VCI. iOS mirrors Android file-for-file.

**Tech Stack:** Kotlin + kotlinx-serialization + OkHttp (Android), Swift + Foundation (iOS), JUnit 4 + Mockk + Truth (tests)

---

## Phase A: Shared Infrastructure

### Task 1: Add `toJwaName()` to Algorithm enum

**Files:**
- Modify: `android/app/src/main/java/my/ssdid/wallet/domain/model/Algorithm.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/model/AlgorithmJwaTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlgorithmJwaTest {

    @Test
    fun testEd25519ToJwaName() {
        assertThat(Algorithm.ED25519.toJwaName()).isEqualTo("EdDSA")
    }

    @Test
    fun testEcdsaP256ToJwaName() {
        assertThat(Algorithm.ECDSA_P256.toJwaName()).isEqualTo("ES256")
    }

    @Test
    fun testEcdsaP384ToJwaName() {
        assertThat(Algorithm.ECDSA_P384.toJwaName()).isEqualTo("ES384")
    }

    @Test
    fun testKazSign128ToJwaName() {
        assertThat(Algorithm.KAZ_SIGN_128.toJwaName()).isEqualTo("KAZ128")
    }

    @Test
    fun testMlDsa44ToJwaName() {
        assertThat(Algorithm.ML_DSA_44.toJwaName()).isEqualTo("ML-DSA-44")
    }

    @Test
    fun testRoundTripAllAlgorithms() {
        for (algo in Algorithm.entries) {
            val jwa = algo.toJwaName()
            val roundTripped = Algorithm.fromJwaName(jwa)
            // KAZ-Sign shares w3cType, so fromJwaName maps to specific levels
            assertThat(roundTripped).isNotNull()
        }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.model.AlgorithmJwaTest" 2>&1 | tail -5`
Expected: FAIL — `toJwaName()` does not exist

**Step 3: Write minimal implementation**

Add to `Algorithm.kt`, inside the enum body (after `isSlhDsa`):

```kotlin
fun toJwaName(): String {
    return jwaMap.entries.first { it.value == this }.key
}
```

And add reverse entries for KAZ_SIGN_192 and KAZ_SIGN_256 to `jwaMap` — they're already there.

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.model.AlgorithmJwaTest" 2>&1 | tail -5`
Expected: PASS (6 tests)

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/model/Algorithm.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/model/AlgorithmJwaTest.kt
git commit -m "feat: add Algorithm.toJwaName() reverse JWA mapping"
```

---

### Task 2: Register OpenID4VP and OpenID4VCI deep link schemes (Android)

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandler.kt`
- Modify: `android/app/src/test/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandlerTest.kt`

**Step 1: Write the failing tests**

Add to existing `DeepLinkHandlerTest.kt`:

```kotlin
@Test
fun testParseOpenId4VpUri() {
    val uri = Uri.parse("openid4vp://?client_id=https://verifier.example.com&request_uri=https://verifier.example.com/request/123")
    val action = DeepLinkHandler.parse(uri)
    assertThat(action).isNotNull()
    assertThat(action!!.action).isEqualTo("openid4vp")
}

@Test
fun testParseOpenId4VpUriByValue() {
    val uri = Uri.parse("openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/response&nonce=abc123&response_mode=direct_post&presentation_definition=%7B%7D")
    val action = DeepLinkHandler.parse(uri)
    assertThat(action).isNotNull()
    assertThat(action!!.action).isEqualTo("openid4vp")
}

@Test
fun testParseCredentialOfferUri() {
    val offerJson = """{"credential_issuer":"https://issuer.example.com","credential_configuration_ids":["UnivDegree"],"grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"abc123"}}}"""
    val uri = Uri.parse("openid-credential-offer://?credential_offer=${Uri.encode(offerJson)}")
    val action = DeepLinkHandler.parse(uri)
    assertThat(action).isNotNull()
    assertThat(action!!.action).isEqualTo("openid-credential-offer")
}

@Test
fun testParseCredentialOfferUriByReference() {
    val uri = Uri.parse("openid-credential-offer://?credential_offer_uri=https://issuer.example.com/offers/123")
    val action = DeepLinkHandler.parse(uri)
    assertThat(action).isNotNull()
    assertThat(action!!.action).isEqualTo("openid-credential-offer")
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.DeepLinkHandlerTest" 2>&1 | tail -10`
Expected: FAIL — `parse()` returns null for non-`ssdid` schemes

**Step 3: Implement**

In `DeepLinkHandler.kt`, update `parse()` to handle new schemes:

```kotlin
fun parse(uri: Uri): DeepLinkAction? {
    return when (uri.scheme) {
        "ssdid" -> parseSsdid(uri)
        "openid4vp" -> parseOpenId4Vp(uri)
        "openid-credential-offer" -> parseCredentialOffer(uri)
        else -> null
    }
}

private fun parseSsdid(uri: Uri): DeepLinkAction? {
    // ... existing ssdid:// parsing logic (move from current parse())
}

private fun parseOpenId4Vp(uri: Uri): DeepLinkAction? {
    // Pass the full URI as rawUri — OpenId4VpHandler will parse the details
    val rawUri = uri.toString()
    return DeepLinkAction(
        action = "openid4vp",
        serverUrl = "",
        callbackUrl = rawUri
    )
}

private fun parseCredentialOffer(uri: Uri): DeepLinkAction? {
    val offerJson = uri.getQueryParameter("credential_offer")
    val offerUri = uri.getQueryParameter("credential_offer_uri")
    if (offerJson == null && offerUri == null) return null
    if (offerUri != null && !offerUri.startsWith("https://")) return null
    return DeepLinkAction(
        action = "openid-credential-offer",
        serverUrl = "",
        callbackUrl = offerJson ?: offerUri ?: ""
    )
}
```

Add `DeepLinkAction.toNavRoute()` case for `openid4vp`:

```kotlin
"openid4vp" -> Screen.PresentationRequest.createRoute(callbackUrl)
"openid-credential-offer" -> Screen.CredentialOffer.createRoute("", callbackUrl)
```

Add to `AndroidManifest.xml` after the existing `ssdid` intent-filter:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="openid4vp" />
</intent-filter>
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="openid-credential-offer" />
</intent-filter>
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.DeepLinkHandlerTest" 2>&1 | tail -10`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add android/app/src/main/AndroidManifest.xml \
        android/app/src/main/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandler.kt \
        android/app/src/test/java/my/ssdid/wallet/platform/deeplink/DeepLinkHandlerTest.kt
git commit -m "feat: register openid4vp:// and openid-credential-offer:// deep link schemes"
```

---

### Task 3: Register deep link schemes (iOS)

**Files:**
- Modify: `ios/SsdidWallet/Info.plist` (or project settings)
- Modify: `ios/SsdidWallet/Platform/DeepLink/DeepLinkHandler.swift`
- Modify: `ios/SsdidWallet/UI/Navigation/AppRouter.swift`
- Modify: `ios/SsdidWallet/UI/Navigation/ContentView.swift`

**Step 1: Add URL schemes to Info.plist**

Add `openid4vp` and `openid-credential-offer` to `CFBundleURLSchemes` array alongside existing `ssdid`.

**Step 2: Add cases to DeepLinkHandler.swift**

```swift
// In DeepLinkHandler.parse(url:)
func parse(url: URL) throws -> DeepLinkAction {
    guard let scheme = url.scheme else {
        throw DeepLinkError.invalidURL(url.absoluteString)
    }

    switch scheme {
    case "ssdid":
        return try parseSsdid(url: url)
    case "openid4vp":
        return .openid4vp(rawUri: url.absoluteString)
    case "openid-credential-offer":
        return try parseCredentialOffer(url: url)
    default:
        throw DeepLinkError.invalidScheme(scheme)
    }
}

// Add new enum cases
enum DeepLinkAction: Equatable {
    // ... existing cases ...
    case openid4vp(rawUri: String)
    case openidCredentialOffer(offerData: String)
}
```

Parse credential offer from query params:

```swift
private func parseCredentialOffer(url: URL) throws -> DeepLinkAction {
    let params = parseQueryParameters(url: url)
    if let offerJson = params["credential_offer"] {
        return .openidCredentialOffer(offerData: offerJson)
    }
    if let offerUri = params["credential_offer_uri"] {
        guard offerUri.hasPrefix("https://") else {
            throw DeepLinkError.unsafeURL("credential_offer_uri must be HTTPS")
        }
        return .openidCredentialOffer(offerData: offerUri)
    }
    throw DeepLinkError.missingRequiredParameter("credential_offer or credential_offer_uri")
}
```

**Step 3: Add Route cases to AppRouter.swift**

```swift
case presentationRequest(rawUri: String)
// credentialOffer already exists
```

**Step 4: Add routing to ContentView.swift**

In `routeDeepLink()`:

```swift
case .openid4vp(let rawUri):
    router.push(.presentationRequest(rawUri: rawUri))
case .openidCredentialOffer(let offerData):
    router.push(.credentialOffer(issuerUrl: "", offerId: offerData))
```

In `routeDestination()`:

```swift
case .presentationRequest(let rawUri):
    PresentationRequestScreen(rawUri: rawUri)
```

**Step 5: Commit**

```bash
git add ios/
git commit -m "feat(ios): register openid4vp:// and openid-credential-offer:// URL schemes"
```

---

## Phase B: OpenID4VP Domain Layer (Android)

### Task 4: AuthorizationRequest model + parser

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/AuthorizationRequest.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/AuthorizationRequestTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthorizationRequestTest {

    @Test
    fun parseByReference() {
        val uri = "openid4vp://?client_id=https://verifier.example.com&request_uri=https://verifier.example.com/request/123"
        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isSuccess).isTrue()
        val req = result.getOrThrow()
        assertThat(req.clientId).isEqualTo("https://verifier.example.com")
        assertThat(req.requestUri).isEqualTo("https://verifier.example.com/request/123")
    }

    @Test
    fun parseByValue() {
        val pd = """{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"IdentityCredential"}}]}}]}"""
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n-123&response_mode=direct_post&presentation_definition=${Uri.encode(pd)}"
        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isSuccess).isTrue()
        val req = result.getOrThrow()
        assertThat(req.clientId).isEqualTo("https://v.example.com")
        assertThat(req.responseUri).isEqualTo("https://v.example.com/cb")
        assertThat(req.nonce).isEqualTo("n-123")
        assertThat(req.presentationDefinition).isNotNull()
    }

    @Test
    fun rejectHttpRequestUri() {
        val uri = "openid4vp://?client_id=https://v.example.com&request_uri=http://v.example.com/request"
        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()!!.message).contains("HTTPS")
    }

    @Test
    fun rejectMissingClientId() {
        val uri = "openid4vp://?request_uri=https://v.example.com/request"
        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun rejectNonDirectPostResponseMode() {
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n&response_mode=fragment&presentation_definition=%7B%7D"
        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()!!.message).contains("direct_post")
    }

    @Test
    fun rejectMissingQuery() {
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n&response_mode=direct_post"
        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()!!.message).contains("presentation_definition")
    }

    @Test
    fun rejectHttpResponseUri() {
        val pd = """{"id":"pd-1","input_descriptors":[]}"""
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=http://v.example.com/cb&nonce=n&response_mode=direct_post&presentation_definition=${Uri.encode(pd)}"
        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()!!.message).contains("HTTPS")
    }

    @Test
    fun parseDcqlQuery() {
        val dcql = """{"credentials":[{"id":"cred-1","format":"vc+sd-jwt","meta":{"vct_values":["IdentityCredential"]}}]}"""
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n&response_mode=direct_post&dcql_query=${Uri.encode(dcql)}"
        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isSuccess).isTrue()
        val req = result.getOrThrow()
        assertThat(req.dcqlQuery).isNotNull()
        assertThat(req.presentationDefinition).isNull()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.AuthorizationRequestTest" 2>&1 | tail -5`
Expected: FAIL — class does not exist

**Step 3: Implement**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

data class AuthorizationRequest(
    val clientId: String,
    val requestUri: String? = null,
    val responseUri: String? = null,
    val nonce: String? = null,
    val state: String? = null,
    val responseType: String? = null,
    val responseMode: String? = null,
    val presentationDefinition: JsonObject? = null,
    val dcqlQuery: JsonObject? = null
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(uriString: String): Result<AuthorizationRequest> = runCatching {
            val uri = Uri.parse(uriString)
            val clientId = uri.getQueryParameter("client_id")
                ?: throw IllegalArgumentException("Missing required parameter: client_id")

            val requestUri = uri.getQueryParameter("request_uri")

            // By-reference: only client_id + request_uri
            if (requestUri != null) {
                require(requestUri.startsWith("https://")) {
                    "request_uri must be HTTPS: $requestUri"
                }
                return@runCatching AuthorizationRequest(
                    clientId = clientId,
                    requestUri = requestUri
                )
            }

            // By-value: full parameters
            val responseUri = uri.getQueryParameter("response_uri")
            val nonce = uri.getQueryParameter("nonce")
            val state = uri.getQueryParameter("state")
            val responseType = uri.getQueryParameter("response_type")
            val responseMode = uri.getQueryParameter("response_mode")

            require(responseMode == "direct_post") {
                "response_mode must be direct_post, got: $responseMode"
            }
            require(responseUri != null) { "Missing required parameter: response_uri" }
            require(responseUri.startsWith("https://")) {
                "response_uri must be HTTPS: $responseUri"
            }
            require(nonce != null) { "Missing required parameter: nonce" }

            val pdRaw = uri.getQueryParameter("presentation_definition")
            val dcqlRaw = uri.getQueryParameter("dcql_query")

            val pd = pdRaw?.let { json.parseToJsonElement(it) as? JsonObject }
            val dcql = dcqlRaw?.let { json.parseToJsonElement(it) as? JsonObject }

            require(pd != null || dcql != null) {
                "Must provide presentation_definition or dcql_query"
            }
            require(pd == null || dcql == null) {
                "Cannot provide both presentation_definition and dcql_query"
            }

            AuthorizationRequest(
                clientId = clientId,
                responseUri = responseUri,
                nonce = nonce,
                state = state,
                responseType = responseType,
                responseMode = responseMode,
                presentationDefinition = pd,
                dcqlQuery = dcql
            )
        }

        fun parseJson(jsonString: String): Result<AuthorizationRequest> = runCatching {
            val obj = json.parseToJsonElement(jsonString) as JsonObject

            val clientId = obj["client_id"]?.let {
                it.toString().trim('"')
            } ?: throw IllegalArgumentException("Missing client_id")

            val responseUri = obj["response_uri"]?.let { it.toString().trim('"') }
            val nonce = obj["nonce"]?.let { it.toString().trim('"') }
            val state = obj["state"]?.let { it.toString().trim('"') }
            val responseMode = obj["response_mode"]?.let { it.toString().trim('"') }

            require(responseMode == "direct_post") {
                "response_mode must be direct_post, got: $responseMode"
            }
            require(responseUri != null) { "Missing response_uri" }
            require(responseUri.startsWith("https://")) {
                "response_uri must be HTTPS: $responseUri"
            }
            require(nonce != null) { "Missing nonce" }

            val pd = obj["presentation_definition"] as? JsonObject
            val dcql = obj["dcql_query"] as? JsonObject

            require(pd != null || dcql != null) {
                "Must provide presentation_definition or dcql_query"
            }

            AuthorizationRequest(
                clientId = clientId,
                responseUri = responseUri,
                nonce = nonce,
                state = state,
                responseMode = responseMode,
                presentationDefinition = pd,
                dcqlQuery = dcql
            )
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.AuthorizationRequestTest" 2>&1 | tail -5`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/AuthorizationRequest.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/AuthorizationRequestTest.kt
git commit -m "feat: add OpenID4VP AuthorizationRequest parser with validation"
```

---

### Task 5: OpenId4VpTransport

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpTransport.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpTransportTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class OpenId4VpTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var transport: OpenId4VpTransport

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        transport = OpenId4VpTransport(OkHttpClient())
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun fetchRequestObject() {
        val requestJson = """{"client_id":"https://v.example.com","response_uri":"https://v.example.com/cb","nonce":"n","response_mode":"direct_post","presentation_definition":{"id":"pd-1","input_descriptors":[]}}"""
        server.enqueue(MockResponse().setBody(requestJson).setResponseCode(200))
        val body = transport.fetchRequestObject(server.url("/request/123").toString())
        assertThat(body).isEqualTo(requestJson)
    }

    @Test
    fun postVpResponse() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        transport.postVpResponse(
            responseUri = server.url("/response").toString(),
            vpToken = "eyJ...",
            presentationSubmission = """{"id":"sub-1"}""",
            state = "state-123"
        )
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        val body = request.body.readUtf8()
        assertThat(body).contains("vp_token=")
        assertThat(body).contains("presentation_submission=")
        assertThat(body).contains("state=state-123")
        assertThat(request.getHeader("Content-Type")).contains("application/x-www-form-urlencoded")
    }

    @Test
    fun postError() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        transport.postError(
            responseUri = server.url("/response").toString(),
            error = "access_denied",
            state = "s-1"
        )
        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertThat(body).contains("error=access_denied")
        assertThat(body).contains("state=s-1")
    }

    @Test(expected = RuntimeException::class)
    fun fetchRequestObjectFailsOnHttpError() {
        server.enqueue(MockResponse().setResponseCode(500))
        transport.fetchRequestObject(server.url("/request/fail").toString())
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.OpenId4VpTransportTest" 2>&1 | tail -5`
Expected: FAIL — class does not exist

**Step 3: Implement**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class OpenId4VpTransport(private val client: OkHttpClient) {

    fun fetchRequestObject(requestUri: String): String {
        val request = Request.Builder().url(requestUri).get().build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
            response.body?.string() ?: throw RuntimeException("Empty response body")
        }
    }

    fun postVpResponse(
        responseUri: String,
        vpToken: String,
        presentationSubmission: String,
        state: String?
    ) {
        val formBuilder = FormBody.Builder()
            .add("vp_token", vpToken)
            .add("presentation_submission", presentationSubmission)
        if (state != null) formBuilder.add("state", state)

        val request = Request.Builder()
            .url(responseUri)
            .post(formBuilder.build())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
        }
    }

    fun postError(responseUri: String, error: String, state: String?) {
        val formBuilder = FormBody.Builder().add("error", error)
        if (state != null) formBuilder.add("state", state)

        val request = Request.Builder()
            .url(responseUri)
            .post(formBuilder.build())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.OpenId4VpTransportTest" 2>&1 | tail -5`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpTransport.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpTransportTest.kt
git commit -m "feat: add OpenId4VpTransport with form-encoded POST"
```

---

### Task 6: PresentationDefinitionMatcher

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/PresentationDefinitionMatcher.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/PresentationDefinitionMatcherTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import org.junit.Test

class PresentationDefinitionMatcherTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val matcher = PresentationDefinitionMatcher()

    private val credential = StoredSdJwtVc(
        id = "vc-1",
        compact = "eyJ...~disc1~disc2~",
        issuer = "did:ssdid:issuer1",
        subject = "did:ssdid:holder1",
        type = "IdentityCredential",
        claims = mapOf("name" to "Ahmad", "email" to "ahmad@example.com"),
        disclosableClaims = listOf("name", "email"),
        issuedAt = 1700000000L
    )

    @Test
    fun matchByVct() {
        val pd = json.parseToJsonElement("""
            {
                "id": "pd-1",
                "input_descriptors": [{
                    "id": "id-1",
                    "format": {"vc+sd-jwt": {}},
                    "constraints": {
                        "fields": [{
                            "path": ["$.vct"],
                            "filter": {"const": "IdentityCredential"}
                        }]
                    }
                }]
            }
        """) as JsonObject

        val results = matcher.match(pd, listOf(credential))
        assertThat(results).hasSize(1)
        assertThat(results[0].credential.id).isEqualTo("vc-1")
        assertThat(results[0].descriptorId).isEqualTo("id-1")
    }

    @Test
    fun noMatchWhenVctDiffers() {
        val pd = json.parseToJsonElement("""
            {
                "id": "pd-1",
                "input_descriptors": [{
                    "id": "id-1",
                    "format": {"vc+sd-jwt": {}},
                    "constraints": {
                        "fields": [{
                            "path": ["$.vct"],
                            "filter": {"const": "DriverLicense"}
                        }]
                    }
                }]
            }
        """) as JsonObject

        val results = matcher.match(pd, listOf(credential))
        assertThat(results).isEmpty()
    }

    @Test
    fun matchWithClaimFields() {
        val pd = json.parseToJsonElement("""
            {
                "id": "pd-1",
                "input_descriptors": [{
                    "id": "id-1",
                    "format": {"vc+sd-jwt": {}},
                    "constraints": {
                        "fields": [
                            {"path": ["$.vct"], "filter": {"const": "IdentityCredential"}},
                            {"path": ["$.name"]},
                            {"path": ["$.email"], "optional": true}
                        ]
                    }
                }]
            }
        """) as JsonObject

        val results = matcher.match(pd, listOf(credential))
        assertThat(results).hasSize(1)
        assertThat(results[0].requiredClaims).contains("name")
        assertThat(results[0].optionalClaims).contains("email")
    }

    @Test
    fun missingInputDescriptorId() {
        val pd = json.parseToJsonElement("""
            {"id": "pd-1", "input_descriptors": [{"format": {"vc+sd-jwt": {}}}]}
        """) as JsonObject
        val results = matcher.match(pd, listOf(credential))
        assertThat(results).isEmpty()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.PresentationDefinitionMatcherTest" 2>&1 | tail -5`
Expected: FAIL

**Step 3: Implement**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import kotlinx.serialization.json.*
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc

data class MatchResult(
    val credential: StoredSdJwtVc,
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

            // Check format supports vc+sd-jwt
            val format = obj["format"]?.jsonObject
            if (format != null && !format.containsKey("vc+sd-jwt")) continue

            val fields = obj["constraints"]?.jsonObject
                ?.get("fields")?.jsonArray ?: continue

            for (cred in credentials) {
                if (matchesConstraints(cred, fields)) {
                    val (required, optional) = extractClaims(fields, cred)
                    results.add(MatchResult(cred, descriptorId, required, optional))
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
                    if (claimName !in cred.claims && claimName !in cred.disclosableClaims) {
                        return false
                    }
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
            val filterConst = obj["filter"]?.jsonObject?.get("const")?.jsonPrimitive?.contentOrNull

            for (path in paths) {
                if (path == "$.vct") continue // vct is a type filter, not a claim
                val claimName = path.removePrefix("$.")
                if (claimName in cred.claims || claimName in cred.disclosableClaims) {
                    if (isOptional) optional.add(claimName) else required.add(claimName)
                }
            }
        }

        return required to optional
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.PresentationDefinitionMatcherTest" 2>&1 | tail -5`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/PresentationDefinitionMatcher.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/PresentationDefinitionMatcherTest.kt
git commit -m "feat: add PresentationDefinitionMatcher for PE 2.0 credential matching"
```

---

### Task 7: DcqlMatcher

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/DcqlMatcher.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/DcqlMatcherTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import org.junit.Test

class DcqlMatcherTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val matcher = DcqlMatcher()

    private val credential = StoredSdJwtVc(
        id = "vc-1",
        compact = "eyJ...",
        issuer = "did:ssdid:issuer1",
        subject = "did:ssdid:holder1",
        type = "IdentityCredential",
        claims = mapOf("name" to "Ahmad", "email" to "a@example.com"),
        disclosableClaims = listOf("name", "email"),
        issuedAt = 1700000000L
    )

    @Test
    fun matchByVctValues() {
        val dcql = json.parseToJsonElement("""
            {
                "credentials": [{
                    "id": "cred-1",
                    "format": "vc+sd-jwt",
                    "meta": {"vct_values": ["IdentityCredential"]}
                }]
            }
        """) as JsonObject

        val results = matcher.match(dcql, listOf(credential))
        assertThat(results).hasSize(1)
        assertThat(results[0].descriptorId).isEqualTo("cred-1")
    }

    @Test
    fun noMatchWhenVctDiffers() {
        val dcql = json.parseToJsonElement("""
            {
                "credentials": [{
                    "id": "cred-1",
                    "format": "vc+sd-jwt",
                    "meta": {"vct_values": ["DriverLicense"]}
                }]
            }
        """) as JsonObject

        val results = matcher.match(dcql, listOf(credential))
        assertThat(results).isEmpty()
    }

    @Test
    fun matchWithClaimsPaths() {
        val dcql = json.parseToJsonElement("""
            {
                "credentials": [{
                    "id": "cred-1",
                    "format": "vc+sd-jwt",
                    "meta": {"vct_values": ["IdentityCredential"]},
                    "claims": [
                        {"path": ["name"]},
                        {"path": ["email"], "optional": true}
                    ]
                }]
            }
        """) as JsonObject

        val results = matcher.match(dcql, listOf(credential))
        assertThat(results).hasSize(1)
        assertThat(results[0].requiredClaims).contains("name")
        assertThat(results[0].optionalClaims).contains("email")
    }

    @Test
    fun missingCredentialId() {
        val dcql = json.parseToJsonElement("""
            {"credentials": [{"format": "vc+sd-jwt"}]}
        """) as JsonObject
        val results = matcher.match(dcql, listOf(credential))
        assertThat(results).isEmpty()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.DcqlMatcherTest" 2>&1 | tail -5`

**Step 3: Implement**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import kotlinx.serialization.json.*
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
                ?.map { it.jsonPrimitive.content }
                ?.toSet()

            val claimsSpec = obj["claims"]?.jsonArray

            for (cred in credentials) {
                if (vctValues != null && cred.type !in vctValues) continue

                val (required, optional) = if (claimsSpec != null) {
                    extractClaims(claimsSpec, cred)
                } else {
                    cred.disclosableClaims to emptyList()
                }

                results.add(MatchResult(cred, id, required, optional))
            }
        }

        return results
    }

    private fun extractClaims(claimsSpec: JsonArray, cred: StoredSdJwtVc): Pair<List<String>, List<String>> {
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
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.DcqlMatcherTest" 2>&1 | tail -5`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/DcqlMatcher.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/DcqlMatcherTest.kt
git commit -m "feat: add DcqlMatcher for DCQL credential query matching"
```

---

### Task 8: VpTokenBuilder

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/VpTokenBuilder.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/VpTokenBuilderTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import org.junit.Test

class VpTokenBuilderTest {

    private val signer: (ByteArray) -> ByteArray = { data -> data.copyOf(64) } // dummy signer

    @Test
    fun buildVpTokenWithSelectedDisclosures() {
        val cred = StoredSdJwtVc(
            id = "vc-1",
            compact = "eyJhbGciOiJFZERTQSJ9.eyJpc3MiOiJkaWQ6c3NkaWQ6aXNzdWVyMSJ9.sig~WyJzYWx0MSIsIm5hbWUiLCJBaG1hZCJd~WyJzYWx0MiIsImVtYWlsIiwiYUBleC5jb20iXQ~",
            issuer = "did:ssdid:issuer1",
            subject = "did:ssdid:holder1",
            type = "IdentityCredential",
            claims = mapOf("name" to "Ahmad", "email" to "a@ex.com"),
            disclosableClaims = listOf("name", "email"),
            issuedAt = 1700000000L
        )

        val vpToken = VpTokenBuilder.build(
            storedSdJwtVc = cred,
            selectedClaims = listOf("name"), // only disclose name, not email
            audience = "https://verifier.example.com",
            nonce = "nonce-123",
            algorithm = "EdDSA",
            signer = signer
        )

        // VP token = issuer_jwt~selected_disclosures~kb_jwt
        val parts = vpToken.split("~")
        assertThat(parts.size).isAtLeast(3) // issuer_jwt, at least 1 disclosure, kb_jwt
        // First part is the issuer JWT
        assertThat(parts[0]).startsWith("eyJ")
        // Last part is the KB-JWT (3 dots)
        assertThat(parts.last().count { it == '.' }).isEqualTo(2)
    }

    @Test
    fun buildVpTokenWithAllDisclosures() {
        val cred = StoredSdJwtVc(
            id = "vc-1",
            compact = "eyJ.eyJ.sig~disc1~disc2~",
            issuer = "did:ssdid:issuer1",
            subject = "did:ssdid:holder1",
            type = "IdentityCredential",
            claims = mapOf("name" to "Ahmad", "email" to "a@ex.com"),
            disclosableClaims = listOf("name", "email"),
            issuedAt = 1700000000L
        )

        val vpToken = VpTokenBuilder.build(
            storedSdJwtVc = cred,
            selectedClaims = listOf("name", "email"),
            audience = "https://v.example.com",
            nonce = "n-1",
            algorithm = "EdDSA",
            signer = signer
        )

        val parts = vpToken.split("~")
        // issuer_jwt + 2 disclosures + kb_jwt
        assertThat(parts.size).isEqualTo(4)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.VpTokenBuilderTest" 2>&1 | tail -5`

**Step 3: Implement**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import my.ssdid.wallet.domain.sdjwt.KeyBindingJwt
import my.ssdid.wallet.domain.sdjwt.SdJwtParser
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc

object VpTokenBuilder {

    fun build(
        storedSdJwtVc: StoredSdJwtVc,
        selectedClaims: List<String>,
        audience: String,
        nonce: String,
        algorithm: String,
        signer: (ByteArray) -> ByteArray,
        issuedAt: Long = System.currentTimeMillis() / 1000
    ): String {
        val parsed = SdJwtParser.parse(storedSdJwtVc.compact)

        // Filter disclosures to only selected claims
        val selectedDisclosures = parsed.disclosures.filter { it.claimName in selectedClaims }

        // Build SD-JWT without KB-JWT: issuer_jwt~disc1~disc2~
        val sdJwtWithDisclosures = buildString {
            append(parsed.issuerJwt)
            append("~")
            for (disc in selectedDisclosures) {
                append(disc.encoded)
                append("~")
            }
        }

        // Create KB-JWT
        val kbJwt = KeyBindingJwt.create(
            sdJwtWithDisclosures = sdJwtWithDisclosures,
            audience = audience,
            nonce = nonce,
            algorithm = algorithm,
            signer = signer,
            issuedAt = issuedAt
        )

        return "$sdJwtWithDisclosures$kbJwt"
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.VpTokenBuilderTest" 2>&1 | tail -5`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/VpTokenBuilder.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/VpTokenBuilderTest.kt
git commit -m "feat: add VpTokenBuilder for SD-JWT VP presentations"
```

---

### Task 9: PresentationSubmission

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/PresentationSubmission.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/PresentationSubmissionTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class PresentationSubmissionTest {

    @Test
    fun toJsonContainsAllFields() {
        val submission = PresentationSubmission(
            id = "sub-123",
            definitionId = "pd-1",
            descriptorMap = listOf(
                DescriptorMapEntry(id = "id-1", format = "vc+sd-jwt", path = "$")
            )
        )
        val jsonStr = submission.toJson()
        val parsed = Json.parseToJsonElement(jsonStr)
        assertThat(jsonStr).contains("\"id\":\"sub-123\"")
        assertThat(jsonStr).contains("\"definition_id\":\"pd-1\"")
        assertThat(jsonStr).contains("\"descriptor_map\"")
        assertThat(jsonStr).contains("\"format\":\"vc+sd-jwt\"")
        assertThat(jsonStr).contains("\"path\":\"$\"")
    }

    @Test
    fun generatesUuidIdWhenNotProvided() {
        val submission = PresentationSubmission.create("pd-1", listOf("id-1"))
        val jsonStr = submission.toJson()
        assertThat(submission.id).isNotEmpty()
        assertThat(submission.id.length).isEqualTo(36) // UUID format
        assertThat(jsonStr).contains("\"definition_id\":\"pd-1\"")
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.PresentationSubmissionTest" 2>&1 | tail -5`

**Step 3: Implement**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class PresentationSubmission(
    val id: String,
    @SerialName("definition_id") val definitionId: String,
    @SerialName("descriptor_map") val descriptorMap: List<DescriptorMapEntry>
) {
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { encodeDefaults = true }

        fun create(definitionId: String, descriptorIds: List<String>): PresentationSubmission {
            return PresentationSubmission(
                id = UUID.randomUUID().toString(),
                definitionId = definitionId,
                descriptorMap = descriptorIds.map { descId ->
                    DescriptorMapEntry(id = descId, format = "vc+sd-jwt", path = "$")
                }
            )
        }
    }
}

@Serializable
data class DescriptorMapEntry(
    val id: String,
    val format: String,
    val path: String
)
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.PresentationSubmissionTest" 2>&1 | tail -5`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/PresentationSubmission.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/PresentationSubmissionTest.kt
git commit -m "feat: add PresentationSubmission model with JSON serialization"
```

---

### Task 10: OpenId4VpHandler (orchestrator)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpHandler.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpHandlerTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import my.ssdid.wallet.domain.vault.Vault
import org.junit.Before
import org.junit.Test

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
    fun processRequestByValueWithPd() {
        val pd = """{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"IdentityCredential"}}]}}]}"""
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n-1&response_mode=direct_post&presentation_definition=${java.net.URLEncoder.encode(pd, "UTF-8")}"

        coEvery { vault.listCredentials() } returns listOf(mockk()) // not used for SD-JWT
        coEvery { vault.listStoredSdJwtVcs() } returns listOf(testCredential)

        val result = handler.processRequest(uri)
        assertThat(result.isSuccess).isTrue()
        val review = result.getOrThrow()
        assertThat(review.authRequest.clientId).isEqualTo("https://v.example.com")
        assertThat(review.matches).hasSize(1)
    }

    @Test
    fun processRequestByReference() {
        val requestJson = """{"client_id":"https://v.example.com","response_uri":"https://v.example.com/cb","nonce":"n-1","response_mode":"direct_post","presentation_definition":{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"IdentityCredential"}}]}}]}}"""
        every { transport.fetchRequestObject("https://v.example.com/request/123") } returns requestJson
        coEvery { vault.listStoredSdJwtVcs() } returns listOf(testCredential)

        val uri = "openid4vp://?client_id=https://v.example.com&request_uri=https://v.example.com/request/123"
        val result = handler.processRequest(uri)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun processRequestNoMatchPostsError() {
        val pd = """{"id":"pd-1","input_descriptors":[{"id":"id-1","format":{"vc+sd-jwt":{}},"constraints":{"fields":[{"path":["$.vct"],"filter":{"const":"DriverLicense"}}]}}]}"""
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n-1&response_mode=direct_post&presentation_definition=${java.net.URLEncoder.encode(pd, "UTF-8")}"
        coEvery { vault.listStoredSdJwtVcs() } returns listOf(testCredential)

        val result = handler.processRequest(uri)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(NoMatchingCredentialsException::class.java)
        verify { transport.postError("https://v.example.com/cb", "access_denied", null) }
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.OpenId4VpHandlerTest" 2>&1 | tail -5`

**Step 3: Implement**

```kotlin
package my.ssdid.wallet.domain.oid4vp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import my.ssdid.wallet.domain.vault.Vault

class NoMatchingCredentialsException(message: String) : Exception(message)

data class OfferReviewResult(
    val authRequest: AuthorizationRequest,
    val matches: List<MatchResult>
)

class OpenId4VpHandler(
    private val transport: OpenId4VpTransport,
    private val peMatcher: PresentationDefinitionMatcher,
    private val dcqlMatcher: DcqlMatcher,
    private val vault: Vault
) {

    fun processRequest(uri: String): Result<OfferReviewResult> = runCatching {
        val parsed = AuthorizationRequest.parse(uri).getOrThrow()

        val authRequest = if (parsed.requestUri != null) {
            val json = transport.fetchRequestObject(parsed.requestUri)
            AuthorizationRequest.parseJson(json).getOrThrow()
        } else {
            parsed
        }

        val storedVcs = runBlocking { vault.listStoredSdJwtVcs() }

        val matches = when {
            authRequest.presentationDefinition != null ->
                peMatcher.match(authRequest.presentationDefinition, storedVcs)
            authRequest.dcqlQuery != null ->
                dcqlMatcher.match(authRequest.dcqlQuery, storedVcs)
            else -> emptyList()
        }

        if (matches.isEmpty()) {
            authRequest.responseUri?.let { responseUri ->
                runCatching { transport.postError(responseUri, "access_denied", authRequest.state) }
            }
            throw NoMatchingCredentialsException("No stored credentials match the request")
        }

        OfferReviewResult(authRequest, matches)
    }

    fun submitPresentation(
        authRequest: AuthorizationRequest,
        matchResult: MatchResult,
        selectedClaims: List<String>,
        algorithm: String,
        signer: (ByteArray) -> ByteArray
    ): Result<Unit> = runCatching {
        val responseUri = authRequest.responseUri
            ?: throw IllegalStateException("No response_uri in authorization request")
        val nonce = authRequest.nonce
            ?: throw IllegalStateException("No nonce in authorization request")

        val vpToken = VpTokenBuilder.build(
            storedSdJwtVc = matchResult.credential,
            selectedClaims = selectedClaims,
            audience = authRequest.clientId,
            nonce = nonce,
            algorithm = algorithm,
            signer = signer
        )

        val definitionId = authRequest.presentationDefinition?.get("id")
            ?.let { it.toString().trim('"') }
            ?: throw IllegalStateException("Missing presentation_definition id")

        val submission = PresentationSubmission.create(
            definitionId = definitionId,
            descriptorIds = listOf(matchResult.descriptorId)
        )

        transport.postVpResponse(responseUri, vpToken, submission.toJson(), authRequest.state)
    }

    private fun <T> runBlocking(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }
}
```

Note: Add `suspend fun listStoredSdJwtVcs(): List<StoredSdJwtVc>` to `Vault.kt` interface.

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vp.OpenId4VpHandlerTest" 2>&1 | tail -5`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpHandler.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vp/OpenId4VpHandlerTest.kt \
        android/app/src/main/java/my/ssdid/wallet/domain/vault/Vault.kt
git commit -m "feat: add OpenId4VpHandler orchestrator with credential matching"
```

---

### Task 11: PresentationRequestViewModel + Screen + DI wiring

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/presentation/PresentationRequestViewModel.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/presentation/PresentationRequestScreen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/feature/presentation/PresentationRequestViewModelTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.feature.presentation

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import my.ssdid.wallet.domain.oid4vp.*
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import my.ssdid.wallet.domain.vault.Vault
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresentationRequestViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var handler: OpenId4VpHandler
    private lateinit var vault: Vault
    private lateinit var viewModel: PresentationRequestViewModel

    private val testCred = StoredSdJwtVc(
        id = "vc-1", compact = "eyJ.eyJ.sig~d1~", issuer = "did:ssdid:i",
        subject = "did:ssdid:h", type = "IdCred",
        claims = mapOf("name" to "Ahmad"), disclosableClaims = listOf("name"),
        issuedAt = 1700000000L
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        handler = mockk()
        vault = mockk()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsLoading() {
        viewModel = PresentationRequestViewModel(handler, vault)
        assertThat(viewModel.state.value).isInstanceOf(PresentationRequestViewModel.UiState.Loading::class.java)
    }

    @Test
    fun toggleClaimUpdatesState() {
        viewModel = PresentationRequestViewModel(handler, vault)
        val match = MatchResult(testCred, "id-1", listOf("name"), listOf("email"))
        val authReq = AuthorizationRequest(clientId = "https://v.example.com", nonce = "n")

        viewModel.setReviewResult(OfferReviewResult(authReq, listOf(match)))

        val state = viewModel.state.value as PresentationRequestViewModel.UiState.CredentialMatch
        assertThat(state.claims.find { it.name == "name" }!!.required).isTrue()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.feature.presentation.PresentationRequestViewModelTest" 2>&1 | tail -5`

**Step 3: Implement ViewModel**

```kotlin
package my.ssdid.wallet.feature.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.ssdid.wallet.domain.oid4vp.*
import my.ssdid.wallet.domain.vault.Vault
import javax.inject.Inject

@HiltViewModel
class PresentationRequestViewModel @Inject constructor(
    private val handler: OpenId4VpHandler,
    private val vault: Vault
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class CredentialMatch(
            val verifierName: String,
            val claims: List<ClaimItem>,
            val matchResult: MatchResult,
            val authRequest: AuthorizationRequest
        ) : UiState()
        object Submitting : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }

    data class ClaimItem(
        val name: String,
        val value: String,
        val required: Boolean,
        val selected: Boolean
    )

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun processRequest(rawUri: String) {
        viewModelScope.launch {
            val result = handler.processRequest(rawUri)
            result.fold(
                onSuccess = { review -> setReviewResult(review) },
                onFailure = { e -> _state.value = UiState.Error(e.message ?: "Unknown error") }
            )
        }
    }

    fun setReviewResult(review: OfferReviewResult) {
        val match = review.matches.first()
        val claims = (match.requiredClaims.map { name ->
            ClaimItem(name, match.credential.claims[name] ?: "", required = true, selected = true)
        } + match.optionalClaims.map { name ->
            ClaimItem(name, match.credential.claims[name] ?: "", required = false, selected = false)
        })
        _state.value = UiState.CredentialMatch(
            verifierName = review.authRequest.clientId,
            claims = claims,
            matchResult = match,
            authRequest = review.authRequest
        )
    }

    fun toggleClaim(claimName: String) {
        _state.update { current ->
            if (current !is UiState.CredentialMatch) return@update current
            current.copy(claims = current.claims.map { item ->
                if (item.name == claimName && !item.required) item.copy(selected = !item.selected)
                else item
            })
        }
    }

    fun approve() {
        var captured: UiState.CredentialMatch? = null
        _state.update { current ->
            if (current !is UiState.CredentialMatch) return@update current
            captured = current
            UiState.Submitting
        }
        val current = captured ?: return

        viewModelScope.launch {
            val selectedClaims = current.claims.filter { it.selected }.map { it.name }
            val identity = vault.listIdentities().firstOrNull() ?: run {
                _state.value = UiState.Error("No identity available")
                return@launch
            }

            val result = handler.submitPresentation(
                authRequest = current.authRequest,
                matchResult = current.matchResult,
                selectedClaims = selectedClaims,
                algorithm = identity.algorithm.toJwaName(),
                signer = { data ->
                    kotlinx.coroutines.runBlocking { vault.sign(identity.keyId, data).getOrThrow() }
                }
            )

            result.fold(
                onSuccess = { _state.value = UiState.Success },
                onFailure = { e -> _state.value = UiState.Error(e.message ?: "Submission failed") }
            )
        }
    }

    fun decline() {
        _state.value = UiState.Error("Declined by user")
    }
}
```

**Step 3b: Add Screen route**

Add to `Screen.kt`:

```kotlin
object PresentationRequest : Screen("presentation_request?rawUri={rawUri}") {
    fun createRoute(rawUri: String) = "presentation_request?rawUri=${Uri.encode(rawUri)}"
}
```

**Step 3c: Create minimal PresentationRequestScreen.kt**

```kotlin
package my.ssdid.wallet.feature.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun PresentationRequestScreen(
    onBack: () -> Unit = {},
    onComplete: () -> Unit = {},
    viewModel: PresentationRequestViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is PresentationRequestViewModel.UiState.Success) onComplete()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (val s = state) {
            is PresentationRequestViewModel.UiState.Loading -> {
                CircularProgressIndicator()
            }
            is PresentationRequestViewModel.UiState.CredentialMatch -> {
                Text("Verifier: ${s.verifierName}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Text("Requested claims:", style = MaterialTheme.typography.bodyLarge)
                s.claims.forEach { claim ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Checkbox(
                            checked = claim.selected,
                            onCheckedChange = { if (!claim.required) viewModel.toggleClaim(claim.name) },
                            enabled = !claim.required
                        )
                        Column {
                            Text(claim.name)
                            Text(claim.value, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.decline(); onBack() }, modifier = Modifier.weight(1f)) {
                        Text("Decline")
                    }
                    Button(onClick = { viewModel.approve() }, modifier = Modifier.weight(1f)) {
                        Text("Share")
                    }
                }
            }
            is PresentationRequestViewModel.UiState.Submitting -> {
                CircularProgressIndicator()
                Text("Submitting...")
            }
            is PresentationRequestViewModel.UiState.Success -> {
                Text("Presentation submitted successfully")
            }
            is PresentationRequestViewModel.UiState.Error -> {
                Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                Button(onClick = onBack) { Text("Back") }
            }
        }
    }
}
```

**Step 3d: Wire into NavGraph and AppModule**

Add to `NavGraph.kt`:

```kotlin
composable(
    Screen.PresentationRequest.route,
    arguments = listOf(navArgument("rawUri") { type = NavType.StringType; defaultValue = "" })
) {
    PresentationRequestScreen(
        onBack = { navController.popBackStack() },
        onComplete = { navController.popBackStack(Screen.WalletHome.route, inclusive = false) }
    )
}
```

Add to `AppModule.kt`:

```kotlin
@Provides
@Singleton
fun provideOpenId4VpTransport(okHttpClient: OkHttpClient): OpenId4VpTransport =
    OpenId4VpTransport(okHttpClient)

@Provides
@Singleton
fun provideOpenId4VpHandler(
    transport: OpenId4VpTransport,
    vault: Vault
): OpenId4VpHandler = OpenId4VpHandler(
    transport, PresentationDefinitionMatcher(), DcqlMatcher(), vault
)
```

**Step 4: Run all tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.feature.presentation.PresentationRequestViewModelTest" 2>&1 | tail -5`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/feature/presentation/ \
        android/app/src/main/java/my/ssdid/wallet/ui/navigation/Screen.kt \
        android/app/src/main/java/my/ssdid/wallet/ui/navigation/NavGraph.kt \
        android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt \
        android/app/src/test/java/my/ssdid/wallet/feature/presentation/
git commit -m "feat: add PresentationRequestScreen with ViewModel and DI wiring"
```

---

## Phase C: OpenID4VP iOS (Tasks 12-14)

### Task 12: iOS OpenID4VP domain layer

**Files:**
- Create: `ios/SsdidWallet/Domain/OpenId4Vp/AuthorizationRequest.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vp/OpenId4VpTransport.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vp/PresentationDefinitionMatcher.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vp/DcqlMatcher.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vp/VpTokenBuilder.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vp/PresentationSubmission.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vp/OpenId4VpHandler.swift`

Mirror all Android domain classes 1:1 in Swift. Use `URLSession` instead of OkHttp. Use `JSONSerialization`/`Codable` instead of kotlinx-serialization. Match validation logic exactly.

**Step 1-3: Implement all files**

Each Swift file mirrors its Kotlin counterpart. Key differences:
- `AuthorizationRequest.parse()` uses `URLComponents` instead of `Uri`
- `OpenId4VpTransport` uses `URLSession.shared.data(for:)` with `async/await`
- Form-encoded POST uses `"application/x-www-form-urlencoded"` content type with manual URL encoding
- `VpTokenBuilder` calls the existing Swift `KeyBindingJwt` equivalent
- Error types use Swift `enum` with `Error` conformance

**Step 4: Build to verify compilation**

Run: `cd ios && xcodebuild build -scheme SsdidWallet -destination 'platform=iOS Simulator,name=iPhone 16' 2>&1 | tail -5`

**Step 5: Commit**

```bash
git add ios/SsdidWallet/Domain/OpenId4Vp/
git commit -m "feat(ios): add OpenID4VP domain layer mirroring Android"
```

---

### Task 13: iOS OpenID4VP UI

**Files:**
- Create: `ios/SsdidWallet/Feature/Presentation/PresentationRequestScreen.swift`
- Create: `ios/SsdidWallet/Feature/Presentation/PresentationRequestViewModel.swift`
- Modify: `ios/SsdidWallet/UI/Navigation/AppRouter.swift` (add route)
- Modify: `ios/SsdidWallet/UI/Navigation/ContentView.swift` (add destination)

Mirror the Android `PresentationRequestScreen` in SwiftUI. Use `@Observable` ViewModel pattern matching the existing iOS codebase style.

**Step 5: Commit**

```bash
git add ios/SsdidWallet/Feature/Presentation/ ios/SsdidWallet/UI/Navigation/
git commit -m "feat(ios): add PresentationRequestScreen with ViewModel"
```

---

### Task 14: iOS OpenID4VP tests

**Files:**
- Create: `ios/SsdidWalletTests/Domain/OpenId4Vp/AuthorizationRequestTests.swift`
- Create: `ios/SsdidWalletTests/Domain/OpenId4Vp/PresentationDefinitionMatcherTests.swift`
- Create: `ios/SsdidWalletTests/Domain/OpenId4Vp/DcqlMatcherTests.swift`
- Create: `ios/SsdidWalletTests/Domain/OpenId4Vp/VpTokenBuilderTests.swift`
- Create: `ios/SsdidWalletTests/Domain/OpenId4Vp/PresentationSubmissionTests.swift`

Mirror Android test coverage. Use XCTest.

**Step 5: Commit**

```bash
git add ios/SsdidWalletTests/Domain/OpenId4Vp/
git commit -m "test(ios): add OpenID4VP test suite"
```

---

## Phase D: OpenID4VCI Domain Layer (Android)

### Task 15: CredentialOffer model + parser

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/CredentialOffer.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/CredentialOfferTest.kt`

**Step 1: Write the failing test**

```kotlin
package my.ssdid.wallet.domain.oid4vci

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CredentialOfferTest {

    @Test
    fun parsePreAuthorizedCodeOffer() {
        val json = """
            {
                "credential_issuer": "https://issuer.example.com",
                "credential_configuration_ids": ["UnivDegree"],
                "grants": {
                    "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                        "pre-authorized_code": "abc123"
                    }
                }
            }
        """
        val result = CredentialOffer.parse(json)
        assertThat(result.isSuccess).isTrue()
        val offer = result.getOrThrow()
        assertThat(offer.credentialIssuer).isEqualTo("https://issuer.example.com")
        assertThat(offer.credentialConfigurationIds).containsExactly("UnivDegree")
        assertThat(offer.preAuthorizedCode).isEqualTo("abc123")
        assertThat(offer.txCode).isNull()
        assertThat(offer.authorizationCodeGrant).isFalse()
    }

    @Test
    fun parseOfferWithTxCode() {
        val json = """
            {
                "credential_issuer": "https://issuer.example.com",
                "credential_configuration_ids": ["IdCard"],
                "grants": {
                    "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                        "pre-authorized_code": "xyz789",
                        "tx_code": {
                            "input_mode": "numeric",
                            "length": 6,
                            "description": "Enter PIN from email"
                        }
                    }
                }
            }
        """
        val result = CredentialOffer.parse(json)
        assertThat(result.isSuccess).isTrue()
        val offer = result.getOrThrow()
        assertThat(offer.txCode).isNotNull()
        assertThat(offer.txCode!!.inputMode).isEqualTo("numeric")
        assertThat(offer.txCode!!.length).isEqualTo(6)
    }

    @Test
    fun parseAuthorizationCodeGrant() {
        val json = """
            {
                "credential_issuer": "https://issuer.example.com",
                "credential_configuration_ids": ["Diploma"],
                "grants": {
                    "authorization_code": {
                        "issuer_state": "state-abc"
                    }
                }
            }
        """
        val result = CredentialOffer.parse(json)
        assertThat(result.isSuccess).isTrue()
        val offer = result.getOrThrow()
        assertThat(offer.authorizationCodeGrant).isTrue()
        assertThat(offer.issuerState).isEqualTo("state-abc")
        assertThat(offer.preAuthorizedCode).isNull()
    }

    @Test
    fun rejectHttpIssuer() {
        val json = """{"credential_issuer":"http://bad.com","credential_configuration_ids":["x"],"grants":{"authorization_code":{}}}"""
        val result = CredentialOffer.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()!!.message).contains("HTTPS")
    }

    @Test
    fun rejectEmptyConfigIds() {
        val json = """{"credential_issuer":"https://issuer.example.com","credential_configuration_ids":[],"grants":{"authorization_code":{}}}"""
        val result = CredentialOffer.parse(json)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun rejectMissingGrants() {
        val json = """{"credential_issuer":"https://issuer.example.com","credential_configuration_ids":["x"]}"""
        val result = CredentialOffer.parse(json)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun parseFromUri() {
        val offerJson = """{"credential_issuer":"https://issuer.example.com","credential_configuration_ids":["x"],"grants":{"authorization_code":{}}}"""
        val result = CredentialOffer.parseFromUri("openid-credential-offer://?credential_offer=${java.net.URLEncoder.encode(offerJson, "UTF-8")}")
        assertThat(result.isSuccess).isTrue()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vci.CredentialOfferTest" 2>&1 | tail -5`

**Step 3: Implement**

```kotlin
package my.ssdid.wallet.domain.oid4vci

import android.net.Uri
import kotlinx.serialization.json.*

data class TxCodeRequirement(
    val inputMode: String,
    val length: Int,
    val description: String?
)

data class CredentialOffer(
    val credentialIssuer: String,
    val credentialConfigurationIds: List<String>,
    val preAuthorizedCode: String? = null,
    val txCode: TxCodeRequirement? = null,
    val authorizationCodeGrant: Boolean = false,
    val issuerState: String? = null
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(jsonString: String): Result<CredentialOffer> = runCatching {
            val obj = json.parseToJsonElement(jsonString).jsonObject

            val issuer = obj["credential_issuer"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing credential_issuer")
            require(issuer.startsWith("https://")) { "credential_issuer must be HTTPS: $issuer" }

            val configIds = obj["credential_configuration_ids"]?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?: throw IllegalArgumentException("Missing credential_configuration_ids")
            require(configIds.isNotEmpty()) { "credential_configuration_ids must not be empty" }

            val grants = obj["grants"]?.jsonObject
                ?: throw IllegalArgumentException("Missing grants")

            val preAuthGrant = grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"]?.jsonObject
            val authCodeGrant = grants["authorization_code"]?.jsonObject

            require(preAuthGrant != null || authCodeGrant != null) { "Must have at least one grant type" }

            val preAuthCode = preAuthGrant?.get("pre-authorized_code")?.jsonPrimitive?.contentOrNull
            val txCodeObj = preAuthGrant?.get("tx_code")?.jsonObject
            val txCode = txCodeObj?.let {
                TxCodeRequirement(
                    inputMode = it["input_mode"]?.jsonPrimitive?.content ?: "numeric",
                    length = it["length"]?.jsonPrimitive?.int ?: 6,
                    description = it["description"]?.jsonPrimitive?.contentOrNull
                )
            }

            val issuerState = authCodeGrant?.get("issuer_state")?.jsonPrimitive?.contentOrNull

            CredentialOffer(
                credentialIssuer = issuer,
                credentialConfigurationIds = configIds,
                preAuthorizedCode = preAuthCode,
                txCode = txCode,
                authorizationCodeGrant = authCodeGrant != null,
                issuerState = issuerState
            )
        }

        fun parseFromUri(uriString: String): Result<CredentialOffer> = runCatching {
            val uri = Uri.parse(uriString)
            val offerJson = uri.getQueryParameter("credential_offer")
            val offerUri = uri.getQueryParameter("credential_offer_uri")

            when {
                offerJson != null -> parse(offerJson).getOrThrow()
                offerUri != null -> {
                    require(offerUri.startsWith("https://")) { "credential_offer_uri must be HTTPS" }
                    throw UnsupportedOperationException("By-reference offers require network fetch — use OpenId4VciHandler")
                }
                else -> throw IllegalArgumentException("Missing credential_offer or credential_offer_uri")
            }
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "my.ssdid.wallet.domain.oid4vci.CredentialOfferTest" 2>&1 | tail -5`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/CredentialOffer.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/CredentialOfferTest.kt
git commit -m "feat: add CredentialOffer parser with pre-auth and auth code grant support"
```

---

### Task 16: IssuerMetadata model + resolver

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/IssuerMetadata.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/IssuerMetadataResolver.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/IssuerMetadataResolverTest.kt`

Fetches `/.well-known/openid-credential-issuer` and `/.well-known/oauth-authorization-server`. Parses `credential_endpoint`, `credential_configurations_supported`, `token_endpoint`, `authorization_endpoint`. Uses OkHttp with `.use{}`. In-memory cache with `ConcurrentHashMap`. Tests use MockWebServer.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/IssuerMetadata.kt \
        android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/IssuerMetadataResolver.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/IssuerMetadataResolverTest.kt
git commit -m "feat: add IssuerMetadataResolver with .well-known endpoint fetching"
```

---

### Task 17: TokenClient

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/TokenClient.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/TokenClientTest.kt`

Two methods: `exchangePreAuthorizedCode(tokenEndpoint, preAuthCode, txCode?)` and `exchangeAuthorizationCode(tokenEndpoint, code, codeVerifier, redirectUri)`. Form-encoded POST. Returns `TokenResponse(accessToken, tokenType, cNonce, cNonceExpiresIn)`. Tests use MockWebServer.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/TokenClient.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/TokenClientTest.kt
git commit -m "feat: add TokenClient for pre-auth and auth code exchange"
```

---

### Task 18: NonceManager + ProofJwtBuilder

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/NonceManager.kt`
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/ProofJwtBuilder.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/NonceManagerTest.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/ProofJwtBuilderTest.kt`

`NonceManager`: thread-safe `c_nonce` storage with expiry tracking. `update(nonce, expiresIn)`, `current()`, `isExpired()`.

`ProofJwtBuilder`: builds JWT with header `{typ: "openid4vci-proof+jwt", alg: Algorithm.toJwaName(), kid: keyId}` and payload `{iss: walletDid, aud: issuerUrl, iat: now, nonce: cNonce}`. Signs with wallet key. Mirrors `KeyBindingJwt.create()` pattern for base64url encoding.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/NonceManager.kt \
        android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/ProofJwtBuilder.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/NonceManagerTest.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/ProofJwtBuilderTest.kt
git commit -m "feat: add NonceManager and ProofJwtBuilder for OpenID4VCI proof of possession"
```

---

### Task 19: OpenId4VciTransport

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/OpenId4VciTransport.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/OpenId4VciTransportTest.kt`

Methods: `fetchIssuerMetadata(issuerUrl)`, `fetchAuthServerMetadata(authServerUrl)`, `postTokenRequest(tokenEndpoint, formBody)`, `postCredentialRequest(credentialEndpoint, accessToken, requestBody)`, `postDeferredRequest(deferredEndpoint, accessToken, transactionId)`, `fetchCredentialOffer(offerUri)`. All use OkHttp with `.use{}`. Credential endpoint uses `Authorization: Bearer` header. Tests use MockWebServer.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/OpenId4VciTransport.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/OpenId4VciTransportTest.kt
git commit -m "feat: add OpenId4VciTransport for issuer metadata, token, and credential endpoints"
```

---

### Task 20: OpenId4VciHandler (orchestrator)

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/OpenId4VciHandler.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/OpenId4VciHandlerTest.kt`

`processOffer(uri)`: parse credential offer → resolve issuer metadata → return `CredentialOfferReview(offer, metadata, displayInfo)`.

`acceptOffer(offer, metadata, selectedConfigId, txCode, identity)`: token exchange (pre-auth or auth code) → build proof JWT → POST credential request → handle `invalid_proof` (extract fresh `c_nonce`, retry once) → parse received SD-JWT VC → verify issuer signature → store in vault.

`pollDeferred(deferredEndpoint, accessToken, transactionId)`: exponential backoff (5s, 10s, 20s..., max 5min), max 12 attempts.

Tests mock transport, vault, and verify the orchestration flow including `invalid_proof` retry.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/domain/oid4vci/OpenId4VciHandler.kt \
        android/app/src/test/java/my/ssdid/wallet/domain/oid4vci/OpenId4VciHandlerTest.kt
git commit -m "feat: add OpenId4VciHandler orchestrator with proof retry and deferred polling"
```

---

### Task 21: CredentialOfferViewModel + UI update + DI wiring

**Files:**
- Create: `android/app/src/main/java/my/ssdid/wallet/feature/credentials/CredentialOfferViewModel.kt`
- Modify: `android/app/src/main/java/my/ssdid/wallet/feature/credentials/CredentialOfferScreen.kt` (replace stub)
- Modify: `android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt`
- Create: `android/app/src/test/java/my/ssdid/wallet/feature/credentials/CredentialOfferViewModelTest.kt`

State machine: `Idle → Parsing → FetchingMetadata → ReviewingOffer → (PinEntry if tx_code) → ExchangingToken → RequestingCredential → Success/Deferred/Error`.

DI: provide `OpenId4VciHandler`, `OpenId4VciTransport`, `IssuerMetadataResolver`, `TokenClient` via Hilt.

**Step 5: Commit**

```bash
git add android/app/src/main/java/my/ssdid/wallet/feature/credentials/ \
        android/app/src/main/java/my/ssdid/wallet/di/AppModule.kt \
        android/app/src/test/java/my/ssdid/wallet/feature/credentials/
git commit -m "feat: add CredentialOfferViewModel with issuance state machine and DI wiring"
```

---

## Phase E: OpenID4VCI iOS (Tasks 22-23)

### Task 22: iOS OpenID4VCI domain layer

**Files:**
- Create: `ios/SsdidWallet/Domain/OpenId4Vci/CredentialOffer.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vci/IssuerMetadata.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vci/IssuerMetadataResolver.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vci/TokenClient.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vci/NonceManager.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vci/ProofJwtBuilder.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vci/OpenId4VciTransport.swift`
- Create: `ios/SsdidWallet/Domain/OpenId4Vci/OpenId4VciHandler.swift`

Mirror all Android domain classes 1:1 in Swift. Use `URLSession` + `async/await`. Use `Codable`/`JSONSerialization`.

**Step 5: Commit**

```bash
git add ios/SsdidWallet/Domain/OpenId4Vci/
git commit -m "feat(ios): add OpenID4VCI domain layer mirroring Android"
```

---

### Task 23: iOS OpenID4VCI UI + tests

**Files:**
- Modify: `ios/SsdidWallet/Feature/Credentials/CredentialOfferScreen.swift` (or create ViewModel)
- Create: `ios/SsdidWallet/Feature/Credentials/CredentialOfferViewModel.swift`
- Create: `ios/SsdidWalletTests/Domain/OpenId4Vci/CredentialOfferTests.swift`
- Create: `ios/SsdidWalletTests/Domain/OpenId4Vci/IssuerMetadataResolverTests.swift`
- Create: `ios/SsdidWalletTests/Domain/OpenId4Vci/TokenClientTests.swift`
- Create: `ios/SsdidWalletTests/Domain/OpenId4Vci/ProofJwtBuilderTests.swift`

**Step 5: Commit**

```bash
git add ios/SsdidWallet/Feature/Credentials/ ios/SsdidWalletTests/Domain/OpenId4Vci/
git commit -m "feat(ios): add OpenID4VCI UI and test suite"
```

---

## Summary

| Phase | Tasks | Description |
|-------|-------|-------------|
| A: Shared | 1-3 | Algorithm JWA mapping, deep link schemes (Android + iOS) |
| B: VP Android | 4-11 | AuthorizationRequest, Transport, Matchers, VpToken, Handler, UI |
| C: VP iOS | 12-14 | Mirror Android VP layer in Swift |
| D: VCI Android | 15-21 | CredentialOffer, Metadata, Token, Nonce, Proof, Handler, UI |
| E: VCI iOS | 22-23 | Mirror Android VCI layer in Swift |

**Total: 23 tasks, ~45 new files, ~15 modified files**
