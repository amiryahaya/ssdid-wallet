package my.ssdid.wallet.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DidDocumentTest {
    @Test
    fun `build creates W3C compliant document`() {
        val did = Did("did:ssdid:7KmVwPq9RtXzN3Fy")
        val keyId = did.keyId(1)
        val doc = DidDocument.build(did, keyId, Algorithm.KAZ_SIGN_192, "uhaXgBZDq8R2mNvK4t")

        assertThat(doc.context).contains("https://www.w3.org/ns/did/v1")
        assertThat(doc.id).isEqualTo("did:ssdid:7KmVwPq9RtXzN3Fy")
        assertThat(doc.controller).isEqualTo(doc.id)
        assertThat(doc.verificationMethod).hasSize(1)
        assertThat(doc.verificationMethod[0].type).isEqualTo("KazSign192VerificationKey2024")
        assertThat(doc.verificationMethod[0].publicKeyMultibase).isEqualTo("uhaXgBZDq8R2mNvK4t")
        assertThat(doc.authentication).containsExactly(keyId)
        assertThat(doc.assertionMethod).containsExactly(keyId)
        assertThat(doc.capabilityInvocation).containsExactly(keyId)
    }

    @Test
    fun `build with Ed25519 uses correct W3C type`() {
        val did = Did("did:ssdid:test123")
        val keyId = did.keyId(1)
        val doc = DidDocument.build(did, keyId, Algorithm.ED25519, "uABC123")
        assertThat(doc.verificationMethod[0].type).isEqualTo("Ed25519VerificationKey2020")
    }
}
