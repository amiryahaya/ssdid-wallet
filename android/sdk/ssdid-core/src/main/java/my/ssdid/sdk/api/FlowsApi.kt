package my.ssdid.sdk.api

import my.ssdid.sdk.domain.SsdidClient
import my.ssdid.sdk.domain.model.Identity
import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.transport.dto.AuthenticateResponse
import my.ssdid.sdk.domain.transport.dto.TxSubmitResponse

class FlowsApi internal constructor(private val client: SsdidClient) {
    suspend fun registerWithService(identity: Identity, serviceUrl: String): Result<VerifiableCredential> =
        client.registerWithService(identity, serviceUrl)
    suspend fun authenticate(credential: VerifiableCredential, serviceUrl: String): Result<AuthenticateResponse> =
        client.authenticate(credential, serviceUrl)
    suspend fun fetchTransactionDetails(sessionToken: String, serverUrl: String): Result<Map<String, String>> =
        client.fetchTransactionDetails(sessionToken, serverUrl)
    suspend fun signTransaction(
        sessionToken: String,
        identity: Identity,
        transaction: Map<String, String>,
        serverUrl: String
    ): Result<TxSubmitResponse> =
        client.signTransaction(sessionToken, identity, transaction, serverUrl)
}
