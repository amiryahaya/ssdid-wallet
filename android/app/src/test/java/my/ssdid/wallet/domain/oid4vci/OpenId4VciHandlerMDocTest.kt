package my.ssdid.wallet.domain.oid4vci

import android.util.Base64
import com.google.common.truth.Truth.assertThat
import com.upokecenter.cbor.CBORObject
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import my.ssdid.wallet.domain.mdoc.StoredMDoc
import my.ssdid.wallet.domain.vault.Vault
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenId4VciHandlerMDocTest {

    private lateinit var metadataResolver: IssuerMetadataResolver
    private lateinit var tokenClient: TokenClient
    private lateinit var nonceManager: NonceManager
    private lateinit var transport: OpenId4VciTransport
    private lateinit var vault: Vault
    private lateinit var handler: OpenId4VciHandler

    private val signer: (ByteArray) -> ByteArray = { ByteArray(64) }

    private val mdocDoctype = "org.iso.18013.5.1.mDL"

    private val mdocConfigMap = mapOf(
        "mDL" to JsonObject(
            mapOf(
                "format" to JsonPrimitive("mso_mdoc"),
                "doctype" to JsonPrimitive(mdocDoctype)
            )
        )
    )

    private val metadata = IssuerMetadata(
        credentialIssuer = "https://issuer.example.com",
        credentialEndpoint = "https://issuer.example.com/credential",
        credentialConfigurationsSupported = mdocConfigMap,
        tokenEndpoint = "https://issuer.example.com/token",
        authorizationEndpoint = null
    )

    private val offer = CredentialOffer(
        credentialIssuer = "https://issuer.example.com",
        credentialConfigurationIds = listOf("mDL"),
        preAuthorizedCode = "pre-code-mdoc"
    )

    /** Build a minimal CBOR map to simulate an issuer-signed mdoc credential. */
    private fun buildTestMDocCbor(): String {
        val nameSpaces = CBORObject.NewMap()
        val items = CBORObject.NewArray()
        val item = CBORObject.NewMap()
        item["elementIdentifier"] = CBORObject.FromObject("family_name")
        item["elementValue"] = CBORObject.FromObject("Doe")
        items.Add(item)
        nameSpaces["org.iso.18013.5.1"] = items

        val root = CBORObject.NewMap()
        root["docType"] = CBORObject.FromObject(mdocDoctype)
        root["nameSpaces"] = nameSpaces

        val bytes = root.EncodeToBytes()
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    @Before
    fun setup() {
        metadataResolver = mockk()
        tokenClient = mockk()
        nonceManager = NonceManager()
        transport = mockk()
        vault = mockk(relaxed = true)
        handler = OpenId4VciHandler(metadataResolver, tokenClient, nonceManager, transport, vault)
    }

    @Test
    fun requestMDocCredentialSendsCorrectFormat() = runTest {
        every {
            tokenClient.exchangePreAuthorizedCode(any(), any(), any())
        } returns Result.success(TokenResponse("at-mdoc", "Bearer", "cn-mdoc", 300))

        val capturedBody = slot<String>()
        every {
            transport.postCredentialRequest(any(), any(), capture(capturedBody))
        } returns """{"credential":"${buildTestMDocCbor()}"}"""

        coEvery { vault.storeMDoc(any()) } just Runs

        handler.acceptOffer(
            offer = offer,
            metadata = metadata,
            selectedConfigId = "mDL",
            walletDid = "did:ssdid:holder",
            keyId = "did:ssdid:holder#key-1",
            algorithm = "EdDSA",
            signer = signer
        )

        val body = capturedBody.captured
        assertThat(body).contains("\"format\":\"mso_mdoc\"")
        assertThat(body).contains("\"doctype\":\"$mdocDoctype\"")
        // Should NOT contain credential_definition (that's SD-JWT specific)
        assertThat(body).doesNotContain("credential_definition")
    }

    @Test
    fun requestMDocCredentialStoresResult() = runTest {
        every {
            tokenClient.exchangePreAuthorizedCode(any(), any(), any())
        } returns Result.success(TokenResponse("at-mdoc", "Bearer", "cn-mdoc", 300))

        every {
            transport.postCredentialRequest(any(), any(), any())
        } returns """{"credential":"${buildTestMDocCbor()}"}"""

        coEvery { vault.storeMDoc(any()) } just Runs

        handler.acceptOffer(
            offer = offer,
            metadata = metadata,
            selectedConfigId = "mDL",
            walletDid = "did:ssdid:holder",
            keyId = "did:ssdid:holder#key-1",
            algorithm = "EdDSA",
            signer = signer
        )

        coVerify(exactly = 1) { vault.storeMDoc(any()) }
        // SD-JWT store should NOT be called
        coVerify(exactly = 0) { vault.storeStoredSdJwtVc(any()) }
    }

    @Test
    fun requestMDocCredentialReturnsMDocSuccess() = runTest {
        every {
            tokenClient.exchangePreAuthorizedCode(any(), any(), any())
        } returns Result.success(TokenResponse("at-mdoc", "Bearer", "cn-mdoc", 300))

        every {
            transport.postCredentialRequest(any(), any(), any())
        } returns """{"credential":"${buildTestMDocCbor()}"}"""

        coEvery { vault.storeMDoc(any()) } just Runs

        val result = handler.acceptOffer(
            offer = offer,
            metadata = metadata,
            selectedConfigId = "mDL",
            walletDid = "did:ssdid:holder",
            keyId = "did:ssdid:holder#key-1",
            algorithm = "EdDSA",
            signer = signer
        )

        assertThat(result.isSuccess).isTrue()
        val issuance = result.getOrThrow()
        assertThat(issuance).isInstanceOf(IssuanceResult.MDocSuccess::class.java)

        val mdocSuccess = issuance as IssuanceResult.MDocSuccess
        assertThat(mdocSuccess.mdoc.docType).isEqualTo(mdocDoctype)
        assertThat(mdocSuccess.mdoc.deviceKeyId).isEqualTo("did:ssdid:holder#key-1")
        assertThat(mdocSuccess.mdoc.issuerSignedCbor).isNotEmpty()
        assertThat(mdocSuccess.mdoc.nameSpaces).isNotEmpty()
    }
}
