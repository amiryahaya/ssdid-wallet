package my.ssdid.wallet.domain.oid4vci

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NonceManagerTest {

    @Test
    fun updateAndRetrieveNonce() {
        val mgr = NonceManager()
        assertThat(mgr.current()).isNull()
        mgr.update("n-1", 300)
        assertThat(mgr.current()).isEqualTo("n-1")
        assertThat(mgr.isExpired()).isFalse()
    }

    @Test
    fun expiredWhenNoNonce() {
        val mgr = NonceManager()
        assertThat(mgr.isExpired()).isTrue()
    }

    @Test
    fun clearResetsNonce() {
        val mgr = NonceManager()
        mgr.update("n-2", 300)
        mgr.clear()
        assertThat(mgr.current()).isNull()
        assertThat(mgr.isExpired()).isTrue()
    }

    @Test
    fun updateReplacesExistingNonce() {
        val mgr = NonceManager()
        mgr.update("first", 300)
        mgr.update("second", 600)
        assertThat(mgr.current()).isEqualTo("second")
    }

    @Test
    fun expiredWithZeroLifetime() {
        val mgr = NonceManager()
        mgr.update("n-3", 0)
        assertThat(mgr.isExpired()).isTrue()
    }
}
