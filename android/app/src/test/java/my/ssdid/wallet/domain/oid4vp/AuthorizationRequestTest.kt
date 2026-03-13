package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
