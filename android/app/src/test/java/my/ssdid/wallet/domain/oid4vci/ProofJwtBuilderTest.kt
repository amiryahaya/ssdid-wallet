package my.ssdid.wallet.domain.oid4vci

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

class ProofJwtBuilderTest {

    private val signer: (ByteArray) -> ByteArray = { data -> data.copyOf(64) }

    @Test
    fun buildProofJwtStructure() {
        val jwt = ProofJwtBuilder.build(
            algorithm = "EdDSA",
            keyId = "did:ssdid:holder1#key-1",
            walletDid = "did:ssdid:holder1",
            issuerUrl = "https://issuer.example.com",
            nonce = "c-nonce-1",
            signer = signer,
            issuedAt = 1700000000L
        )

        val parts = jwt.split(".")
        assertThat(parts.size).isEqualTo(3)

        // Decode header
        val headerJson = String(Base64.getUrlDecoder().decode(parts[0]))
        assertThat(headerJson).contains("openid4vci-proof+jwt")
        assertThat(headerJson).contains("EdDSA")
        assertThat(headerJson).contains("did:ssdid:holder1#key-1")

        // Decode payload
        val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]))
        assertThat(payloadJson).contains("did:ssdid:holder1")
        assertThat(payloadJson).contains("https://issuer.example.com")
        assertThat(payloadJson).contains("c-nonce-1")
        assertThat(payloadJson).contains("1700000000")
        // exp = iat + 120 = 1700000120
        assertThat(payloadJson).contains("1700000120")
    }

    @Test
    fun proofJwtContainsCorrectFields() {
        val jwt = ProofJwtBuilder.build(
            algorithm = "ES256",
            keyId = "did:ssdid:h#k-1",
            walletDid = "did:ssdid:h",
            issuerUrl = "https://iss.example.com",
            nonce = "n-1",
            signer = signer,
            issuedAt = 1700000000L
        )
        assertThat(jwt.split(".")).hasSize(3)
    }

    @Test
    fun signatureUsesHeaderDotPayloadAsInput() {
        var capturedInput: ByteArray? = null
        val capturingSigner: (ByteArray) -> ByteArray = { data ->
            capturedInput = data
            data.copyOf(32)
        }

        val jwt = ProofJwtBuilder.build(
            algorithm = "EdDSA",
            keyId = "did:ssdid:x#k",
            walletDid = "did:ssdid:x",
            issuerUrl = "https://iss.example.com",
            nonce = "n",
            signer = capturingSigner,
            issuedAt = 1700000000L
        )

        val parts = jwt.split(".")
        val expectedInput = "${parts[0]}.${parts[1]}"
        assertThat(String(capturedInput!!)).isEqualTo(expectedInput)
    }
}
