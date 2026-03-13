package my.ssdid.wallet.domain.revocation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import my.ssdid.wallet.domain.model.VerifiableCredential
import java.util.concurrent.ConcurrentHashMap

enum class RevocationStatus { VALID, REVOKED, UNKNOWN }

fun interface StatusListFetcher {
    suspend fun fetch(url: String): Result<StatusListCredential>
}

class RevocationManager(private val fetcher: StatusListFetcher) {

    private val cache = ConcurrentHashMap<String, StatusListCredential>()
    private val fetchMutex = Mutex()

    suspend fun checkRevocation(credential: VerifiableCredential): RevocationStatus {
        val status = credential.credentialStatus ?: return RevocationStatus.VALID

        val listUrl = status.statusListCredential
        val index = status.statusListIndex.toIntOrNull()
            ?: return RevocationStatus.UNKNOWN

        val statusListCred = cache[listUrl] ?: fetchMutex.withLock {
            cache[listUrl] ?: run {
                val result = fetcher.fetch(listUrl)
                if (result.isFailure) return RevocationStatus.UNKNOWN
                result.getOrThrow().also { cache[listUrl] = it }
            }
        }

        return try {
            if (BitstringParser.isRevoked(statusListCred.credentialSubject.encodedList, index))
                RevocationStatus.REVOKED
            else
                RevocationStatus.VALID
        } catch (_: Exception) {
            RevocationStatus.UNKNOWN
        }
    }

    fun invalidateCache() {
        cache.clear()
    }
}
