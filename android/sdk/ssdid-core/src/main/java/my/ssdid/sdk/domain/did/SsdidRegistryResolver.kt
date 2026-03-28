package my.ssdid.sdk.domain.did

import my.ssdid.sdk.domain.did.DidResolver
import my.ssdid.sdk.domain.model.DidDocument
import my.ssdid.sdk.domain.transport.RegistryApi

class SsdidRegistryResolver(private val registryApi: RegistryApi) : DidResolver {
    override suspend fun resolve(did: String): Result<DidDocument> = runCatching {
        registryApi.resolveDid(did)
    }
}
