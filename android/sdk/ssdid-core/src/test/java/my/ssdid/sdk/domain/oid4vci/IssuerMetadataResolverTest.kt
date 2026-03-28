package my.ssdid.sdk.domain.oid4vci

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class IssuerMetadataResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var resolver: IssuerMetadataResolver

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        resolver = IssuerMetadataResolver(OkHttpClient())
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun resolveIssuerMetadata() {
        val issuerMeta = """
            {
                "credential_endpoint": "${server.url("/credential")}",
                "credential_configurations_supported": {
                    "UnivDegree": {"format": "vc+sd-jwt"}
                }
            }
        """.trimIndent()
        val authMeta = """
            {
                "token_endpoint": "${server.url("/token")}",
                "authorization_endpoint": "${server.url("/authorize")}"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(issuerMeta).setResponseCode(200))
        server.enqueue(MockResponse().setBody(authMeta).setResponseCode(200))

        val result = resolver.resolve(server.url("/").toString())
        assertThat(result.isSuccess).isTrue()
        val meta = result.getOrThrow()
        assertThat(meta.credentialEndpoint).contains("/credential")
        assertThat(meta.tokenEndpoint).contains("/token")
        assertThat(meta.authorizationEndpoint).contains("/authorize")
        assertThat(meta.credentialConfigurationsSupported).containsKey("UnivDegree")
    }

    @Test
    fun cachesResolvedMetadata() {
        val issuerMeta = """
            {
                "credential_endpoint": "${server.url("/credential")}",
                "credential_configurations_supported": {}
            }
        """.trimIndent()
        val authMeta = """{"token_endpoint": "${server.url("/token")}"}"""
        server.enqueue(MockResponse().setBody(issuerMeta))
        server.enqueue(MockResponse().setBody(authMeta))

        resolver.resolve(server.url("/").toString())
        // Second call should use cache - no more server requests needed
        val result = resolver.resolve(server.url("/").toString())
        assertThat(result.isSuccess).isTrue()
        assertThat(server.requestCount).isEqualTo(2) // Only 2 requests (issuer + auth), not 4
    }

    @Test
    fun failsOnMissingCredentialEndpoint() {
        server.enqueue(MockResponse().setBody("""{"credential_configurations_supported":{}}"""))
        server.enqueue(MockResponse().setBody("""{"token_endpoint":"${server.url("/token")}"}"""))
        val result = resolver.resolve(server.url("/").toString())
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun failsOnHttpError() {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = resolver.resolve(server.url("/").toString())
        assertThat(result.isFailure).isTrue()
    }
}
