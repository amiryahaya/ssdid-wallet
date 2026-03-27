package my.ssdid.sdk.domain.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Thread-safe singleton that ensures the full BouncyCastle provider is installed exactly once.
 *
 * Android ships a stripped BouncyCastle that lacks PQC algorithms (ML-DSA, SLH-DSA).
 * This replaces it with the full bcprov-jdk18on provider at high priority.
 */
object BouncyCastleInstaller {
    @Volatile
    private var installed = false

    fun ensureInstalled() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            Security.removeProvider("BC")
            // Insert at end so Android's default provider (Conscrypt) handles
            // AES/GCM for hardware-backed AndroidKeyStore keys, while BC still
            // provides PQC algorithms (ML-DSA, SLH-DSA).
            Security.addProvider(BouncyCastleProvider())
            installed = true
        }
    }
}
