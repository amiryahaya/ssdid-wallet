package my.ssdid.wallet.domain.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Thread-safe singleton that ensures the full BouncyCastle provider is installed exactly once.
 *
 * Android ships a stripped BouncyCastle that lacks PQC algorithms (ML-DSA, SLH-DSA).
 * This replaces it with the full bcprov-jdk18on provider at high priority.
 */
internal object BouncyCastleInstaller {
    @Volatile
    private var installed = false

    fun ensureInstalled() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            Security.removeProvider("BC")
            Security.insertProviderAt(BouncyCastleProvider(), 1)
            installed = true
        }
    }
}
