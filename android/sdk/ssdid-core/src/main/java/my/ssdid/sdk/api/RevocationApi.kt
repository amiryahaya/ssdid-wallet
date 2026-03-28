package my.ssdid.sdk.api

import my.ssdid.sdk.domain.model.VerifiableCredential
import my.ssdid.sdk.domain.revocation.RevocationManager
import my.ssdid.sdk.domain.revocation.RevocationStatus

class RevocationApi internal constructor(private val manager: RevocationManager) {
    suspend fun checkStatus(credential: VerifiableCredential): RevocationStatus =
        manager.checkRevocation(credential)
    fun invalidateCache() = manager.invalidateCache()
}
