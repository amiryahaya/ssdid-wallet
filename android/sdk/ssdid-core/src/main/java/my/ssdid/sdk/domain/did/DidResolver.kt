package my.ssdid.sdk.domain.did

import my.ssdid.sdk.domain.model.DidDocument

interface DidResolver {
    suspend fun resolve(did: String): Result<DidDocument>
}
