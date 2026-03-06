package my.ssdid.mobile.domain.transport.dto

import kotlinx.serialization.Serializable
import my.ssdid.mobile.domain.model.VerifiableCredential

@Serializable
data class RegisterStartRequest(
    val did: String,
    val key_id: String
)

@Serializable
data class RegisterStartResponse(
    val challenge: String,
    val server_did: String,
    val server_key_id: String,
    val server_signature: String
)

@Serializable
data class RegisterVerifyRequest(
    val did: String,
    val key_id: String,
    val signed_challenge: String
)

@Serializable
data class RegisterVerifyResponse(
    val credential: VerifiableCredential
)

@Serializable
data class AuthenticateRequest(
    val credential: VerifiableCredential
)

@Serializable
data class AuthenticateResponse(
    val session_token: String,
    val server_did: String,
    val server_key_id: String,
    val server_signature: String? = null
)

@Serializable
data class TxChallengeRequest(
    val session_token: String
)

@Serializable
data class TxChallengeResponse(
    val challenge: String,
    val transaction: Map<String, String> = emptyMap()
)

@Serializable
data class TxSubmitRequest(
    val session_token: String,
    val did: String,
    val key_id: String,
    val signed_challenge: String,
    val transaction: Map<String, String>
)

@Serializable
data class TxSubmitResponse(
    val transaction_id: String,
    val status: String
)
