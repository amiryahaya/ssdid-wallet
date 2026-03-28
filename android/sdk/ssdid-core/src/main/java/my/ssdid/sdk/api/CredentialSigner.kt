package my.ssdid.sdk.api

/**
 * Signs data for credential issuance and presentation operations.
 * Implementations should use the identity's private key to produce a signature.
 *
 * @see IssuanceApi.acceptOffer
 * @see PresentationApi.submitPresentation
 */
fun interface CredentialSigner {
    /**
     * Sign the given data bytes and return the signature.
     * @param data the raw bytes to sign
     * @return the signature bytes
     */
    suspend fun sign(data: ByteArray): ByteArray
}
