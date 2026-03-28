package my.ssdid.sdk.domain.oid4vp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import my.ssdid.sdk.domain.util.parseQueryParam

data class AuthorizationRequest(
    val clientId: String,
    val requestUri: String? = null,
    val responseUri: String? = null,
    val nonce: String? = null,
    val state: String? = null,
    val responseType: String? = null,
    val responseMode: String? = null,
    val presentationDefinition: JsonObject? = null,
    val dcqlQuery: JsonObject? = null
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(uriString: String): Result<AuthorizationRequest> = runCatching {
            val clientId = parseQueryParam(uriString, "client_id")
                ?: throw IllegalArgumentException("Missing required parameter: client_id")

            val requestUri = parseQueryParam(uriString, "request_uri")

            if (requestUri != null) {
                require(requestUri.startsWith("https://")) {
                    "request_uri must be HTTPS: $requestUri"
                }
                return@runCatching AuthorizationRequest(
                    clientId = clientId,
                    requestUri = requestUri
                )
            }

            val responseUri = parseQueryParam(uriString, "response_uri")
            val nonce = parseQueryParam(uriString, "nonce")
            val state = parseQueryParam(uriString, "state")
            val responseType = parseQueryParam(uriString, "response_type")
            val responseMode = parseQueryParam(uriString, "response_mode")

            require(responseMode == "direct_post") {
                "response_mode must be direct_post, got: $responseMode"
            }
            require(responseUri != null) { "Missing required parameter: response_uri" }
            require(responseUri.startsWith("https://")) {
                "response_uri must be HTTPS: $responseUri"
            }
            require(nonce != null) { "Missing required parameter: nonce" }

            val pdRaw = parseQueryParam(uriString, "presentation_definition")
            val dcqlRaw = parseQueryParam(uriString, "dcql_query")

            val pd = pdRaw?.let { json.parseToJsonElement(it) as? JsonObject }
            val dcql = dcqlRaw?.let { json.parseToJsonElement(it) as? JsonObject }

            require(pd != null || dcql != null) {
                "Must provide presentation_definition or dcql_query"
            }
            require(pd == null || dcql == null) {
                "Cannot provide both presentation_definition and dcql_query"
            }

            AuthorizationRequest(
                clientId = clientId,
                responseUri = responseUri,
                nonce = nonce,
                state = state,
                responseType = responseType,
                responseMode = responseMode,
                presentationDefinition = pd,
                dcqlQuery = dcql
            )
        }

        fun parseJson(jsonString: String): Result<AuthorizationRequest> = runCatching {
            val obj = json.parseToJsonElement(jsonString) as JsonObject

            val clientId = obj["client_id"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing client_id")

            val responseUri = obj["response_uri"]?.jsonPrimitive?.content
            val nonce = obj["nonce"]?.jsonPrimitive?.content
            val state = obj["state"]?.jsonPrimitive?.content
            val responseMode = obj["response_mode"]?.jsonPrimitive?.content

            require(responseMode == "direct_post") {
                "response_mode must be direct_post, got: $responseMode"
            }
            require(responseUri != null) { "Missing response_uri" }
            require(responseUri.startsWith("https://")) {
                "response_uri must be HTTPS: $responseUri"
            }
            require(nonce != null) { "Missing nonce" }

            val pd = obj["presentation_definition"] as? JsonObject
            val dcql = obj["dcql_query"] as? JsonObject

            require(pd != null || dcql != null) {
                "Must provide presentation_definition or dcql_query"
            }

            AuthorizationRequest(
                clientId = clientId,
                responseUri = responseUri,
                nonce = nonce,
                state = state,
                responseMode = responseMode,
                presentationDefinition = pd,
                dcqlQuery = dcql
            )
        }
    }
}
