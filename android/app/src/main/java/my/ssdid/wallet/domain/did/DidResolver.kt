package my.ssdid.wallet.domain.did

import my.ssdid.wallet.domain.model.DidDocument

interface DidResolver {
    suspend fun resolve(did: String): Result<DidDocument>
}
