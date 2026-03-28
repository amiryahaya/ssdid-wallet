package my.ssdid.sdk.domain.device

import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import my.ssdid.sdk.domain.SsdidClient
import my.ssdid.sdk.domain.model.*
import my.ssdid.sdk.domain.transport.RegistryApi
import my.ssdid.sdk.domain.transport.SsdidHttpClient
import my.ssdid.sdk.domain.transport.dto.*
import my.ssdid.sdk.domain.vault.Vault
import org.junit.Before
import org.junit.Test

class DeviceManagerTest {

    private lateinit var vault: Vault
    private lateinit var httpClient: SsdidHttpClient
    private lateinit var registryApi: RegistryApi
    private lateinit var ssdidClient: SsdidClient
    private lateinit var ssdidClientProvider: () -> SsdidClient
    private lateinit var deviceInfoProvider: DeviceInfoProvider
    private lateinit var manager: DeviceManager

    private val testIdentity = Identity(
        name = "Test",
        did = "did:ssdid:abc123",
        keyId = "key-1",
        algorithm = Algorithm.ED25519,
        publicKeyMultibase = "uPublicKey",
        createdAt = "2024-01-01T00:00:00Z"
    )

    @Before
    fun setup() {
        vault = mockk()
        httpClient = mockk()
        registryApi = mockk()
        ssdidClient = mockk()
        ssdidClientProvider = { ssdidClient }
        deviceInfoProvider = object : DeviceInfoProvider {
            override val deviceName: String get() = "Test Device"
            override val platform: String get() = "android"
        }
        every { httpClient.registry } returns registryApi
        manager = DeviceManager(vault, httpClient, ssdidClientProvider, deviceInfoProvider)
    }

    @Test
    fun `initiatePairing returns PairingData with correct did`() = runTest {
        coEvery { registryApi.initPairing("did:ssdid:abc123", any()) } returns PairingInitResponse("pairing-1")

        val result = manager.initiatePairing(testIdentity)

        assertThat(result.isSuccess).isTrue()
        val data = result.getOrThrow()
        assertThat(data.did).isEqualTo("did:ssdid:abc123")
        assertThat(data.pairingId).isEqualTo("pairing-1")
        assertThat(data.challenge).isNotEmpty()
    }

    @Test
    fun `initiatePairing returns failure on network error`() = runTest {
        coEvery { registryApi.initPairing(any(), any()) } throws RuntimeException("Network error")

        val result = manager.initiatePairing(testIdentity)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Network error")
    }

    @Test
    fun `joinPairing signs challenge and calls registry`() = runTest {
        val sigBytes = byteArrayOf(1, 2, 3, 4)
        coEvery { vault.sign("key-1", any()) } returns Result.success(sigBytes)
        coEvery { registryApi.joinPairing("did:ssdid:abc123", "pairing-1", any()) } returns PairingJoinResponse("joined")

        val result = manager.joinPairing(
            did = "did:ssdid:abc123",
            pairingId = "pairing-1",
            challenge = "test-challenge",
            identity = testIdentity,
            deviceName = "Test Phone"
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isEqualTo("joined")
        coVerify { vault.sign("key-1", "join:test-challenge".toByteArray()) }
        coVerify {
            registryApi.joinPairing("did:ssdid:abc123", "pairing-1", match {
                it.pairing_id == "pairing-1" &&
                it.device_name == "Test Phone" &&
                it.platform == "android" &&
                it.public_key == "uPublicKey"
            })
        }
    }

    @Test
    fun `joinPairing returns failure when signing fails`() = runTest {
        coEvery { vault.sign("key-1", any()) } returns Result.failure(RuntimeException("Key not found"))

        val result = manager.joinPairing(
            did = "did:ssdid:abc123",
            pairingId = "pairing-1",
            challenge = "test-challenge",
            identity = testIdentity,
            deviceName = "Test Phone"
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Key not found")
    }

    @Test
    fun `approvePairing signs approval and updates DID document`() = runTest {
        val sigBytes = byteArrayOf(5, 6, 7, 8)
        coEvery { vault.sign("key-1", any()) } returns Result.success(sigBytes)
        coEvery { registryApi.approvePairing("did:ssdid:abc123", "pairing-1", any()) } returns Unit
        coEvery { ssdidClient.updateDidDocument("key-1") } returns Result.success(Unit)

        val result = manager.approvePairing(testIdentity, "pairing-1")

        assertThat(result.isSuccess).isTrue()
        coVerify { vault.sign("key-1", "approve:pairing-1".toByteArray()) }
        coVerify {
            registryApi.approvePairing("did:ssdid:abc123", "pairing-1", match {
                it.did == "did:ssdid:abc123" &&
                it.key_id == "key-1"
            })
        }
        coVerify { ssdidClient.updateDidDocument("key-1") }
    }

    @Test
    fun `approvePairing returns failure when signing fails`() = runTest {
        coEvery { vault.sign("key-1", any()) } returns Result.failure(RuntimeException("Signing failed"))

        val result = manager.approvePairing(testIdentity, "pairing-1")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Signing failed")
    }

    @Test
    fun `revokeDevice rejects primary key`() = runTest {
        val result = manager.revokeDevice(testIdentity, "key-1")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Cannot revoke primary device key")
    }

    @Test
    fun `revokeDevice succeeds for non-primary key and updates DID document`() = runTest {
        coEvery { ssdidClient.updateDidDocument("key-1") } returns Result.success(Unit)

        val result = manager.revokeDevice(testIdentity, "key-2")

        assertThat(result.isSuccess).isTrue()
        coVerify { ssdidClient.updateDidDocument("key-1") }
    }

    @Test
    fun `listDevices returns at least the primary device`() = runTest {
        val didDoc = DidDocument(
            id = "did:ssdid:abc123",
            controller = "did:ssdid:abc123",
            verificationMethod = listOf(
                VerificationMethod(
                    id = "key-1",
                    type = "Ed25519VerificationKey2020",
                    controller = "did:ssdid:abc123",
                    publicKeyMultibase = "uPublicKey"
                )
            ),
            authentication = listOf("key-1"),
            assertionMethod = listOf("key-1")
        )
        coEvery { vault.buildDidDocument("key-1") } returns Result.success(didDoc)

        val result = manager.listDevices(testIdentity)

        assertThat(result.isSuccess).isTrue()
        val devices = result.getOrThrow()
        assertThat(devices).isNotEmpty()
        assertThat(devices.any { it.isPrimary }).isTrue()
        assertThat(devices.first { it.isPrimary }.keyId).isEqualTo("key-1")
    }

    @Test
    fun `listDevices includes secondary devices from DID document`() = runTest {
        val didDoc = DidDocument(
            id = "did:ssdid:abc123",
            controller = "did:ssdid:abc123",
            verificationMethod = listOf(
                VerificationMethod(
                    id = "key-1",
                    type = "Ed25519VerificationKey2020",
                    controller = "did:ssdid:abc123",
                    publicKeyMultibase = "uPublicKey"
                ),
                VerificationMethod(
                    id = "key-2",
                    type = "Ed25519VerificationKey2020",
                    controller = "did:ssdid:abc123",
                    publicKeyMultibase = "uOtherKey"
                )
            ),
            authentication = listOf("key-1", "key-2"),
            assertionMethod = listOf("key-1", "key-2")
        )
        coEvery { vault.buildDidDocument("key-1") } returns Result.success(didDoc)

        val result = manager.listDevices(testIdentity)

        assertThat(result.isSuccess).isTrue()
        val devices = result.getOrThrow()
        assertThat(devices).hasSize(2)
        assertThat(devices.count { it.isPrimary }).isEqualTo(1)
        assertThat(devices.count { !it.isPrimary }).isEqualTo(1)
        assertThat(devices.find { !it.isPrimary }?.keyId).isEqualTo("key-2")
    }

    @Test
    fun `checkPairingStatus returns status from registry`() = runTest {
        val statusResponse = PairingStatusResponse(status = "pending")
        coEvery { registryApi.getPairingStatus("did:ssdid:abc123", "pairing-1") } returns statusResponse

        val result = manager.checkPairingStatus("did:ssdid:abc123", "pairing-1")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().status).isEqualTo("pending")
    }

    @Test
    fun `listDevices falls back to identity when verificationMethod is empty`() = runTest {
        val didDoc = DidDocument(
            id = "did:ssdid:abc123",
            controller = "did:ssdid:abc123",
            verificationMethod = emptyList(),
            authentication = emptyList(),
            assertionMethod = emptyList()
        )
        coEvery { vault.buildDidDocument("key-1") } returns Result.success(didDoc)

        val result = manager.listDevices(testIdentity)

        assertThat(result.isSuccess).isTrue()
        val devices = result.getOrThrow()
        assertThat(devices).hasSize(1)
        assertThat(devices.first().isPrimary).isTrue()
        assertThat(devices.first().keyId).isEqualTo("key-1")
    }

    @Test
    fun `approvePairing returns failure when updateDidDocument fails`() = runTest {
        val sigBytes = byteArrayOf(5, 6, 7, 8)
        coEvery { vault.sign("key-1", any()) } returns Result.success(sigBytes)
        coEvery { registryApi.approvePairing("did:ssdid:abc123", "pairing-1", any()) } returns Unit
        coEvery { ssdidClient.updateDidDocument("key-1") } returns Result.failure(RuntimeException("Registry unreachable"))

        val result = manager.approvePairing(testIdentity, "pairing-1")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("Registry unreachable")
    }
}
