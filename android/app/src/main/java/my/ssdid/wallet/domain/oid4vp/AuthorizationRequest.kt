package my.ssdid.wallet.domain.oid4vp

import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AuthorizationRequest(
    val clientId: String,
    val responseType: String? = null,
    val responseMode: String? = null,
    val responseUri: String? = null,
    val nonce: String? = null,
    val state: String? = null,
    val presentationDefinition: String? = null,
    val dcqlQuery: String? = null,
    val requestUri: String? = null
) {
    companion object {
        fun parse(uriString: String): Result<AuthorizationRequest> = runCatching {
            val uri = Uri.parse(uriString)

            val clientId = uri.getQueryParameter("client_id")
                ?: throw IllegalArgumentException("Missing required parameter: client_id")

            val requestUri = uri.getQueryParameter("request_uri")

            // By-reference: only client_id and request_uri needed
            if (requestUri != null) {
                return@runCatching AuthorizationRequest(
                    clientId = clientId,
                    requestUri = requestUri
                )
            }

            // By-value: validate all required parameters
            val responseType = uri.getQueryParameter("response_type")
                ?: throw IllegalArgumentException("Missing required parameter: response_type")
            require(responseType == "vp_token") { "Unsupported response_type: $responseType" }

            val nonce = uri.getQueryParameter("nonce")
                ?: throw IllegalArgumentException("Missing required parameter: nonce")

            val responseMode = uri.getQueryParameter("response_mode") ?: "direct_post"
            require(responseMode == "direct_post") { "Unsupported response_mode: $responseMode" }

            val responseUri = uri.getQueryParameter("response_uri")
                ?: throw IllegalArgumentException("Missing required parameter: response_uri")
            require(responseUri.startsWith("https://")) {
                "response_uri must be HTTPS: $responseUri"
            }

            val presentationDefinition = uri.getQueryParameter("presentation_definition")
            val dcqlQuery = uri.getQueryParameter("dcql_query")

            if (presentationDefinition != null && dcqlQuery != null) {
                throw IllegalArgumentException(
                    "Request is ambiguous: both presentation_definition and dcql_query present"
                )
            }

            val state = uri.getQueryParameter("state")

            // Validate client_id is HTTPS URL or DID
            validateClientId(clientId)

            AuthorizationRequest(
                clientId = clientId,
                responseType = responseType,
                responseMode = responseMode,
                responseUri = responseUri,
                nonce = nonce,
                state = state,
                presentationDefinition = presentationDefinition,
                dcqlQuery = dcqlQuery
            )
        }

        fun parseJson(json: String): Result<AuthorizationRequest> = runCatching {
            val obj = Json.parseToJsonElement(json).jsonObject
            val clientId = obj["client_id"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing client_id in request object")
            AuthorizationRequest(
                clientId = clientId,
                responseType = obj["response_type"]?.jsonPrimitive?.content,
                responseMode = obj["response_mode"]?.jsonPrimitive?.content,
                responseUri = obj["response_uri"]?.jsonPrimitive?.content,
                nonce = obj["nonce"]?.jsonPrimitive?.content,
                state = obj["state"]?.jsonPrimitive?.content,
                presentationDefinition = obj["presentation_definition"]?.toString(),
                dcqlQuery = obj["dcql_query"]?.toString()
            )
        }

        private fun validateClientId(clientId: String) {
            val isHttps = clientId.startsWith("https://")
            val isDid = clientId.startsWith("did:")
            require(isHttps || isDid) { "client_id must be HTTPS URL or DID: $clientId" }
        }
    }
}
