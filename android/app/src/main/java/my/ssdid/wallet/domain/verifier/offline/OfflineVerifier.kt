package my.ssdid.wallet.domain.verifier.offline

import my.ssdid.sdk.domain.crypto.CryptoProvider
import my.ssdid.sdk.domain.crypto.Multibase
import my.ssdid.sdk.domain.model.*
import my.ssdid.wallet.domain.revocation.BitstringParser
import my.ssdid.wallet.domain.revocation.RevocationStatus
import my.ssdid.wallet.domain.settings.TtlProvider
import my.ssdid.wallet.domain.vault.VaultImpl
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.Instant

/**
 * Verifies credentials offline using cached verification bundles.
 * Falls back gracefully when bundles are stale or missing.
 */
class OfflineVerifier(
    private val classicalProvider: CryptoProvider,
    private val pqcProvider: CryptoProvider,
    private val bundleStore: BundleStore,
    private val ttlProvider: TtlProvider
) {
    private val canonicalJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Verify a credential using cached bundles.
     * @return OfflineVerificationResult with status and details
     */
    suspend fun verifyCredential(credential: VerifiableCredential): OfflineVerificationResult {
        // Check expiration
        credential.expirationDate?.let { exp ->
            try {
                if (Instant.now().isAfter(Instant.parse(exp))) {
                    return OfflineVerificationResult(
                        signatureValid = false,
                        revocationStatus = RevocationStatus.UNKNOWN,
                        bundleFresh = false,
                        error = "Credential expired at $exp"
                    )
                }
            } catch (_: Exception) { /* ignore parse errors */ }
        }

        val issuerDid = Did.fromKeyId(credential.proof.verificationMethod)
        val bundle = bundleStore.getBundle(issuerDid.value)
            ?: return OfflineVerificationResult(
                signatureValid = false,
                revocationStatus = RevocationStatus.UNKNOWN,
                bundleFresh = false,
                error = "No cached bundle for issuer ${issuerDid.value}"
            )

        val bundleFresh = !ttlProvider.isExpired(bundle.fetchedAt)

        // Verify signature using cached DID Document
        val signatureValid = try {
            val vm = bundle.didDocument.verificationMethod
                .find { it.id == credential.proof.verificationMethod }
                ?: throw IllegalArgumentException("Key not found in cached DID Document")

            val publicKey = Multibase.decode(vm.publicKeyMultibase)
            val algorithm = Algorithm.fromW3cType(vm.type)
                ?: throw IllegalArgumentException("Unknown verification method type: ${vm.type}")
            val provider = if (algorithm.isPostQuantum) pqcProvider else classicalProvider
            val signature = Multibase.decode(credential.proof.proofValue)
            val signedData = canonicalizeCredentialWithoutProof(credential)
            provider.verify(algorithm, publicKey, signature, signedData)
        } catch (e: Exception) {
            return OfflineVerificationResult(
                signatureValid = false,
                revocationStatus = RevocationStatus.UNKNOWN,
                bundleFresh = bundleFresh,
                error = "Signature verification failed: ${e.message}"
            )
        }

        // Check revocation using cached status list
        val revocationStatus = checkRevocationOffline(credential, bundle)

        return OfflineVerificationResult(
            signatureValid = signatureValid,
            revocationStatus = revocationStatus,
            bundleFresh = bundleFresh
        )
    }

    private fun checkRevocationOffline(
        credential: VerifiableCredential,
        bundle: VerificationBundle
    ): RevocationStatus {
        val status = credential.credentialStatus ?: return RevocationStatus.VALID
        val statusList = bundle.statusList ?: return RevocationStatus.UNKNOWN

        // S7: Validate that the credential's statusListCredential URL matches the cached bundle's id
        if (status.statusListCredential != statusList.id) return RevocationStatus.UNKNOWN

        // S2: Require a proof on the status list credential before trusting its bitstring
        if (statusList.proof == null) return RevocationStatus.UNKNOWN

        val index = status.statusListIndex.toIntOrNull() ?: return RevocationStatus.UNKNOWN

        return try {
            if (BitstringParser.isRevoked(statusList.credentialSubject.encodedList, index))
                RevocationStatus.REVOKED
            else
                RevocationStatus.VALID
        } catch (_: Exception) {
            RevocationStatus.UNKNOWN
        }
    }

    private fun canonicalizeCredentialWithoutProof(credential: VerifiableCredential): ByteArray {
        val fullJson = canonicalJson.encodeToString(credential)
        val jsonObj = canonicalJson.parseToJsonElement(fullJson).jsonObject.toMutableMap()
        jsonObj.remove("proof")
        return VaultImpl.canonicalJson(kotlinx.serialization.json.JsonObject(jsonObj)).toByteArray(Charsets.UTF_8)
    }
}

data class OfflineVerificationResult(
    val signatureValid: Boolean,
    val revocationStatus: RevocationStatus,
    val bundleFresh: Boolean,
    val error: String? = null
) {
    val isValid: Boolean
        get() = signatureValid && revocationStatus != RevocationStatus.REVOKED && error == null
}

/**
 * Storage for verification bundles.
 */
interface BundleStore {
    suspend fun saveBundle(bundle: VerificationBundle)
    suspend fun getBundle(issuerDid: String): VerificationBundle?
    suspend fun deleteBundle(issuerDid: String)
    suspend fun listBundles(): List<VerificationBundle>
}
