package my.ssdid.sdk.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DidValidationTest {

    @Test
    fun `validate accepts valid DID`() {
        val result = Did.validate("did:ssdid:dGVzdDEyMzQ1Njc4OTAxMjM")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().value).isEqualTo("did:ssdid:dGVzdDEyMzQ1Njc4OTAxMjM")
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
    fun `validate rejects short ID (less than 22 chars)`() {
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
        val result = Did.validate("did:ssdid:abc-def_ghi-jkl_mno_pqr")
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `validate rejects null-like strings`() {
        assertThat(Did.validate("null").isFailure).isTrue()
        assertThat(Did.validate("undefined").isFailure).isTrue()
    }

    // D1: Minimum length should be 22 for 128-bit entropy
    @Test
    fun `validate rejects 21-char ID (insufficient entropy)`() {
        assertThat(Did.validate("did:ssdid:abcdefghijklmnopqrstu").isFailure).isTrue()
    }

    @Test
    fun `validate accepts exactly 22-char ID`() {
        assertThat(Did.validate("did:ssdid:abcdefghijklmnopqrstuv").isSuccess).isTrue()
    }

    // D5: Max length to prevent DoS
    @Test
    fun `validate rejects excessively long ID`() {
        assertThat(Did.validate("did:ssdid:" + "a".repeat(200)).isFailure).isTrue()
    }

    // D6: Additional attack vectors
    @Test
    fun `validate rejects null byte in ID`() {
        assertThat(Did.validate("did:ssdid:abcdefghijklmnopqrst\u0000v").isFailure).isTrue()
    }

    @Test
    fun `validate rejects colon in method-specific ID`() {
        assertThat(Did.validate("did:ssdid:abc:defghijklmnopqrstuv").isFailure).isTrue()
    }

    @Test
    fun `validate rejects padding character in ID`() {
        assertThat(Did.validate("did:ssdid:dGVzdDEyMzQ1Njc4OTAxMjM=").isFailure).isTrue()
    }
}
