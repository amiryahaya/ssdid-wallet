package my.ssdid.wallet.domain

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.model.*
import my.ssdid.wallet.domain.history.ActivityRepository
import my.ssdid.wallet.domain.transport.RegistryApi
import my.ssdid.wallet.domain.transport.ServerApi
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import my.ssdid.wallet.domain.transport.dto.*
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.verifier.Verifier
import org.junit.Before
import org.junit.Test

class SsdidClientTest {

    private lateinit var client: SsdidClient
    private lateinit var vault: Vault
    private lateinit var verifier: Verifier
    private lateinit var httpClient: SsdidHttpClient
    private lateinit var registryApi: RegistryApi
    private lateinit var serverApi: ServerApi
    private lateinit var activityRepo: ActivityRepository

    private val testProof = Proof(
        type = "Ed25519Signature2020",
        created = "2026-03-06T00:00:00Z",
        verificationMethod = "did:ssdid:test#key-1",
        proofPurpose = "assertionMethod",
        proofValue = "uABC123"
    )

    private val testIdentity = Identity(
        name = "Test",
        did = "did:ssdid:test123",
        keyId = "did:ssdid:test123#key-1",
        algorithm = Algorithm.ED25519,
        publicKeyMultibase = "uPublicKey",
        createdAt = "2026-03-06T00:00:00Z"
    )

    private val testDidDoc = DidDocument(
        id = "did:ssdid:test123",
        controller = "did:ssdid:test123",
        verificationMethod = listOf(
            VerificationMethod(
                id = "did:ssdid:test123#key-1",
                type = "Ed25519VerificationKey2020",
                controller = "did:ssdid:test123",
                publicKeyMultibase = "uPublicKey"
            )
        ),
        authentication = listOf("did:ssdid:test123#key-1"),
        assertionMethod = listOf("did:ssdid:test123#key-1")
    )

    private val testVc = VerifiableCredential(
        id = "urn:uuid:vc-1",
        type = listOf("VerifiableCredential"),
        issuer = "did:ssdid:server",
        issuanceDate = "2026-03-06T00:00:00Z",
        credentialSubject = CredentialSubject(id = "did:ssdid:test123"),
        proof = testProof
    )

    @Before
    fun setup() {
        vault = mockk()
        verifier = mockk()
        httpClient = mockk(relaxed = true)
        registryApi = mockk(relaxed = true)
        serverApi = mockk(relaxed = true)
        activityRepo = mockk(relaxed = true)

        every { httpClient.registry } returns registryApi
        every { httpClient.serverApi(any()) } returns serverApi

        client = SsdidClient(vault, verifier, httpClient, activityRepo)
    }

    // --- Flow 1: initIdentity ---

    @Test
    fun `initIdentity creates identity and registers DID`() = runTest {
        coEvery { vault.createIdentity("Test", Algorithm.ED25519) } returns Result.success(testIdentity)
        coEvery { vault.buildDidDocument(testIdentity.keyId) } returns Result.success(testDidDoc)
        coEvery { vault.createProof(testIdentity.keyId, any(), "assertionMethod") } returns Result.success(testProof)
        coEvery { registryApi.registerDid(any()) } returns RegisterDidResponse("did:ssdid:test123", "ok")

        val result = client.initIdentity("Test", Algorithm.ED25519)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isEqualTo(testIdentity)
        coVerify { registryApi.registerDid(any()) }
    }

    @Test
    fun `initIdentity fails when vault createIdentity fails`() = runTest {
        coEvery { vault.createIdentity(any(), any()) } returns Result.failure(RuntimeException("keygen failed"))

        val result = client.initIdentity("Test", Algorithm.ED25519)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).isEqualTo("keygen failed")
    }

    @Test
    fun `initIdentity fails when buildDidDocument fails`() = runTest {
        coEvery { vault.createIdentity(any(), any()) } returns Result.success(testIdentity)
        coEvery { vault.buildDidDocument(any()) } returns Result.failure(RuntimeException("doc build failed"))

        val result = client.initIdentity("Test", Algorithm.ED25519)

        assertThat(result.isFailure).isTrue()
    }

    // --- deactivateDid ---

    @Test
    fun `deactivateDid calls registry and succeeds`() = runTest {
        coEvery { vault.getIdentity(testIdentity.keyId) } returns testIdentity
        coEvery { vault.createProof(testIdentity.keyId, any(), "capabilityInvocation") } returns Result.success(testProof)
        coEvery { registryApi.deactivateDid(testIdentity.did, any()) } returns RegisterDidResponse(testIdentity.did, "ok")
        coEvery { vault.deleteIdentity(testIdentity.keyId) } returns Result.success(Unit)

        val result = client.deactivateDid(testIdentity.keyId)

        assertThat(result.isSuccess).isTrue()
        coVerify { registryApi.deactivateDid(eq(testIdentity.did), any()) }
        coVerify { vault.deleteIdentity(testIdentity.keyId) }
    }

    // --- Flow 2: registerWithService ---

    @Test
    fun `registerWithService completes mutual auth and stores VC`() = runTest {
        val startResp = RegisterStartResponse(
            challenge = "server-challenge",
            server_did = "did:ssdid:server",
            server_key_id = "did:ssdid:server#key-1",
            server_signature = "uServerSig"
        )
        coEvery { serverApi.registerStart(any()) } returns startResp
        coEvery { verifier.verifyChallengeResponse(any(), any(), any(), any()) } returns Result.success(true)
        coEvery { vault.sign(testIdentity.keyId, any()) } returns Result.success("signature".toByteArray())
        coEvery { serverApi.registerVerify(any()) } returns RegisterVerifyResponse(testVc)
        coEvery { vault.storeCredential(testVc) } returns Result.success(Unit)

        val result = client.registerWithService(testIdentity, "https://server.example.com")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isEqualTo(testVc)
        coVerify { vault.storeCredential(testVc) }
    }

    @Test
    fun `registerWithService fails when server mutual auth fails`() = runTest {
        val startResp = RegisterStartResponse(
            challenge = "challenge",
            server_did = "did:ssdid:server",
            server_key_id = "did:ssdid:server#key-1",
            server_signature = "uBadSig"
        )
        coEvery { serverApi.registerStart(any()) } returns startResp
        coEvery { verifier.verifyChallengeResponse(any(), any(), any(), any()) } returns Result.success(false)

        val result = client.registerWithService(testIdentity, "https://server.example.com")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
        assertThat(result.exceptionOrNull()?.message).contains("mutual authentication failed")
    }

    @Test
    fun `registerWithService fails when verifier throws`() = runTest {
        val startResp = RegisterStartResponse(
            challenge = "challenge",
            server_did = "did:ssdid:server",
            server_key_id = "did:ssdid:server#key-1",
            server_signature = "uSig"
        )
        coEvery { serverApi.registerStart(any()) } returns startResp
        coEvery { verifier.verifyChallengeResponse(any(), any(), any(), any()) } returns Result.failure(RuntimeException("network error"))

        val result = client.registerWithService(testIdentity, "https://server.example.com")

        assertThat(result.isFailure).isTrue()
    }

    // --- Flow 3: authenticate ---

    @Test
    fun `authenticate verifies server session token`() = runTest {
        val authResp = AuthenticateResponse(
            session_token = "session-abc",
            server_did = "did:ssdid:server",
            server_key_id = "did:ssdid:server#key-1",
            server_signature = "uSessionSig"
        )
        coEvery { serverApi.authenticate(any()) } returns authResp
        coEvery { verifier.verifyChallengeResponse("did:ssdid:server", "did:ssdid:server#key-1", "session-abc", "uSessionSig") } returns Result.success(true)

        val result = client.authenticate(testVc, "https://server.example.com")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().session_token).isEqualTo("session-abc")
    }

    @Test
    fun `authenticate fails when server has no signature`() = runTest {
        val authResp = AuthenticateResponse(
            session_token = "session-abc",
            server_did = "did:ssdid:server",
            server_key_id = "did:ssdid:server#key-1",
            server_signature = null
        )
        coEvery { serverApi.authenticate(any()) } returns authResp

        val result = client.authenticate(testVc, "https://server.example.com")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
        assertThat(result.exceptionOrNull()?.message).contains("did not provide mutual authentication")
    }

    @Test
    fun `authenticate fails when server signature verification fails`() = runTest {
        val authResp = AuthenticateResponse(
            session_token = "session-abc",
            server_did = "did:ssdid:server",
            server_key_id = "did:ssdid:server#key-1",
            server_signature = "uBadSig"
        )
        coEvery { serverApi.authenticate(any()) } returns authResp
        coEvery { verifier.verifyChallengeResponse(any(), any(), any(), any()) } returns Result.success(false)

        val result = client.authenticate(testVc, "https://server.example.com")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("session token verification failed")
    }

    // --- fetchTransactionDetails ---

    @Test
    fun `fetchTransactionDetails returns transaction map`() = runTest {
        val txMap = mapOf("amount" to "100", "recipient" to "Alice")
        val response = TxChallengeResponse(challenge = "ch", transaction = txMap)
        coEvery { serverApi.requestChallenge(any()) } returns response

        val result = client.fetchTransactionDetails("session-abc", "https://server.example.com")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isEqualTo(txMap)
    }

    // --- Flow 4: signTransaction ---

    @Test
    fun `signTransaction signs challenge with tx hash binding`() = runTest {
        val tx = mapOf("amount" to "500", "to" to "Bob")
        val challengeResp = TxChallengeResponse(challenge = "server-challenge", transaction = tx)
        coEvery { serverApi.requestChallenge(any()) } returns challengeResp
        coEvery { vault.sign(testIdentity.keyId, any()) } returns Result.success("txsig".toByteArray())
        val submitResp = TxSubmitResponse(transaction_id = "tx-001", status = "confirmed")
        coEvery { serverApi.submitTransaction(any()) } returns submitResp

        val result = client.signTransaction("session-abc", testIdentity, tx, "https://server.example.com")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().transaction_id).isEqualTo("tx-001")
        assertThat(result.getOrThrow().status).isEqualTo("confirmed")

        coVerify {
            serverApi.submitTransaction(match {
                it.session_token == "session-abc" &&
                it.did == testIdentity.did &&
                it.key_id == testIdentity.keyId &&
                it.transaction == tx &&
                it.signed_challenge.isNotEmpty()
            })
        }
    }

    @Test
    fun `signTransaction signs payload containing challenge and txHash`() = runTest {
        val capturedPayload = slot<ByteArray>()
        coEvery { vault.sign(testIdentity.keyId, capture(capturedPayload)) } returns Result.success("sig".toByteArray())
        coEvery { serverApi.submitTransaction(any()) } returns TxSubmitResponse("tx-1", "ok")

        val tx = mapOf("key" to "value")
        val challengeResp = TxChallengeResponse(challenge = "test-challenge", transaction = tx)
        coEvery { serverApi.requestChallenge(any()) } returns challengeResp

        client.signTransaction("session", testIdentity, tx, "https://server.example.com")

        val payload = String(capturedPayload.captured)
        assertThat(payload).startsWith("test-challenge")
        // Payload format: "challenge" + Base64(SHA3-256(txJson))
        // The Base64-encoded hash portion should be non-empty
        val hashPortion = payload.removePrefix("test-challenge")
        assertThat(hashPortion).isNotEmpty()
        // Base64url (no padding) of a 32-byte SHA3-256 hash is 43 characters
        assertThat(hashPortion.length).isEqualTo(43)
    }

    @Test
    fun `signTransaction fails when vault sign fails`() = runTest {
        coEvery { vault.sign(any(), any()) } returns Result.failure(RuntimeException("sign failed"))

        val result = client.signTransaction("session", testIdentity, mapOf("a" to "b"), "https://server.example.com")

        assertThat(result.isFailure).isTrue()
    }
}
