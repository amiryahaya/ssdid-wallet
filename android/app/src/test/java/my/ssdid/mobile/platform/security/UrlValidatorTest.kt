package my.ssdid.mobile.platform.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UrlValidatorTest {

    @Test
    fun `valid HTTPS URL is accepted`() {
        assertThat(UrlValidator.isValidServerUrl("https://demo.ssdid.my")).isTrue()
    }

    @Test
    fun `valid HTTPS URL with path is accepted`() {
        assertThat(UrlValidator.isValidServerUrl("https://demo.ssdid.my/api/v1")).isTrue()
    }

    @Test
    fun `HTTP URL is rejected`() {
        assertThat(UrlValidator.isValidServerUrl("http://demo.ssdid.my")).isFalse()
    }

    @Test
    fun `file scheme is rejected`() {
        assertThat(UrlValidator.isValidServerUrl("file:///etc/passwd")).isFalse()
    }

    @Test
    fun `localhost is rejected`() {
        assertThat(UrlValidator.isValidServerUrl("https://localhost")).isFalse()
    }

    @Test
    fun `loopback IP is rejected`() {
        assertThat(UrlValidator.isValidServerUrl("https://127.0.0.1")).isFalse()
    }

    @Test
    fun `private 10 dot network is rejected`() {
        assertThat(UrlValidator.isValidServerUrl("https://10.0.0.1")).isFalse()
    }

    @Test
    fun `private 172 dot 16 network is rejected`() {
        assertThat(UrlValidator.isValidServerUrl("https://172.16.0.1")).isFalse()
    }

    @Test
    fun `private 192 dot 168 network is rejected`() {
        assertThat(UrlValidator.isValidServerUrl("https://192.168.1.1")).isFalse()
    }

    @Test
    fun `single-label hostname is rejected`() {
        assertThat(UrlValidator.isValidServerUrl("https://intranet")).isFalse()
    }

    @Test
    fun `empty URL is rejected`() {
        assertThat(UrlValidator.isValidServerUrl("")).isFalse()
    }

    @Test
    fun `no-scheme URL is rejected`() {
        assertThat(UrlValidator.isValidServerUrl("demo.ssdid.my")).isFalse()
    }

    @Test
    fun `IPv6 loopback is rejected`() {
        assertThat(UrlValidator.isValidServerUrl("https://[::1]")).isFalse()
    }

    @Test
    fun `IPv6 unique local address is rejected`() {
        assertThat(UrlValidator.isValidServerUrl("https://[fd00::1]")).isFalse()
    }

    @Test
    fun `IPv6 link-local address is rejected`() {
        assertThat(UrlValidator.isValidServerUrl("https://[fe80::1]")).isFalse()
    }
}
