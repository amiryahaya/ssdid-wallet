package my.ssdid.wallet.domain.did

import my.ssdid.wallet.domain.model.DidDocument

class MultiMethodResolver(
    private val ssdidResolver: SsdidRegistryResolver,
    private val keyResolver: DidKeyResolver,
    private val jwkResolver: DidJwkResolver
) : DidResolver {
    override suspend fun resolve(did: String): Result<DidDocument> {
        val method = did.removePrefix("did:").substringBefore(":")
        return when (method) {
            "ssdid" -> ssdidResolver.resolve(did)
            "key" -> keyResolver.resolve(did)
            "jwk" -> jwkResolver.resolve(did)
            else -> Result.failure(IllegalArgumentException("Unsupported DID method: did:$method"))
        }
    }
}
