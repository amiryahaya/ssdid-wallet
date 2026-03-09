package my.ssdid.wallet.domain.revocation

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import my.ssdid.wallet.domain.model.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

class RevocationManagerTest {

    private lateinit var fetcher: StatusListFetcher
    private lateinit var manager: RevocationManager

    private fun makeEncodedList(revokedIndices: Set<Int>): String {
        val bytes = ByteArray(128) // 1024 bits
        for (idx in revokedIndices) {
            val bytePos = idx / 8
            val bitPos = 7 - (idx % 8)
            bytes[bytePos] = (bytes[bytePos].toInt() or (1 shl bitPos)).toByte()
        }
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(bytes) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray())
    }

    private fun makeStatusListCredential(revokedIndices: Set<Int>): StatusListCredential {
        return StatusListCredential(
            type = listOf("VerifiableCredential", "BitstringStatusListCredential"),
            issuer = "did:ssdid:issuer",
            credentialSubject = StatusListSubject(
                type = "BitstringStatusList",
                statusPurpose = "revocation",
                encodedList = makeEncodedList(revokedIndices)
            )
        )
    }

    private val testProof = Proof(
        type = "Ed25519Signature2020", created = "2026-01-01T00:00:00Z",
        verificationMethod = "did:ssdid:issuer#key-1", proofPurpose = "assertionMethod", proofValue = "uABC"
    )

    @Before
    fun setup() {
        fetcher = mockk()
        manager = RevocationManager(fetcher)
    }

    @Test
    fun `checkRevocation returns VALID for credential without status`() = runTest {
        val vc = VerifiableCredential(
            id = "urn:uuid:1", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = "2026-01-01T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            proof = testProof
        )
        val result = manager.checkRevocation(vc)
        assertThat(result).isEqualTo(RevocationStatus.VALID)
    }

    @Test
    fun `checkRevocation returns REVOKED when bit is set`() = runTest {
        val statusListCred = makeStatusListCredential(setOf(42))
        coEvery { fetcher.fetch("https://registry.example/status/1") } returns Result.success(statusListCred)

        val vc = VerifiableCredential(
            id = "urn:uuid:2", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = "2026-01-01T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            credentialStatus = CredentialStatus(
                id = "https://registry.example/status/1#42",
                type = "BitstringStatusListEntry",
                statusPurpose = "revocation",
                statusListIndex = "42",
                statusListCredential = "https://registry.example/status/1"
            ),
            proof = testProof
        )
        val result = manager.checkRevocation(vc)
        assertThat(result).isEqualTo(RevocationStatus.REVOKED)
    }

    @Test
    fun `checkRevocation returns VALID when bit is not set`() = runTest {
        val statusListCred = makeStatusListCredential(setOf(42))
        coEvery { fetcher.fetch("https://registry.example/status/1") } returns Result.success(statusListCred)

        val vc = VerifiableCredential(
            id = "urn:uuid:3", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = "2026-01-01T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            credentialStatus = CredentialStatus(
                id = "https://registry.example/status/1#10",
                type = "BitstringStatusListEntry",
                statusPurpose = "revocation",
                statusListIndex = "10",
                statusListCredential = "https://registry.example/status/1"
            ),
            proof = testProof
        )
        val result = manager.checkRevocation(vc)
        assertThat(result).isEqualTo(RevocationStatus.VALID)
    }

    @Test
    fun `checkRevocation returns UNKNOWN when fetch fails`() = runTest {
        coEvery { fetcher.fetch(any()) } returns Result.failure(RuntimeException("network error"))

        val vc = VerifiableCredential(
            id = "urn:uuid:4", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = "2026-01-01T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            credentialStatus = CredentialStatus(
                id = "https://registry.example/status/1#5",
                type = "BitstringStatusListEntry",
                statusPurpose = "revocation",
                statusListIndex = "5",
                statusListCredential = "https://registry.example/status/1"
            ),
            proof = testProof
        )
        val result = manager.checkRevocation(vc)
        assertThat(result).isEqualTo(RevocationStatus.UNKNOWN)
    }

    @Test
    fun `checkRevocation caches status list and reuses it`() = runTest {
        val statusListCred = makeStatusListCredential(emptySet())
        coEvery { fetcher.fetch("https://registry.example/status/1") } returns Result.success(statusListCred)

        val status = CredentialStatus(
            id = "https://registry.example/status/1#5",
            type = "BitstringStatusListEntry",
            statusPurpose = "revocation",
            statusListIndex = "5",
            statusListCredential = "https://registry.example/status/1"
        )

        val vc1 = VerifiableCredential(
            id = "urn:uuid:a", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = "2026-01-01T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            credentialStatus = status, proof = testProof
        )
        val vc2 = VerifiableCredential(
            id = "urn:uuid:b", type = listOf("VerifiableCredential"),
            issuer = "did:ssdid:issuer", issuanceDate = "2026-01-01T00:00:00Z",
            credentialSubject = CredentialSubject(id = "did:ssdid:holder"),
            credentialStatus = status, proof = testProof
        )

        manager.checkRevocation(vc1)
        manager.checkRevocation(vc2)

        coVerify(exactly = 1) { fetcher.fetch(any()) }
    }
}
