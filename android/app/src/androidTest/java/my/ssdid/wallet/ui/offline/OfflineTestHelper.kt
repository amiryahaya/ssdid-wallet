package my.ssdid.wallet.ui.offline

import my.ssdid.wallet.domain.crypto.ClassicalProvider
import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.model.*
import my.ssdid.wallet.domain.revocation.StatusListCredential
import my.ssdid.wallet.domain.vault.VaultImpl
import my.ssdid.wallet.domain.verifier.offline.VerificationBundle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.zip.GZIPOutputStream

object OfflineTestHelper {

    private val classicalProvider = ClassicalProvider()

    private val canonicalJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Creates an Ed25519 key pair and returns (publicKeyMultibase, privateKey).
     * Uses Multibase 'u' prefix (base64url) to match OfflineVerifier's expectations.
     */
    fun createKeyPair(): Pair<String, ByteArray> {
        val keyPair = classicalProvider.generateKeyPair(Algorithm.ED25519)
        val publicKeyMultibase = Multibase.encode(keyPair.publicKey)
        return publicKeyMultibase to keyPair.privateKey
    }

    /**
     * Signs a VerifiableCredential with the given private key.
     * Matches the canonicalization used by OfflineVerifier (VaultImpl.canonicalJson).
     */
    fun createTestCredential(
        issuerDid: String,
        keyId: String,
        privateKey: ByteArray,
        expirationDate: String? = null,
        credentialStatus: CredentialStatus? = null
    ): VerifiableCredential {
        val subject = CredentialSubject(id = "did:ssdid:holder123")
        val now = Instant.now().toString()
        val credWithoutProof = VerifiableCredential(
            id = "urn:uuid:${java.util.UUID.randomUUID()}",
            type = listOf("VerifiableCredential"),
            issuer = issuerDid,
            issuanceDate = now,
            expirationDate = expirationDate,
            credentialSubject = subject,
            credentialStatus = credentialStatus,
            proof = Proof(
                type = "Ed25519Signature2020",
                created = now,
                verificationMethod = keyId,
                proofPurpose = "assertionMethod",
                proofValue = ""
            )
        )
        // Canonicalize using the same method as OfflineVerifier
        val canonical = canonicalizeWithoutProof(credWithoutProof)
        val signature = classicalProvider.sign(Algorithm.ED25519, privateKey, canonical)
        val proofValue = Multibase.encode(signature)

        return credWithoutProof.copy(
            proof = credWithoutProof.proof.copy(proofValue = proofValue)
        )
    }

    /**
     * Creates a VerificationBundle for the given issuer with configurable freshness.
     * freshnessRatio: 0.0 = just fetched, 1.0 = at TTL boundary, >1.0 = expired
     */
    fun createBundle(
        issuerDid: String,
        didDocument: DidDocument,
        freshnessRatio: Double = 0.1,
        ttlDays: Int = 7,
        statusList: StatusListCredential? = null
    ): VerificationBundle {
        val ttlSeconds = ttlDays.toLong() * 86400
        val ageSeconds = (ttlSeconds * freshnessRatio).toLong()
        val fetchedAt = Instant.now().minus(Duration.ofSeconds(ageSeconds))
        val expiresAt = fetchedAt.plus(Duration.ofSeconds(ttlSeconds))
        return VerificationBundle(
            issuerDid = issuerDid,
            didDocument = didDocument,
            statusList = statusList,
            fetchedAt = fetchedAt.toString(),
            expiresAt = expiresAt.toString()
        )
    }

    /**
     * Creates a DidDocument with an Ed25519 verification method.
     */
    fun createDidDocument(did: String, keyId: String, publicKeyMultibase: String): DidDocument {
        return DidDocument(
            context = listOf("https://www.w3.org/ns/did/v1"),
            id = did,
            controller = did,
            verificationMethod = listOf(
                VerificationMethod(
                    id = keyId,
                    type = "Ed25519VerificationKey2020",
                    controller = did,
                    publicKeyMultibase = publicKeyMultibase
                )
            ),
            authentication = listOf(keyId),
            assertionMethod = listOf(keyId),
            capabilityInvocation = listOf(keyId)
        )
    }

    /**
     * Creates a GZIP-compressed bitstring for revocation status list.
     */
    fun createStatusListBitstring(size: Int = 16384, revokedIndices: Set<Int> = emptySet()): String {
        val bytes = ByteArray(size / 8)
        for (index in revokedIndices) {
            val byteIndex = index / 8
            val bitIndex = 7 - (index % 8) // MSB-first per W3C spec
            if (byteIndex < bytes.size) {
                bytes[byteIndex] = (bytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
            }
        }
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(bytes) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray())
    }

    /**
     * Canonicalizes a credential without the proof field, matching OfflineVerifier's method.
     */
    private fun canonicalizeWithoutProof(credential: VerifiableCredential): ByteArray {
        val fullJson = canonicalJson.encodeToString(credential)
        val jsonObj = canonicalJson.parseToJsonElement(fullJson).jsonObject.toMutableMap()
        jsonObj.remove("proof")
        return VaultImpl.canonicalJson(JsonObject(jsonObj)).toByteArray(Charsets.UTF_8)
    }
}
