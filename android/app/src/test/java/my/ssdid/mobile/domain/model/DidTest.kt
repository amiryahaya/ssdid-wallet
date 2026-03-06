package my.ssdid.mobile.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

class DidTest {
    @Test
    fun `generate creates valid did-ssdid format`() {
        val did = Did.generate()
        assertThat(did.value).startsWith("did:ssdid:")
        val methodSpecificId = did.value.removePrefix("did:ssdid:")
        val decoded = Base64.getUrlDecoder().decode(methodSpecificId)
        assertThat(decoded).hasLength(16)
    }

    @Test
    fun `keyId appends fragment`() {
        val did = Did("did:ssdid:7KmVwPq9RtXzN3Fy")
        assertThat(did.keyId(1)).isEqualTo("did:ssdid:7KmVwPq9RtXzN3Fy#key-1")
    }

    @Test
    fun `fromKeyId extracts DID`() {
        val keyId = "did:ssdid:7KmVwPq9RtXzN3Fy#key-1"
        val did = Did.fromKeyId(keyId)
        assertThat(did.value).isEqualTo("did:ssdid:7KmVwPq9RtXzN3Fy")
    }

    @Test
    fun `methodSpecificId strips prefix`() {
        val did = Did("did:ssdid:7KmVwPq9RtXzN3Fy")
        assertThat(did.methodSpecificId()).isEqualTo("7KmVwPq9RtXzN3Fy")
    }

    @Test
    fun `each generate produces unique DID`() {
        val did1 = Did.generate()
        val did2 = Did.generate()
        assertThat(did1.value).isNotEqualTo(did2.value)
    }
}
