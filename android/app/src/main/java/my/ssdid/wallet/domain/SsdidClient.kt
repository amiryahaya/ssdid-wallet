package my.ssdid.wallet.domain

import my.ssdid.wallet.domain.crypto.Multibase
import my.ssdid.wallet.domain.history.ActivityRepository
import my.ssdid.wallet.domain.model.*
import my.ssdid.wallet.domain.revocation.RevocationManager
import my.ssdid.wallet.domain.revocation.RevocationStatus
import my.ssdid.wallet.domain.transport.NetworkResult
import my.ssdid.wallet.domain.transport.SsdidHttpClient
import my.ssdid.wallet.domain.transport.dto.*
import my.ssdid.wallet.domain.vault.Vault
import my.ssdid.wallet.domain.verifier.Verifier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

class SsdidClient(
    private val vault: Vault,
    private val verifier: Verifier,
    private val httpClient: SsdidHttpClient,
    private val activityRepo: ActivityRepository,
    private val revocationManager: RevocationManager
) {
    private val wireJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private suspend fun logActivity(
        type: ActivityType,
        did: String,
        serviceUrl: String? = null,
        details: Map<String, String> = emptyMap()
    ) {
        try {
            activityRepo.addActivity(
                ActivityRecord(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    did = did,
                    serviceUrl = serviceUrl,
                    timestamp = Instant.now().toString(),
                    status = ActivityStatus.SUCCESS,
                    details = details
                )
            )
        } catch (_: Exception) {
            // Activity logging should never break the main flow
        }
    }

    private suspend fun <T> safeApiCall(block: suspend () -> T): NetworkResult<T> {
        return try {
            NetworkResult.Success(block())
        } catch (e: SocketTimeoutException) {
            NetworkResult.Timeout
        } catch (e: retrofit2.HttpException) {
            NetworkResult.fromHttpError(e.code(), e.message())
        } catch (e: IOException) {
            NetworkResult.NetworkError(e)
        }
    }

    /** Flow 1: Create identity and publish DID to Registry */
    suspend fun initIdentity(name: String, algorithm: Algorithm): Result<Identity> = runCatching {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "identity"; message = "Creating identity"; level = SentryLevel.INFO
            data["algorithm"] = algorithm.name
        })
        val identity = vault.createIdentity(name, algorithm).getOrThrow()

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "identity"; message = "Building DID document"; level = SentryLevel.INFO
            data["keyId"] = identity.keyId
        })
        val didDoc = vault.buildDidDocument(identity.keyId).getOrThrow()
        val didDocJson = wireJson.encodeToString(didDoc)
        val didDocJsonObject = wireJson.parseToJsonElement(didDocJson).jsonObject

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "identity"; message = "Creating proof"; level = SentryLevel.INFO
            data["proofPurpose"] = "assertionMethod"
        })
        val proof = vault.createProof(
            identity.keyId,
            didDocJsonObject,
            "assertionMethod"
        ).getOrThrow()

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "identity"; message = "Registering DID with registry"; level = SentryLevel.INFO
        })
        httpClient.registry.registerDid(RegisterDidRequest(didDoc, proof))
        logActivity(ActivityType.IDENTITY_CREATED, identity.did, details = mapOf("algorithm" to algorithm.name))
        identity
    }

    /** Update DID Document on Registry (used by rotation and recovery) */
    suspend fun updateDidDocument(keyId: String): Result<Unit> = runCatching {
        val identity = vault.getIdentity(keyId)
            ?: throw IllegalArgumentException("Identity not found: $keyId")
        val didDoc = vault.buildDidDocument(keyId).getOrThrow()
        val didDocJson = wireJson.encodeToString(didDoc)
        val didDocJsonObject = wireJson.parseToJsonElement(didDocJson).jsonObject
        val challengeResp = httpClient.registry.createChallenge(identity.did)
        val proof = vault.createProof(keyId, didDocJsonObject, "capabilityInvocation", challengeResp.challenge, challengeResp.domain).getOrThrow()
        httpClient.registry.updateDid(identity.did, UpdateDidRequest(didDoc, proof))
    }

    /** Deactivate DID — irreversible */
    suspend fun deactivateDid(keyId: String): Result<Unit> = runCatching {
        val identity = vault.getIdentity(keyId)
            ?: throw IllegalArgumentException("Identity not found: $keyId")
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "identity"; message = "Deactivating DID"; level = SentryLevel.WARNING
            data["algorithm"] = identity.algorithm.name
        })
        val deactivateData = wireJson.parseToJsonElement(
            """{"action":"deactivate","did":"${identity.did}"}"""
        ).jsonObject

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "identity"; message = "Requesting deactivation challenge"; level = SentryLevel.INFO
        })
        val challengeResp = httpClient.registry.createChallenge(identity.did)

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "identity"; message = "Creating deactivation proof"; level = SentryLevel.INFO
        })
        val proof = vault.createProof(keyId, deactivateData, "capabilityInvocation", challengeResp.challenge, challengeResp.domain).getOrThrow()

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "identity"; message = "Sending deactivation to registry"; level = SentryLevel.INFO
        })
        httpClient.registry.deactivateDid(identity.did, DeactivateDidRequest(proof))

        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "identity"; message = "Deleting local identity"; level = SentryLevel.INFO
        })
        vault.deleteIdentity(keyId).getOrThrow()
        logActivity(ActivityType.IDENTITY_CREATED, identity.did, details = mapOf("action" to "deactivated"))
    }

    /** Flow 2: Register with a service (mutual auth) */
    suspend fun registerWithService(identity: Identity, serverUrl: String): Result<VerifiableCredential> = runCatching {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "service"; message = "Registering with service"; level = SentryLevel.INFO
            data["serverUrl"] = serverUrl
        })
        val serverApi = httpClient.serverApi(serverUrl)

        // Step 1: Start registration — send our DID
        val startResp = serverApi.registerStart(
            RegisterStartRequest(identity.did, identity.keyId)
        )

        // Step 2: Verify server's signature (mutual auth)
        val serverVerified = verifier.verifyChallengeResponse(
            startResp.server_did,
            startResp.server_key_id,
            startResp.challenge,
            startResp.server_signature
        ).getOrThrow()

        if (!serverVerified) throw SecurityException("Server mutual authentication failed")

        // Step 3: Sign the challenge
        val signatureBytes = vault.sign(identity.keyId, startResp.challenge.toByteArray()).getOrThrow()
        val signedChallenge = Multibase.encode(signatureBytes)

        // Step 4: Complete registration — receive VC
        val verifyResp = serverApi.registerVerify(
            RegisterVerifyRequest(identity.did, identity.keyId, signedChallenge)
        )

        // Step 5: Store the credential
        val vc = verifyResp.credential
        vault.storeCredential(vc).getOrThrow()
        logActivity(ActivityType.SERVICE_REGISTERED, identity.did, serverUrl)
        logActivity(ActivityType.CREDENTIAL_RECEIVED, identity.did, serverUrl)
        vc
    }

    /** Flow 3: Authenticate with a service */
    suspend fun authenticate(credential: VerifiableCredential, serverUrl: String): Result<AuthenticateResponse> = runCatching {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "auth"; message = "Authenticating with service"; level = SentryLevel.INFO
            data["serverUrl"] = serverUrl
        })
        // Check revocation status before presenting credential
        val revocationStatus = revocationManager.checkRevocation(credential)
        if (revocationStatus == RevocationStatus.REVOKED) {
            throw SecurityException("Credential has been revoked")
        }

        val serverApi = httpClient.serverApi(serverUrl)
        val resp = serverApi.authenticate(AuthenticateRequest(credential))

        // Verify server's session token signature (mutual auth — mandatory)
        val serverSig = resp.server_signature
            ?: throw SecurityException("Server did not provide mutual authentication signature")
        val verified = verifier.verifyChallengeResponse(
            resp.server_did,
            resp.server_key_id,
            resp.session_token,
            serverSig
        ).getOrThrow()
        if (!verified) throw SecurityException("Server session token verification failed")
        logActivity(ActivityType.AUTHENTICATED, credential.credentialSubject.id, serverUrl)
        resp
    }

    /** Fetch transaction details from server for display before signing */
    suspend fun fetchTransactionDetails(sessionToken: String, serverUrl: String): Result<Map<String, String>> = runCatching {
        val serverApi = httpClient.serverApi(serverUrl)
        val resp = serverApi.requestChallenge(TxChallengeRequest(sessionToken))
        resp.transaction
    }

    /** Flow 4: Sign a transaction with challenge-response + TX binding */
    suspend fun signTransaction(
        sessionToken: String,
        identity: Identity,
        transaction: Map<String, String>,
        serverUrl: String
    ): Result<TxSubmitResponse> = runCatching {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "transaction"; message = "Signing transaction"; level = SentryLevel.INFO
            data["serverUrl"] = serverUrl; data["algorithm"] = identity.algorithm.name
        })
        val serverApi = httpClient.serverApi(serverUrl)

        // Step 1: Request fresh challenge
        val challengeResp = serverApi.requestChallenge(TxChallengeRequest(sessionToken))

        // Step 2: Hash transaction body with SHA3-256
        val txJson = Json.encodeToString(
            JsonObject.serializer(),
            JsonObject(transaction.mapValues { JsonPrimitive(it.value) })
        )
        val sha3 = MessageDigest.getInstance("SHA3-256")
        val txHash = sha3.digest(txJson.toByteArray())
        val txHashBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(txHash)

        // Step 3: Sign challenge || txHash (transaction binding)
        val payload = (challengeResp.challenge + txHashBase64).toByteArray()
        val signatureBytes = vault.sign(identity.keyId, payload).getOrThrow()
        val signedChallenge = Multibase.encode(signatureBytes)

        // Step 4: Submit signed transaction
        val response = serverApi.submitTransaction(
            TxSubmitRequest(
                session_token = sessionToken,
                did = identity.did,
                key_id = identity.keyId,
                signed_challenge = signedChallenge,
                transaction = transaction
            )
        )
        logActivity(ActivityType.TX_SIGNED, identity.did, serverUrl)
        response
    }
}
