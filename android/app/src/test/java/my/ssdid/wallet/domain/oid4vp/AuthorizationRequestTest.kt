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
        assertThat(req.responseUri).isNull()
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

    @Test
    fun rejectBothPdAndDcql() {
        val pd = """{"id":"pd-1","input_descriptors":[]}"""
        val dcql = """{"credentials":[]}"""
        val uri = "openid4vp://?response_type=vp_token&client_id=https://v.example.com&response_uri=https://v.example.com/cb&nonce=n&response_mode=direct_post&presentation_definition=${Uri.encode(pd)}&dcql_query=${Uri.encode(dcql)}"
        val result = AuthorizationRequest.parse(uri)
        assertThat(result.isFailure).isTrue()
    }

    // --- G13: parseJson error paths ---

    @Test
    fun parseJsonRejectsMissingClientId() {
        val json = """{"response_uri":"https://v.example.com/cb","nonce":"n","response_mode":"direct_post","presentation_definition":{"id":"pd-1"}}"""
        val result = AuthorizationRequest.parseJson(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("client_id")
    }

    @Test
    fun parseJsonRejectsNonDirectPostMode() {
        val json = """{"client_id":"c","response_uri":"https://v.example.com/cb","nonce":"n","response_mode":"fragment","presentation_definition":{"id":"pd-1"}}"""
        val result = AuthorizationRequest.parseJson(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("direct_post")
    }

    @Test
    fun parseJsonRejectsMissingResponseUri() {
        val json = """{"client_id":"c","nonce":"n","response_mode":"direct_post","presentation_definition":{"id":"pd-1"}}"""
        val result = AuthorizationRequest.parseJson(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("response_uri")
    }

    @Test
    fun parseJsonRejectsHttpResponseUri() {
        val json = """{"client_id":"c","response_uri":"http://insecure.com/cb","nonce":"n","response_mode":"direct_post","presentation_definition":{"id":"pd-1"}}"""
        val result = AuthorizationRequest.parseJson(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("HTTPS")
    }

    @Test
    fun parseJsonRejectsMissingNonce() {
        val json = """{"client_id":"c","response_uri":"https://v.example.com/cb","response_mode":"direct_post","presentation_definition":{"id":"pd-1"}}"""
        val result = AuthorizationRequest.parseJson(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("nonce")
    }

    @Test
    fun parseJsonRejectsMissingQueryType() {
        val json = """{"client_id":"c","response_uri":"https://v.example.com/cb","nonce":"n","response_mode":"direct_post"}"""
        val result = AuthorizationRequest.parseJson(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("presentation_definition")
    }

    @Test
    fun parseJsonAcceptsDcqlQuery() {
        val json = """{"client_id":"c","response_uri":"https://v.example.com/cb","nonce":"n","response_mode":"direct_post","dcql_query":{"credentials":[]}}"""
        val result = AuthorizationRequest.parseJson(json)
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().dcqlQuery).isNotNull()
    }

    @Test
    fun parseJsonRequestObject() {
        val json = """{"client_id":"https://v.example.com","response_uri":"https://v.example.com/cb","nonce":"n-1","response_mode":"direct_post","presentation_definition":{"id":"pd-1","input_descriptors":[]}}"""
        val result = AuthorizationRequest.parseJson(json)
        assertThat(result.isSuccess).isTrue()
        val req = result.getOrThrow()
        assertThat(req.clientId).isEqualTo("https://v.example.com")
        assertThat(req.nonce).isEqualTo("n-1")
    }
}
