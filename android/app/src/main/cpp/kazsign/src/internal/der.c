/*
 * KAZ-SIGN: DER Key Encoding/Decoding
 * Version 3.0
 *
 * Implements SubjectPublicKeyInfo (X.509) and PrivateKeyInfo (PKCS#8)
 * DER encoding for KAZ-Sign public and private keys.
 *
 * Uses manual TLV (tag-length-value) construction for portability.
 *
 * OIDs (private enterprise arc):
 *   KAZ-SIGN-128: 1.3.6.1.4.1.99999.1.1
 *   KAZ-SIGN-192: 1.3.6.1.4.1.99999.1.2
 *   KAZ-SIGN-256: 1.3.6.1.4.1.99999.1.3
 */

#include <string.h>

#include "kaz/sign.h"
#include "kaz/security.h"

/* ============================================================================
 * OID Definitions
 * ============================================================================
 *
 * OID 1.3.6.1.4.1.99999.1.X
 *
 * BER/DER OID encoding:
 *   First two arcs combined: 1*40 + 3 = 43 = 0x2B
 *   6 -> 0x06
 *   1 -> 0x01
 *   4 -> 0x04
 *   1 -> 0x01
 *   99999 in base-128 varint:
 *     99999 = 0x1869F
 *     Split into 7-bit groups (from MSB): 0x06, 0x0D, 0x1F
 *     With continuation bits: 0x86, 0x8D, 0x1F
 *   1 -> 0x01
 *   X -> level-dependent final byte
 *
 * Full OID TLV (tag 0x06, length 0x0A = 10 value bytes):
 *   06 0A 2B 06 01 04 01 86 8D 1F 01 XX
 */

/* OID value bytes (without tag+length), last byte is level-dependent */
static const unsigned char OID_PREFIX[] = {
    0x2B, 0x06, 0x01, 0x04, 0x01, 0x86, 0x8D, 0x1F, 0x01
};
#define OID_PREFIX_LEN  9
#define OID_VALUE_LEN  10   /* OID_PREFIX_LEN + 1 byte for level */
#define OID_TLV_LEN    12   /* tag(1) + length(1) + OID_VALUE_LEN */

/* ============================================================================
 * Internal helpers
 * ============================================================================ */

/**
 * Get the OID final byte for a security level
 */
static int oid_level_byte(kaz_sign_level_t level)
{
    switch (level) {
        case KAZ_LEVEL_128: return 0x01;
        case KAZ_LEVEL_192: return 0x02;
        case KAZ_LEVEL_256: return 0x03;
        default: return -1;
    }
}

/**
 * Compute the number of bytes needed to DER-encode a length value.
 * Returns 1 for lengths < 128, or 1 + N where N is the number of
 * bytes needed for the length value itself.
 */
static size_t der_length_size(size_t len)
{
    if (len < 128) return 1;
    if (len < 256) return 2;          /* 0x81 NN */
    if (len < 65536) return 3;        /* 0x82 NN NN */
    return 4;                          /* 0x83 NN NN NN (up to 16M) */
}

/**
 * Write a DER length at `out`. Returns number of bytes written.
 */
static size_t der_write_length(unsigned char *out, size_t len)
{
    if (len < 128) {
        out[0] = (unsigned char)len;
        return 1;
    }
    if (len < 256) {
        out[0] = 0x81;
        out[1] = (unsigned char)len;
        return 2;
    }
    if (len < 65536) {
        out[0] = 0x82;
        out[1] = (unsigned char)(len >> 8);
        out[2] = (unsigned char)(len & 0xFF);
        return 3;
    }
    /* up to 16MB */
    out[0] = 0x83;
    out[1] = (unsigned char)(len >> 16);
    out[2] = (unsigned char)((len >> 8) & 0xFF);
    out[3] = (unsigned char)(len & 0xFF);
    return 4;
}

/**
 * Parse a DER length from `in` (max `avail` bytes).
 * Sets *len_out and returns bytes consumed, or 0 on error.
 */
static size_t der_read_length(const unsigned char *in, size_t avail, size_t *len_out)
{
    if (avail == 0) return 0;

    if (in[0] < 128) {
        *len_out = in[0];
        return 1;
    }

    size_t nbytes = in[0] & 0x7F;
    if (nbytes == 0 || nbytes > 3 || nbytes + 1 > avail) return 0;

    size_t val = 0;
    for (size_t i = 0; i < nbytes; i++) {
        val = (val << 8) | in[1 + i];
    }
    *len_out = val;
    return 1 + nbytes;
}

/**
 * Write the AlgorithmIdentifier SEQUENCE for a given level.
 * Format: SEQUENCE { OID }
 * Returns bytes written.
 */
static size_t write_algorithm_id(unsigned char *out, kaz_sign_level_t level)
{
    int lb = oid_level_byte(level);
    if (lb < 0) return 0;
    /* SEQUENCE tag */
    out[0] = 0x30;
    /* SEQUENCE length = OID_TLV_LEN */
    out[1] = (unsigned char)OID_TLV_LEN;
    /* OID tag */
    out[2] = 0x06;
    /* OID length */
    out[3] = (unsigned char)OID_VALUE_LEN;
    /* OID value */
    memcpy(out + 4, OID_PREFIX, OID_PREFIX_LEN);
    out[4 + OID_PREFIX_LEN] = (unsigned char)lb;

    return 2 + OID_TLV_LEN; /* SEQUENCE tag+len + OID TLV */
}

#define ALGID_LEN (2 + OID_TLV_LEN) /* 14 bytes */

/**
 * Verify that the AlgorithmIdentifier at `in` matches the expected level.
 * Returns ALGID_LEN on success, 0 on mismatch.
 */
static size_t verify_algorithm_id(const unsigned char *in, size_t avail,
                                   kaz_sign_level_t level)
{
    int lb = oid_level_byte(level);
    if (lb < 0) return 0;
    if (avail < ALGID_LEN) return 0;

    /* Check SEQUENCE tag + length */
    if (in[0] != 0x30 || in[1] != OID_TLV_LEN) return 0;
    /* Check OID tag + length */
    if (in[2] != 0x06 || in[3] != OID_VALUE_LEN) return 0;
    /* Check OID prefix */
    if (memcmp(in + 4, OID_PREFIX, OID_PREFIX_LEN) != 0) return 0;
    /* Check level byte */
    if (in[4 + OID_PREFIX_LEN] != (unsigned char)lb) return 0;

    return ALGID_LEN;
}

/* ============================================================================
 * Public Key DER Encoding (SubjectPublicKeyInfo)
 * ============================================================================
 *
 * SEQUENCE {
 *   SEQUENCE { OID }         -- AlgorithmIdentifier
 *   BIT STRING {             -- subjectPublicKey
 *     0x00                   -- unused bits
 *     raw_public_key_bytes
 *   }
 * }
 */

int kaz_sign_pubkey_to_der(kaz_sign_level_t level,
                           const unsigned char *pk,
                           unsigned char *der,
                           unsigned long long *derlen)
{
    if (!pk || !derlen) return KAZ_SIGN_ERROR_INVALID;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(level);
    if (!params) return KAZ_SIGN_ERROR_INVALID;

    size_t pk_bytes = params->public_key_bytes;

    /* BIT STRING content = 1 (unused-bits byte) + pk_bytes */
    size_t bitstr_content_len = 1 + pk_bytes;
    /* BIT STRING TLV: tag(1) + length_bytes + content */
    size_t bitstr_len_size = der_length_size(bitstr_content_len);
    size_t bitstr_tlv_len = 1 + bitstr_len_size + bitstr_content_len;

    /* Outer SEQUENCE content = AlgID + BIT STRING TLV */
    size_t seq_content_len = ALGID_LEN + bitstr_tlv_len;
    size_t seq_len_size = der_length_size(seq_content_len);
    size_t total_len = 1 + seq_len_size + seq_content_len;

    /* If der is NULL, just report the needed size */
    if (!der) {
        *derlen = (unsigned long long)total_len;
        return KAZ_SIGN_SUCCESS;
    }

    /* Check buffer size */
    if ((unsigned long long)total_len > *derlen) {
        *derlen = (unsigned long long)total_len;
        return KAZ_SIGN_ERROR_BUFFER;
    }

    unsigned char *p = der;

    /* Outer SEQUENCE */
    *p++ = 0x30;
    p += der_write_length(p, seq_content_len);

    /* AlgorithmIdentifier */
    p += write_algorithm_id(p, level);

    /* BIT STRING */
    *p++ = 0x03;
    p += der_write_length(p, bitstr_content_len);
    *p++ = 0x00; /* unused bits */
    memcpy(p, pk, pk_bytes);
    p += pk_bytes;

    *derlen = (unsigned long long)(p - der);
    return KAZ_SIGN_SUCCESS;
}

/* ============================================================================
 * Public Key DER Decoding
 * ============================================================================ */

int kaz_sign_pubkey_from_der(kaz_sign_level_t level,
                             const unsigned char *der,
                             unsigned long long derlen,
                             unsigned char *pk)
{
    if (!der || !pk) return KAZ_SIGN_ERROR_INVALID;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(level);
    if (!params) return KAZ_SIGN_ERROR_INVALID;

    size_t pk_bytes = params->public_key_bytes;
    const unsigned char *p = der;
    size_t remain = (size_t)derlen;

    /* Outer SEQUENCE */
    if (remain < 2 || p[0] != 0x30) return KAZ_SIGN_ERROR_DER;
    p++; remain--;

    size_t seq_len;
    size_t consumed = der_read_length(p, remain, &seq_len);
    if (consumed == 0) return KAZ_SIGN_ERROR_DER;
    p += consumed; remain -= consumed;

    if (seq_len > remain) return KAZ_SIGN_ERROR_DER;
    /* Limit remaining to sequence content */
    remain = seq_len;

    /* AlgorithmIdentifier */
    size_t aid_len = verify_algorithm_id(p, remain, level);
    if (aid_len == 0) return KAZ_SIGN_ERROR_DER;
    p += aid_len; remain -= aid_len;

    /* BIT STRING */
    if (remain < 2 || p[0] != 0x03) return KAZ_SIGN_ERROR_DER;
    p++; remain--;

    size_t bitstr_len;
    consumed = der_read_length(p, remain, &bitstr_len);
    if (consumed == 0) return KAZ_SIGN_ERROR_DER;
    p += consumed; remain -= consumed;

    if (bitstr_len > remain) return KAZ_SIGN_ERROR_DER;
    /* First byte is unused-bits count, must be 0 */
    if (bitstr_len < 1 || p[0] != 0x00) return KAZ_SIGN_ERROR_DER;
    p++; /* skip unused-bits byte */

    size_t key_len = bitstr_len - 1;
    if (key_len != pk_bytes) return KAZ_SIGN_ERROR_DER;

    memcpy(pk, p, pk_bytes);
    return KAZ_SIGN_SUCCESS;
}

/* ============================================================================
 * Private Key DER Encoding (PKCS#8 PrivateKeyInfo)
 * ============================================================================
 *
 * SEQUENCE {
 *   INTEGER 0                -- version
 *   SEQUENCE { OID }         -- AlgorithmIdentifier
 *   OCTET STRING {           -- privateKey
 *     raw_secret_key_bytes
 *   }
 * }
 */

int kaz_sign_privkey_to_der(kaz_sign_level_t level,
                            const unsigned char *sk,
                            unsigned char *der,
                            unsigned long long *derlen)
{
    if (!sk || !derlen) return KAZ_SIGN_ERROR_INVALID;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(level);
    if (!params) return KAZ_SIGN_ERROR_INVALID;

    size_t sk_bytes = params->secret_key_bytes;

    /* INTEGER 0: tag(1) + length(1) + value(1) = 3 bytes */
    size_t int_tlv_len = 3;

    /* OCTET STRING content = sk_bytes */
    size_t oct_len_size = der_length_size(sk_bytes);
    size_t oct_tlv_len = 1 + oct_len_size + sk_bytes;

    /* Outer SEQUENCE content = version INTEGER + AlgID + OCTET STRING */
    size_t seq_content_len = int_tlv_len + ALGID_LEN + oct_tlv_len;
    size_t seq_len_size = der_length_size(seq_content_len);
    size_t total_len = 1 + seq_len_size + seq_content_len;

    /* If der is NULL, just report the needed size */
    if (!der) {
        *derlen = (unsigned long long)total_len;
        return KAZ_SIGN_SUCCESS;
    }

    /* Check buffer size */
    if ((unsigned long long)total_len > *derlen) {
        *derlen = (unsigned long long)total_len;
        return KAZ_SIGN_ERROR_BUFFER;
    }

    unsigned char *p = der;

    /* Outer SEQUENCE */
    *p++ = 0x30;
    p += der_write_length(p, seq_content_len);

    /* Version INTEGER 0 */
    *p++ = 0x02; /* INTEGER tag */
    *p++ = 0x01; /* length = 1 */
    *p++ = 0x00; /* value = 0 */

    /* AlgorithmIdentifier */
    p += write_algorithm_id(p, level);

    /* OCTET STRING */
    *p++ = 0x04;
    p += der_write_length(p, sk_bytes);
    memcpy(p, sk, sk_bytes);
    p += sk_bytes;

    *derlen = (unsigned long long)(p - der);
    return KAZ_SIGN_SUCCESS;
}

/* ============================================================================
 * Private Key DER Decoding
 * ============================================================================ */

int kaz_sign_privkey_from_der(kaz_sign_level_t level,
                              const unsigned char *der,
                              unsigned long long derlen,
                              unsigned char *sk)
{
    if (!der || !sk) return KAZ_SIGN_ERROR_INVALID;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(level);
    if (!params) return KAZ_SIGN_ERROR_INVALID;

    size_t sk_bytes = params->secret_key_bytes;
    const unsigned char *p = der;
    size_t remain = (size_t)derlen;

    /* Outer SEQUENCE */
    if (remain < 2 || p[0] != 0x30) return KAZ_SIGN_ERROR_DER;
    p++; remain--;

    size_t seq_len;
    size_t consumed = der_read_length(p, remain, &seq_len);
    if (consumed == 0) return KAZ_SIGN_ERROR_DER;
    p += consumed; remain -= consumed;

    if (seq_len > remain) return KAZ_SIGN_ERROR_DER;
    remain = seq_len;

    /* Version INTEGER: must be 02 01 00 */
    if (remain < 3) return KAZ_SIGN_ERROR_DER;
    if (p[0] != 0x02 || p[1] != 0x01 || p[2] != 0x00) return KAZ_SIGN_ERROR_DER;
    p += 3; remain -= 3;

    /* AlgorithmIdentifier */
    size_t aid_len = verify_algorithm_id(p, remain, level);
    if (aid_len == 0) return KAZ_SIGN_ERROR_DER;
    p += aid_len; remain -= aid_len;

    /* OCTET STRING */
    if (remain < 2 || p[0] != 0x04) return KAZ_SIGN_ERROR_DER;
    p++; remain--;

    size_t oct_len;
    consumed = der_read_length(p, remain, &oct_len);
    if (consumed == 0) return KAZ_SIGN_ERROR_DER;
    p += consumed; remain -= consumed;

    if (oct_len > remain) return KAZ_SIGN_ERROR_DER;
    if (oct_len != sk_bytes) return KAZ_SIGN_ERROR_DER;

    memcpy(sk, p, sk_bytes);

    return KAZ_SIGN_SUCCESS;
}
