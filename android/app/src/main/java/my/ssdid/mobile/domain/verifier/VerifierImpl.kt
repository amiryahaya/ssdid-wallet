package my.ssdid.mobile.domain.verifier

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import my.ssdid.mobile.domain.crypto.CryptoProvider
import my.ssdid.mobile.domain.crypto.Multibase
import my.ssdid.mobile.domain.model.*
import my.ssdid.mobile.domain.transport.RegistryApi
import java.time.Instant

class VerifierImpl(
    private val registryApi: RegistryApi,
    private val classicalProvider: CryptoProvider,
    private val pqcProvider: CryptoProvider
) : Verifier {

    private val canonicalJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    override suspend fun resolveDid(did: String): Result<DidDocument> = runCatching {
        registryApi.resolveDid(did)
    }

    override suspend fun verifySignature(did: String, keyId: String, signature: ByteArray, data: ByteArray): Result<Boolean> = runCatching {
        val doc = resolveDid(did).getOrThrow()
        val vm = doc.verificationMethod.find { it.id == keyId }
            ?: throw IllegalArgumentException("Key $keyId not found in DID Document for $did")
        val publicKey = Multibase.decode(vm.publicKeyMultibase)
        val algorithm = algorithmFromW3cType(vm.type)
        val provider = if (algorithm.isPostQuantum) pqcProvider else classicalProvider
        provider.verify(algorithm, publicKey, signature, data)
    }

    override suspend fun verifyChallengeResponse(did: String, keyId: String, challenge: String, signedChallenge: String): Result<Boolean> {
        val signature = Multibase.decode(signedChallenge)
        return verifySignature(did, keyId, signature, challenge.toByteArray())
    }

    override suspend fun verifyCredential(credential: VerifiableCredential): Result<Boolean> = runCatching {
        // Check expiration
        credential.expirationDate?.let { exp ->
            if (Instant.now().isAfter(Instant.parse(exp))) {
                throw SecurityException("Credential expired at $exp")
            }
        }
        // Verify issuer signature
        val proof = credential.proof
        val issuerDid = Did.fromKeyId(proof.verificationMethod)
        val doc = resolveDid(issuerDid.value).getOrThrow()
        val vm = doc.verificationMethod.find { it.id == proof.verificationMethod }
            ?: throw IllegalArgumentException("Key ${proof.verificationMethod} not found in issuer DID Document")
        val publicKey = Multibase.decode(vm.publicKeyMultibase)
        val algorithm = algorithmFromW3cType(vm.type)
        val provider = if (algorithm.isPostQuantum) pqcProvider else classicalProvider
        val signature = Multibase.decode(proof.proofValue)
        // Canonical signed data = credential JSON with proof field removed
        val signedData = canonicalizeCredentialWithoutProof(credential)
        provider.verify(algorithm, publicKey, signature, signedData)
    }

    private fun canonicalizeCredentialWithoutProof(credential: VerifiableCredential): ByteArray {
        val fullJson = canonicalJson.encodeToString(credential)
        val jsonObj = canonicalJson.parseToJsonElement(fullJson).jsonObject.toMutableMap()
        jsonObj.remove("proof")
        val sorted = JsonObject(jsonObj.toSortedMap())
        return canonicalJson.encodeToString(sorted).toByteArray(Charsets.UTF_8)
    }

    private fun algorithmFromW3cType(type: String): Algorithm {
        return Algorithm.entries.firstOrNull { it.w3cType == type }
            ?: throw IllegalArgumentException("Unknown W3C verification method type: $type")
    }
}
