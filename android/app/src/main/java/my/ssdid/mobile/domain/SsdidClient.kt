package my.ssdid.mobile.domain

import my.ssdid.mobile.domain.crypto.Multibase
import my.ssdid.mobile.domain.model.*
import my.ssdid.mobile.domain.transport.SsdidHttpClient
import my.ssdid.mobile.domain.transport.dto.*
import my.ssdid.mobile.domain.vault.Vault
import my.ssdid.mobile.domain.verifier.Verifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.util.Base64

class SsdidClient(
    private val vault: Vault,
    private val verifier: Verifier,
    private val httpClient: SsdidHttpClient
) {
    /** Flow 1: Create identity and publish DID to Registry */
    suspend fun initIdentity(name: String, algorithm: Algorithm): Result<Identity> = runCatching {
        val identity = vault.createIdentity(name, algorithm).getOrThrow()
        val didDoc = vault.buildDidDocument(identity.keyId).getOrThrow()
        val proof = vault.createProof(
            identity.keyId,
            mapOf("id" to didDoc.id),
            "assertionMethod"
        ).getOrThrow()
        httpClient.registry.registerDid(RegisterDidRequest(didDoc, proof))
        identity
    }

    /** Flow 2: Register with a service (mutual auth) */
    suspend fun registerWithService(identity: Identity, serverUrl: String): Result<VerifiableCredential> = runCatching {
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
        vc
    }

    /** Flow 3: Authenticate with a service */
    suspend fun authenticate(credential: VerifiableCredential, serverUrl: String): Result<AuthenticateResponse> = runCatching {
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
        serverApi.submitTransaction(
            TxSubmitRequest(
                session_token = sessionToken,
                did = identity.did,
                key_id = identity.keyId,
                signed_challenge = signedChallenge,
                transaction = transaction
            )
        )
    }
}
