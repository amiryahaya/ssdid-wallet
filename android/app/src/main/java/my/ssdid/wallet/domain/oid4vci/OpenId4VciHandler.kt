package my.ssdid.wallet.domain.oid4vci

import kotlinx.serialization.json.*
import my.ssdid.wallet.domain.mdoc.CborCodec
import my.ssdid.wallet.domain.mdoc.StoredMDoc
import my.ssdid.wallet.domain.sdjwt.StoredSdJwtVc
import my.ssdid.wallet.domain.vault.Vault

data class CredentialOfferReview(
    val offer: CredentialOffer,
    val metadata: IssuerMetadata,
    val credentialConfigNames: List<String>
)

sealed class IssuanceResult {
    data class Success(val credential: StoredSdJwtVc) : IssuanceResult()
    data class MDocSuccess(val mdoc: StoredMDoc) : IssuanceResult()
    class Deferred(
        val transactionId: String,
        val deferredEndpoint: String,
        val accessToken: String
    ) : IssuanceResult() {
        override fun toString() = "Deferred(transactionId=$transactionId, deferredEndpoint=$deferredEndpoint, accessToken=<redacted>)"
    }
    data class Failed(val error: String) : IssuanceResult()
}

class OpenId4VciHandler(
    private val metadataResolver: IssuerMetadataResolver,
    private val tokenClient: TokenClient,
    private val nonceManager: NonceManager,
    private val transport: OpenId4VciTransport,
    private val vault: Vault
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun processOffer(uri: String): Result<CredentialOfferReview> = runCatching {
        val offer = CredentialOffer.parseFromUri(uri).getOrThrow()
        val metadata = metadataResolver.resolve(offer.credentialIssuer).getOrThrow()

        CredentialOfferReview(
            offer = offer,
            metadata = metadata,
            credentialConfigNames = offer.credentialConfigurationIds
        )
    }

    suspend fun acceptOffer(
        offer: CredentialOffer,
        metadata: IssuerMetadata,
        selectedConfigId: String,
        txCode: String? = null,
        walletDid: String,
        keyId: String,
        algorithm: String,
        signer: (ByteArray) -> ByteArray
    ): Result<IssuanceResult> = runCatching {
        // Step 1: Token exchange
        val tokenResponse = if (offer.preAuthorizedCode != null) {
            tokenClient.exchangePreAuthorizedCode(
                metadata.tokenEndpoint,
                offer.preAuthorizedCode,
                txCode
            ).getOrThrow()
        } else {
            throw UnsupportedOperationException(
                "Authorization code flow requires browser interaction"
            )
        }

        // Step 2: Update nonce from token response
        tokenResponse.cNonce?.let { nonce ->
            nonceManager.update(nonce, tokenResponse.cNonceExpiresIn ?: 300)
        }

        // Step 3: Request credential
        requestCredential(
            metadata = metadata,
            accessToken = tokenResponse.accessToken,
            selectedConfigId = selectedConfigId,
            walletDid = walletDid,
            keyId = keyId,
            algorithm = algorithm,
            signer = signer
        )
    }

    private suspend fun requestCredential(
        metadata: IssuerMetadata,
        accessToken: String,
        selectedConfigId: String,
        walletDid: String,
        keyId: String,
        algorithm: String,
        signer: (ByteArray) -> ByteArray
    ): IssuanceResult {
        val currentNonce = nonceManager.current()
            ?: throw IllegalStateException("No c_nonce available")

        val proofJwt = ProofJwtBuilder.build(
            algorithm = algorithm,
            keyId = keyId,
            walletDid = walletDid,
            issuerUrl = metadata.credentialIssuer,
            nonce = currentNonce,
            signer = signer
        )

        // Determine format from credential configuration
        val configObj = metadata.credentialConfigurationsSupported[selectedConfigId]
        val format = configObj?.get("format")?.jsonPrimitive?.contentOrNull ?: "vc+sd-jwt"
        val isMDoc = format == "mso_mdoc"

        val requestBody = if (isMDoc) {
            val doctype = configObj?.get("doctype")?.jsonPrimitive?.contentOrNull
                ?: selectedConfigId
            buildJsonObject {
                put("format", "mso_mdoc")
                put("doctype", doctype)
                putJsonObject("proof") {
                    put("proof_type", "jwt")
                    put("jwt", proofJwt)
                }
            }
        } else {
            buildJsonObject {
                put("format", "vc+sd-jwt")
                putJsonObject("credential_definition") {
                    put("vct", selectedConfigId)
                }
                putJsonObject("proof") {
                    put("proof_type", "jwt")
                    put("jwt", proofJwt)
                }
            }
        }

        val responseStr = transport.postCredentialRequest(
            metadata.credentialEndpoint,
            accessToken,
            requestBody.toString()
        )

        val response = json.parseToJsonElement(responseStr).jsonObject

        // Check for credential in response
        val credential = response["credential"]?.jsonPrimitive?.contentOrNull
        if (credential != null) {
            // Update nonce from response if present
            response["c_nonce"]?.jsonPrimitive?.contentOrNull?.let { nonce ->
                nonceManager.update(
                    nonce,
                    response["c_nonce_expires_in"]?.jsonPrimitive?.intOrNull ?: 300
                )
            }

            if (isMDoc) {
                return handleMDocCredential(credential, selectedConfigId, keyId, metadata)
            }

            val storedVc = StoredSdJwtVc(
                id = java.util.UUID.randomUUID().toString(),
                compact = credential,
                issuer = metadata.credentialIssuer,
                subject = walletDid,
                type = selectedConfigId,
                claims = emptyMap(),
                disclosableClaims = emptyList(),
                issuedAt = System.currentTimeMillis() / 1000
            )

            vault.storeStoredSdJwtVc(storedVc)
            return IssuanceResult.Success(storedVc)
        }

        // Check for deferred issuance
        val transactionId = response["transaction_id"]?.jsonPrimitive?.contentOrNull
        if (transactionId != null) {
            return IssuanceResult.Deferred(
                transactionId = transactionId,
                deferredEndpoint = metadata.credentialEndpoint
                    .replace("/credential", "/deferred_credential"),
                accessToken = accessToken
            )
        }

        return IssuanceResult.Failed("Unexpected response from credential endpoint")
    }

    private suspend fun handleMDocCredential(
        credentialBase64: String,
        selectedConfigId: String,
        keyId: String,
        metadata: IssuerMetadata
    ): IssuanceResult.MDocSuccess {
        val cborBytes = java.util.Base64.getUrlDecoder().decode(credentialBase64)

        // Parse the IssuerSigned to extract element identifiers per namespace
        val issuerSigned = my.ssdid.wallet.domain.mdoc.MsoParser.parseIssuerSigned(cborBytes)
        val decoded = CborCodec.decodeMap(cborBytes)

        @Suppress("UNCHECKED_CAST")
        val docType = decoded["docType"] as? String ?: selectedConfigId

        // Extract element identifiers from parsed IssuerSignedItems
        val nameSpaces = issuerSigned.nameSpaces.mapValues { (_, items) ->
            items.map { it.elementIdentifier }
        }

        val configObj = metadata.credentialConfigurationsSupported[selectedConfigId]
        val configDoctype = configObj?.get("doctype")?.jsonPrimitive?.contentOrNull

        val storedMDoc = StoredMDoc(
            id = java.util.UUID.randomUUID().toString(),
            docType = configDoctype ?: docType,
            issuerSignedCbor = cborBytes,
            deviceKeyId = keyId,
            issuedAt = System.currentTimeMillis() / 1000,
            nameSpaces = nameSpaces
        )

        vault.storeMDoc(storedMDoc)
        return IssuanceResult.MDocSuccess(storedMDoc)
    }
}
