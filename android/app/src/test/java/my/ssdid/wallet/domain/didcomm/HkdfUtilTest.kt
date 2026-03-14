package my.ssdid.wallet.domain.didcomm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HkdfUtilTest {

    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /**
     * RFC 5869 Appendix A - Test Case 1
     * IKM  = 0x0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b (22 octets)
     * salt = 0x000102030405060708090a0b0c (13 octets)
     * info = 0xf0f1f2f3f4f5f6f7f8f9 (10 octets)
     * L    = 42
     * OKM  = 0x3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865
     */
    @Test
    fun `RFC 5869 test case 1`() {
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expectedOkm = hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865")

        val okm = HkdfUtil.deriveKey(ikm, salt, info, 42)

        assertThat(okm).isEqualTo(expectedOkm)
    }

    /**
     * RFC 5869 Appendix A - Test Case 2
     * Longer IKM, salt, info, and output.
     */
    @Test
    fun `RFC 5869 test case 2`() {
        val ikm = hex(
            "000102030405060708090a0b0c0d0e0f" +
            "101112131415161718191a1b1c1d1e1f" +
            "202122232425262728292a2b2c2d2e2f" +
            "303132333435363738393a3b3c3d3e3f" +
            "404142434445464748494a4b4c4d4e4f"
        )
        val salt = hex(
            "606162636465666768696a6b6c6d6e6f" +
            "707172737475767778797a7b7c7d7e7f" +
            "808182838485868788898a8b8c8d8e8f" +
            "909192939495969798999a9b9c9d9e9f" +
            "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf"
        )
        val info = hex(
            "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
            "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf" +
            "d0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
            "e0e1e2e3e4e5e6e7e8e9eaebecedeeef" +
            "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff"
        )
        val expectedOkm = hex(
            "b11e398dc80327a1c8e7f78c596a4934" +
            "4f012eda2d4efad8a050cc4c19afa97c" +
            "59045a99cac7827271cb41c65e590e09" +
            "da3275600c2f09b8367793a9aca3db71" +
            "cc30c58179ec3e87c14c01d5c1f3434f" +
            "1d87"
        )

        val okm = HkdfUtil.deriveKey(ikm, salt, info, 82)

        assertThat(okm).isEqualTo(expectedOkm)
    }

    /**
     * RFC 5869 Appendix A - Test Case 3
     * Zero-length salt and info.
     */
    @Test
    fun `RFC 5869 test case 3`() {
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = ByteArray(0)
        val info = ByteArray(0)
        val expectedOkm = hex(
            "8da4e775a563c18f715f802a063c5a31" +
            "b8a11f5c5ee1879ec3454e5f3c738d2d" +
            "9d201395faa4b61a96c8"
        )

        val okm = HkdfUtil.deriveKey(ikm, salt, info, 42)

        assertThat(okm).isEqualTo(expectedOkm)
    }

    @Test
    fun `RFC 5869 test case 3 using omitted salt parameter`() {
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val expectedOkm = hex(
            "8da4e775a563c18f715f802a063c5a31" +
            "b8a11f5c5ee1879ec3454e5f3c738d2d" +
            "9d201395faa4b61a96c8"
        )
        // Salt parameter intentionally omitted — exercises the ByteArray(32) default
        val okm = HkdfUtil.deriveKey(ikm, info = ByteArray(0), length = 42)
        assertThat(okm).isEqualTo(expectedOkm)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deriveKey rejects zero length`() {
        HkdfUtil.deriveKey(ByteArray(32), info = ByteArray(0), length = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deriveKey rejects length exceeding maximum`() {
        HkdfUtil.deriveKey(ByteArray(32), info = ByteArray(0), length = 255 * 32 + 1)
    }

    @Test
    fun `deriveKey at maximum length succeeds`() {
        val okm = HkdfUtil.deriveKey(ByteArray(32), info = ByteArray(0), length = 255 * 32)
        assertThat(okm).hasLength(255 * 32)
    }

    @Test
    fun `deriveKey with DIDComm info produces 32-byte key`() {
        val ikm = ByteArray(32) { it.toByte() }
        val key = HkdfUtil.deriveKey(ikm, info = "DIDComm-authcrypt".toByteArray(), length = 32)
        assertThat(key).hasLength(32)
    }
}
