package my.ssdid.wallet.domain.oid4vp

import com.google.common.truth.Truth.assertThat
import my.ssdid.wallet.domain.mdoc.StoredMDoc
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import org.junit.Test

class CredentialRefTest {

    @Test
    fun sdJwtRefWrapsCredential() {
        val vc = StoredSdJwtVc("id", "compact", "iss", "sub", "type", emptyMap(), emptyList(), 0)
        val ref = CredentialRef.SdJwt(vc)
        assertThat(ref).isInstanceOf(CredentialRef.SdJwt::class.java)
        assertThat((ref as CredentialRef.SdJwt).credential.id).isEqualTo("id")
    }

    @Test
    fun mdocRefWrapsCredential() {
        val mdoc = StoredMDoc("id", "org.iso.18013.5.1.mDL", byteArrayOf(), "key", 0)
        val ref = CredentialRef.MDoc(mdoc)
        assertThat(ref).isInstanceOf(CredentialRef.MDoc::class.java)
        assertThat((ref as CredentialRef.MDoc).credential.docType).isEqualTo("org.iso.18013.5.1.mDL")
    }

    @Test
    fun matchResultUsesCredentialRef() {
        val vc = StoredSdJwtVc("id", "compact", "iss", "sub", "type", emptyMap(), emptyList(), 0)
        val result = MatchResult(CredentialRef.SdJwt(vc), "desc-1", listOf("name"), emptyList())
        assertThat(result.credentialRef).isInstanceOf(CredentialRef.SdJwt::class.java)
    }
}
