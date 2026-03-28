package my.ssdid.sdk.domain.recovery.social

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShamirSecretSharingTest {

    @Test
    fun `split and combine 2-of-3 reconstructs secret`() {
        val secret = "my secret key".toByteArray()
        val shares = ShamirSecretSharing.split(secret, k = 2, n = 3)

        assertThat(shares).hasSize(3)
        // Any 2 shares should reconstruct
        val result = ShamirSecretSharing.combine(listOf(shares[0], shares[1]))
        assertThat(result).isEqualTo(secret)
    }

    @Test
    fun `split and combine 3-of-5 reconstructs secret`() {
        val secret = ByteArray(32) { it.toByte() }
        val shares = ShamirSecretSharing.split(secret, k = 3, n = 5)

        assertThat(shares).hasSize(5)
        val result = ShamirSecretSharing.combine(listOf(shares[0], shares[2], shares[4]))
        assertThat(result).isEqualTo(secret)
    }

    @Test
    fun `any k subset of n shares reconstructs`() {
        val secret = "recovery-key-bytes-1234567890AB".toByteArray()
        val shares = ShamirSecretSharing.split(secret, k = 3, n = 5)

        // Try all 3-combinations of 5 shares
        val combinations = listOf(
            listOf(0, 1, 2), listOf(0, 1, 3), listOf(0, 1, 4),
            listOf(0, 2, 3), listOf(0, 2, 4), listOf(0, 3, 4),
            listOf(1, 2, 3), listOf(1, 2, 4), listOf(1, 3, 4),
            listOf(2, 3, 4)
        )
        for (combo in combinations) {
            val subset = combo.map { shares[it] }
            val result = ShamirSecretSharing.combine(subset)
            assertThat(result).isEqualTo(secret)
        }
    }

    @Test
    fun `fewer than k shares produces wrong result`() {
        val secret = "my secret".toByteArray()
        val shares = ShamirSecretSharing.split(secret, k = 3, n = 5)

        // Only 2 shares with threshold 3 should NOT reconstruct correctly
        val result = ShamirSecretSharing.combine(listOf(shares[0], shares[1]))
        assertThat(result).isNotEqualTo(secret)
    }

    @Test
    fun `single byte secret works`() {
        val secret = byteArrayOf(42)
        val shares = ShamirSecretSharing.split(secret, k = 2, n = 3)
        val result = ShamirSecretSharing.combine(listOf(shares[1], shares[2]))
        assertThat(result).isEqualTo(secret)
    }

    @Test
    fun `large secret works (256 bytes)`() {
        val secret = ByteArray(256) { (it * 7 + 13).toByte() }
        val shares = ShamirSecretSharing.split(secret, k = 3, n = 5)
        val result = ShamirSecretSharing.combine(listOf(shares[0], shares[3], shares[4]))
        assertThat(result).isEqualTo(secret)
    }

    @Test
    fun `share indices are 1-indexed`() {
        val shares = ShamirSecretSharing.split(byteArrayOf(1, 2, 3), k = 2, n = 3)
        assertThat(shares.map { it.index }).containsExactly(1, 2, 3)
    }

    @Test
    fun `shares have same length as secret`() {
        val secret = ByteArray(48) { it.toByte() }
        val shares = ShamirSecretSharing.split(secret, k = 2, n = 5)
        for (share in shares) {
            assertThat(share.data).hasLength(48)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `split rejects empty secret`() {
        ShamirSecretSharing.split(ByteArray(0), k = 2, n = 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `split rejects threshold less than 2`() {
        ShamirSecretSharing.split(byteArrayOf(1), k = 1, n = 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `split rejects n less than k`() {
        ShamirSecretSharing.split(byteArrayOf(1), k = 3, n = 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `split rejects more than 255 shares`() {
        ShamirSecretSharing.split(byteArrayOf(1), k = 2, n = 256)
    }

    @Test
    fun `GF256 multiplication identity`() {
        assertThat(ShamirSecretSharing.gfMul(1, 42)).isEqualTo(42)
        assertThat(ShamirSecretSharing.gfMul(42, 1)).isEqualTo(42)
    }

    @Test
    fun `GF256 multiplication by zero`() {
        assertThat(ShamirSecretSharing.gfMul(0, 42)).isEqualTo(0)
        assertThat(ShamirSecretSharing.gfMul(42, 0)).isEqualTo(0)
    }

    @Test
    fun `GF256 inverse round trip`() {
        for (a in 1..255) {
            val inv = ShamirSecretSharing.gfInverse(a)
            assertThat(ShamirSecretSharing.gfMul(a, inv)).isEqualTo(1)
        }
    }

    @Test
    fun `2-of-2 minimum threshold works`() {
        val secret = "minimum".toByteArray()
        val shares = ShamirSecretSharing.split(secret, k = 2, n = 2)
        val result = ShamirSecretSharing.combine(shares)
        assertThat(result).isEqualTo(secret)
    }
}
