package my.ssdid.sdk.domain.oid4vci

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CredentialOfferTest {

    @Test
    fun parsePreAuthorizedCodeOffer() {
        val json = """
            {
                "credential_issuer": "https://issuer.example.com",
                "credential_configuration_ids": ["UnivDegree"],
                "grants": {
                    "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                        "pre-authorized_code": "abc123"
                    }
                }
            }
        """
        val result = CredentialOffer.parse(json)
        assertThat(result.isSuccess).isTrue()
        val offer = result.getOrThrow()
        assertThat(offer.credentialIssuer).isEqualTo("https://issuer.example.com")
        assertThat(offer.credentialConfigurationIds).containsExactly("UnivDegree")
        assertThat(offer.preAuthorizedCode).isEqualTo("abc123")
        assertThat(offer.txCode).isNull()
        assertThat(offer.authorizationCodeGrant).isFalse()
    }

    @Test
    fun parseOfferWithTxCode() {
        val json = """
            {
                "credential_issuer": "https://issuer.example.com",
                "credential_configuration_ids": ["IdCard"],
                "grants": {
                    "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                        "pre-authorized_code": "xyz789",
                        "tx_code": {
                            "input_mode": "numeric",
                            "length": 6,
                            "description": "Enter PIN from email"
                        }
                    }
                }
            }
        """
        val result = CredentialOffer.parse(json)
        assertThat(result.isSuccess).isTrue()
        val offer = result.getOrThrow()
        assertThat(offer.txCode).isNotNull()
        assertThat(offer.txCode!!.inputMode).isEqualTo("numeric")
        assertThat(offer.txCode!!.length).isEqualTo(6)
    }

    @Test
    fun parseAuthorizationCodeGrant() {
        val json = """
            {
                "credential_issuer": "https://issuer.example.com",
                "credential_configuration_ids": ["Diploma"],
                "grants": {
                    "authorization_code": {
                        "issuer_state": "state-abc"
                    }
                }
            }
        """
        val result = CredentialOffer.parse(json)
        assertThat(result.isSuccess).isTrue()
        val offer = result.getOrThrow()
        assertThat(offer.authorizationCodeGrant).isTrue()
        assertThat(offer.issuerState).isEqualTo("state-abc")
        assertThat(offer.preAuthorizedCode).isNull()
    }

    @Test
    fun rejectHttpIssuer() {
        val json = """{"credential_issuer":"http://bad.com","credential_configuration_ids":["x"],"grants":{"authorization_code":{}}}"""
        val result = CredentialOffer.parse(json)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()!!.message).contains("HTTPS")
    }

    @Test
    fun rejectEmptyConfigIds() {
        val json = """{"credential_issuer":"https://issuer.example.com","credential_configuration_ids":[],"grants":{"authorization_code":{}}}"""
        val result = CredentialOffer.parse(json)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun rejectMissingGrants() {
        val json = """{"credential_issuer":"https://issuer.example.com","credential_configuration_ids":["x"]}"""
        val result = CredentialOffer.parse(json)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun parseFromUri() {
        val offerJson = """{"credential_issuer":"https://issuer.example.com","credential_configuration_ids":["x"],"grants":{"authorization_code":{}}}"""
        val result = CredentialOffer.parseFromUri("openid-credential-offer://?credential_offer=${java.net.URLEncoder.encode(offerJson, "UTF-8")}")
        assertThat(result.isSuccess).isTrue()
    }
}
