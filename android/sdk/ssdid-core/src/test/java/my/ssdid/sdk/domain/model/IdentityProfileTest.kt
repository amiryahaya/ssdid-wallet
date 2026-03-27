package my.ssdid.sdk.domain.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class IdentityProfileTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `identity serializes with profileName, email and emailVerified fields`() {
        val identity = Identity(
            name = "Work",
            did = "did:ssdid:abc",
            keyId = "did:ssdid:abc#key-1",
            algorithm = Algorithm.ED25519,
            publicKeyMultibase = "z6Mk...",
            createdAt = "2026-03-16T00:00:00Z",
            profileName = "Amir Yahaya",
            email = "amir@acme.com",
            emailVerified = true
        )
        val encoded = json.encodeToString(identity)
        assertThat(encoded).contains("\"profileName\":\"Amir Yahaya\"")
        assertThat(encoded).contains("\"email\":\"amir@acme.com\"")
        assertThat(encoded).contains("\"emailVerified\":true")

        val decoded = json.decodeFromString<Identity>(encoded)
        assertThat(decoded.profileName).isEqualTo("Amir Yahaya")
        assertThat(decoded.email).isEqualTo("amir@acme.com")
        assertThat(decoded.emailVerified).isTrue()
    }

    @Test
    fun `identity without profile fields deserializes with null defaults`() {
        val jsonStr = """{"name":"Work","did":"did:ssdid:abc","keyId":"did:ssdid:abc#key-1","algorithm":"ED25519","publicKeyMultibase":"z6Mk...","createdAt":"2026-03-16T00:00:00Z"}"""
        val decoded = json.decodeFromString<Identity>(jsonStr)
        assertThat(decoded.profileName).isNull()
        assertThat(decoded.email).isNull()
        assertThat(decoded.emailVerified).isFalse()
    }

    @Test
    fun `claimsMap returns profileName as name claim`() {
        val identity = Identity(
            name = "Work",
            did = "did:ssdid:abc",
            keyId = "did:ssdid:abc#key-1",
            algorithm = Algorithm.ED25519,
            publicKeyMultibase = "z6Mk...",
            createdAt = "2026-03-16T00:00:00Z",
            profileName = "Amir Yahaya",
            email = "amir@acme.com"
        )
        val claims = identity.claimsMap()
        assertThat(claims["name"]).isEqualTo("Amir Yahaya")
        assertThat(claims["email"]).isEqualTo("amir@acme.com")
    }

    @Test
    fun `claimsMap omits null fields`() {
        val identity = Identity(
            name = "Work",
            did = "did:ssdid:abc",
            keyId = "did:ssdid:abc#key-1",
            algorithm = Algorithm.ED25519,
            publicKeyMultibase = "z6Mk...",
            createdAt = "2026-03-16T00:00:00Z"
        )
        val claims = identity.claimsMap()
        assertThat(claims).doesNotContainKey("name")
        assertThat(claims).doesNotContainKey("email")
    }
}
