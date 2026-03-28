package my.ssdid.sdk

import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.vault.KeystoreManager
import my.ssdid.sdk.domain.verifier.offline.BundleStore
import my.ssdid.sdk.domain.verifier.offline.CredentialRepository
import my.ssdid.sdk.domain.verifier.offline.VerificationBundle
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SsdidSdkBuilderTest {

    /** In-memory fake that avoids the real AndroidKeyStore (unavailable under Robolectric). */
    private class FakeKeystoreManager : KeystoreManager {
        private val keys = mutableMapOf<String, ByteArray>()
        override fun generateWrappingKey(alias: String) { keys[alias] = ByteArray(32) }
        override fun encrypt(alias: String, data: ByteArray): ByteArray = data
        override fun decrypt(alias: String, encryptedData: ByteArray): ByteArray = encryptedData
        override fun deleteKey(alias: String) { keys.remove(alias) }
        override fun hasKey(alias: String): Boolean = keys.containsKey(alias)
    }

    private class FakeCredentialRepository : CredentialRepository {
        override suspend fun saveCredential(credential: VerifiableCredential) {}
        override suspend fun getHeldCredentials(): List<VerifiableCredential> = emptyList()
        override suspend fun getUniqueIssuerDids(): List<String> = emptyList()
        override suspend fun deleteCredential(credentialId: String) {}
    }

    private class FakeBundleStore : BundleStore {
        override suspend fun saveBundle(bundle: VerificationBundle) {}
        override suspend fun getBundle(issuerDid: String): VerificationBundle? = null
        override suspend fun deleteBundle(issuerDid: String) {}
        override suspend fun listBundles(): List<VerificationBundle> = emptyList()
    }

    @Test
    fun `builder creates SDK with required registryUrl`() {
        val context = RuntimeEnvironment.getApplication()
        val sdk = SsdidSdk.builder(context)
            .registryUrl("https://registry.ssdid.my")
            .keystoreManager(FakeKeystoreManager())
            .credentialRepository(FakeCredentialRepository())
            .bundleStore(FakeBundleStore())
            .build()
        assertThat(sdk).isNotNull()
        assertThat(sdk.identity).isNotNull()
        assertThat(sdk.vault).isNotNull()
        assertThat(sdk.credentials).isNotNull()
        assertThat(sdk.flows).isNotNull()
        assertThat(sdk.issuance).isNotNull()
        assertThat(sdk.presentation).isNotNull()
        assertThat(sdk.sdJwt).isNotNull()
        assertThat(sdk.verifier).isNotNull()
        assertThat(sdk.offline).isNotNull()
        assertThat(sdk.recovery).isNotNull()
        assertThat(sdk.rotation).isNotNull()
        assertThat(sdk.backup).isNotNull()
        assertThat(sdk.device).isNotNull()
        assertThat(sdk.notifications).isNotNull()
        assertThat(sdk.revocation).isNotNull()
        assertThat(sdk.history).isNotNull()
    }

    @Test(expected = IllegalStateException::class)
    fun `builder throws without registryUrl`() {
        val context = RuntimeEnvironment.getApplication()
        SsdidSdk.builder(context).build()
    }
}
