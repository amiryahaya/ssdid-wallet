package my.ssdid.sdk.api

import my.ssdid.sdk.api.model.AuthSession
import my.ssdid.sdk.api.model.TransactionResult
import my.ssdid.sdk.domain.SsdidClient
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.model.VerifiableCredential

class FlowsApi internal constructor(private val client: SsdidClient) {
    suspend fun registerWithService(identity: Identity, serviceUrl: String): Result<VerifiableCredential> =
        client.registerWithService(identity, serviceUrl)

    suspend fun authenticate(credential: VerifiableCredential, serviceUrl: String): Result<AuthSession> =
        client.authenticate(credential, serviceUrl).map { resp ->
            AuthSession(sessionToken = resp.session_token, serverDid = resp.server_did)
        }

    suspend fun fetchTransactionDetails(sessionToken: String, serverUrl: String): Result<Map<String, String>> =
        client.fetchTransactionDetails(sessionToken, serverUrl)

    suspend fun signTransaction(
        sessionToken: String,
        identity: Identity,
        transaction: Map<String, String>,
        serverUrl: String
    ): Result<TransactionResult> =
        client.signTransaction(sessionToken, identity, transaction, serverUrl).map { resp ->
            TransactionResult(transactionId = resp.transaction_id, status = resp.status)
        }
}
