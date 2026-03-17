package my.ssdid.wallet.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DidValidationTest {

    @Test
    fun `validate accepts valid DID`() {
        val result = Did.validate("did:ssdid:dGVzdDEyMzQ1Njc4OTAx")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().value).isEqualTo("did:ssdid:dGVzdDEyMzQ1Njc4OTAx")
    }

    @Test
    fun `validate accepts generated DID`() {
        val did = Did.generate()
        val result = Did.validate(did.value)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `validate rejects empty string`() {
        assertThat(Did.validate("").isFailure).isTrue()
    }

    @Test
    fun `validate rejects wrong prefix`() {
        assertThat(Did.validate("did:key:z6MkhaXgBZD").isFailure).isTrue()
    }

    @Test
    fun `validate rejects did-ssdid with no ID`() {
        assertThat(Did.validate("did:ssdid:").isFailure).isTrue()
    }

    @Test
    fun `validate rejects short ID (less than 16 chars)`() {
        assertThat(Did.validate("did:ssdid:abc").isFailure).isTrue()
    }

    @Test
    fun `validate rejects invalid base64url characters`() {
        assertThat(Did.validate("did:ssdid:invalid+chars/here==").isFailure).isTrue()
    }

    @Test
    fun `validate rejects spaces in ID`() {
        assertThat(Did.validate("did:ssdid:has spaces here!").isFailure).isTrue()
    }

    @Test
    fun `validate accepts base64url with hyphens and underscores`() {
        val result = Did.validate("did:ssdid:abc-def_ghi-jkl_mno")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `validate rejects null-like strings`() {
        assertThat(Did.validate("null").isFailure).isTrue()
        assertThat(Did.validate("undefined").isFailure).isTrue()
    }
}
