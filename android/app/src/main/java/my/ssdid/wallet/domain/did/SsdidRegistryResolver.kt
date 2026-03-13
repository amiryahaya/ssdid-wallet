package my.ssdid.wallet.domain.did

import my.ssdid.wallet.domain.model.DidDocument
import my.ssdid.wallet.domain.transport.RegistryApi

class SsdidRegistryResolver(private val registryApi: RegistryApi) : DidResolver {
    override suspend fun resolve(did: String): Result<DidDocument> = runCatching {
        registryApi.resolveDid(did)
    }
}
