package my.ssdid.sdk.domain.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClaimValidatorTest {

    // --- isWellKnown ---

    @Test
    fun `isWellKnown returns true for name, email, phone`() {
        assertThat(ClaimValidator.isWellKnown("name")).isTrue()
        assertThat(ClaimValidator.isWellKnown("email")).isTrue()
        assertThat(ClaimValidator.isWellKnown("phone")).isTrue()
    }

    @Test
    fun `isWellKnown returns false for unknown keys`() {
        assertThat(ClaimValidator.isWellKnown("address")).isFalse()
        assertThat(ClaimValidator.isWellKnown("age")).isFalse()
        assertThat(ClaimValidator.isWellKnown("")).isFalse()
    }

    // --- validate name ---

    @Test
    fun `valid name passes`() {
        assertThat(ClaimValidator.validate("name", "Alice")).isNull()
    }

    @Test
    fun `empty name fails`() {
        assertThat(ClaimValidator.validate("name", "")).isNotNull()
    }

    @Test
    fun `name over 100 chars fails`() {
        val longName = "a".repeat(101)
        assertThat(ClaimValidator.validate("name", longName)).isNotNull()
    }

    // --- validate email ---

    @Test
    fun `valid email passes`() {
        assertThat(ClaimValidator.validate("email", "alice@example.com")).isNull()
    }

    @Test
    fun `invalid email fails`() {
        assertThat(ClaimValidator.validate("email", "not-an-email")).isNotNull()
    }

    // --- validate phone ---

    @Test
    fun `valid phone passes`() {
        assertThat(ClaimValidator.validate("phone", "+60123456789")).isNull()
    }

    @Test
    fun `phone without plus fails`() {
        assertThat(ClaimValidator.validate("phone", "60123456789")).isNotNull()
    }

    // --- unknown keys ---

    @Test
    fun `unknown claim key always passes`() {
        assertThat(ClaimValidator.validate("address", "anything")).isNull()
        assertThat(ClaimValidator.validate("age", "")).isNull()
    }
}
