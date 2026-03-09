/*
 * KAZ-SIGN: Post-Quantum Digital Signature Algorithm
 * Version 3.0.0
 *
 * Unified implementation supporting security levels 128, 192, and 256
 * Uses OpenSSL BIGNUM with constant-time operations
 *
 * Supports both compile-time and runtime security level selection.
 *
 * NIST-developed software is provided by NIST as a public service.
 */

#ifndef KAZ_SIGN_H
#define KAZ_SIGN_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ============================================================================
 * Version Information
 * ============================================================================ */

#define KAZ_SIGN_VERSION_MAJOR     3
#define KAZ_SIGN_VERSION_MINOR     0
#define KAZ_SIGN_VERSION_PATCH     0
#define KAZ_SIGN_VERSION_STRING    "3.0.0"

/* Version as single integer: (major * 10000) + (minor * 100) + patch */
#define KAZ_SIGN_VERSION_NUMBER    30000

/* ============================================================================
 * Runtime Security Level Selection (NEW in 2.1, extended in 3.0)
 * ============================================================================ */

/**
 * Security level enumeration for runtime selection
 */
typedef enum {
    KAZ_LEVEL_128 = 128,    /* 128-bit security (SHA-256) */
    KAZ_LEVEL_192 = 192,    /* 192-bit security (SHA-384) */
    KAZ_LEVEL_256 = 256     /* 256-bit security (SHA-512) */
} kaz_sign_level_t;

/**
 * Security level parameters (read-only, for introspection)
 */
typedef struct {
    int level;                  /* Security level (128, 192, 256) */
    const char *algorithm_name; /* Algorithm name string */
    size_t secret_key_bytes;    /* Secret key size (SK + v1 + v2) */
    size_t public_key_bytes;    /* Public key size (v1 + v2) */
    size_t hash_bytes;          /* Hash output size */
    size_t signature_overhead;  /* Signature size without message (S1 + S2) */
    size_t sk_bytes;            /* SK (private scalar) component size */
    size_t v1_bytes;            /* v1 public key component size */
    size_t v2_bytes;            /* v2 public key component size */
    size_t s1_bytes;            /* S1 signature component size */
    size_t s2_bytes;            /* S2 signature component size */
} kaz_sign_level_params_t;

/**
 * Get parameters for a security level
 *
 * @param level  Security level (KAZ_LEVEL_128, KAZ_LEVEL_192, or KAZ_LEVEL_256)
 * @return Pointer to level parameters, or NULL if invalid level
 */
const kaz_sign_level_params_t *kaz_sign_get_level_params(kaz_sign_level_t level);

/* ============================================================================
 * Compile-time Security Level Selection (Legacy, for backwards compatibility)
 * Set KAZ_SECURITY_LEVEL to 128, 192, or 256
 * ============================================================================ */

#ifndef KAZ_SECURITY_LEVEL
#define KAZ_SECURITY_LEVEL 128
#endif

/* Validate security level */
#if KAZ_SECURITY_LEVEL != 128 && KAZ_SECURITY_LEVEL != 192 && KAZ_SECURITY_LEVEL != 256
#error "KAZ_SECURITY_LEVEL must be 128, 192, or 256"
#endif

/* ============================================================================
 * Security Level 128 Parameters
 * ============================================================================ */
#if KAZ_SECURITY_LEVEL == 128

#define KAZ_SIGN_ALGNAME           "KAZ-SIGN-128"
#define KAZ_SIGN_SECRETKEYBYTES    98
#define KAZ_SIGN_PUBLICKEYBYTES    49
#define KAZ_SIGN_BYTES             32

#define KAZ_SIGN_SP_g1             "65537"
#define KAZ_SIGN_SP_g2             "65539"

#define KAZ_SIGN_SKBYTES           49
#define KAZ_SIGN_V1BYTES           26
#define KAZ_SIGN_V2BYTES           23
#define KAZ_SIGN_S1BYTES           49
#define KAZ_SIGN_S2BYTES           8

/* Hash function: SHA-256 for 128-bit security */
#define KAZ_SIGN_HASH_ALG          "SHA-256"

/* ============================================================================
 * Security Level 192 Parameters
 * ============================================================================ */
#elif KAZ_SECURITY_LEVEL == 192

#define KAZ_SIGN_ALGNAME           "KAZ-SIGN-192"
#define KAZ_SIGN_SECRETKEYBYTES    146
#define KAZ_SIGN_PUBLICKEYBYTES    73
#define KAZ_SIGN_BYTES             48

#define KAZ_SIGN_SP_g1             "65537"
#define KAZ_SIGN_SP_g2             "65539"

#define KAZ_SIGN_SKBYTES           73
#define KAZ_SIGN_V1BYTES           34
#define KAZ_SIGN_V2BYTES           39
#define KAZ_SIGN_S1BYTES           73
#define KAZ_SIGN_S2BYTES           8

/* Hash function: SHA-384 for 192-bit security */
#define KAZ_SIGN_HASH_ALG          "SHA-384"

/* ============================================================================
 * Security Level 256 Parameters
 * ============================================================================ */
#elif KAZ_SECURITY_LEVEL == 256

#define KAZ_SIGN_ALGNAME           "KAZ-SIGN-256"
#define KAZ_SIGN_SECRETKEYBYTES    194
#define KAZ_SIGN_PUBLICKEYBYTES    97
#define KAZ_SIGN_BYTES             64

#define KAZ_SIGN_SP_g1             "65537"
#define KAZ_SIGN_SP_g2             "65539"

#define KAZ_SIGN_SKBYTES           97
#define KAZ_SIGN_V1BYTES           42
#define KAZ_SIGN_V2BYTES           55
#define KAZ_SIGN_S1BYTES           97
#define KAZ_SIGN_S2BYTES           8

/* Hash function: SHA-512 for 256-bit security */
#define KAZ_SIGN_HASH_ALG          "SHA-512"

#endif /* KAZ_SECURITY_LEVEL */

/* ============================================================================
 * Derived Constants
 * ============================================================================ */

/* Total signature overhead (without message): S1 + S2 */
#define KAZ_SIGN_SIGNATURE_OVERHEAD (KAZ_SIGN_S1BYTES + KAZ_SIGN_S2BYTES)

/* Backend information */
#define KAZ_SIGN_BACKEND "OpenSSL (constant-time)"

/* ============================================================================
 * KazWire Encoding Constants (aligned with kaz-pqc-core-v2.0)
 * ============================================================================ */
#define KAZ_WIRE_MAGIC_HI      0x67
#define KAZ_WIRE_MAGIC_LO      0x52
#define KAZ_WIRE_VERSION       0x01

#define KAZ_WIRE_SIGN_128      0x01
#define KAZ_WIRE_SIGN_192      0x02
#define KAZ_WIRE_SIGN_256      0x03

#define KAZ_WIRE_TYPE_PRIV     0x01
#define KAZ_WIRE_TYPE_PUB      0x02
#define KAZ_WIRE_TYPE_SIG_DET  0x10
#define KAZ_WIRE_TYPE_SIG_ATT  0x11

#define KAZ_WIRE_HEADER_LEN    5

/* ============================================================================
 * Error Codes
 * ============================================================================ */

#define KAZ_SIGN_SUCCESS           0
#define KAZ_SIGN_ERROR_MEMORY     -1
#define KAZ_SIGN_ERROR_RNG        -2
#define KAZ_SIGN_ERROR_INVALID    -3
#define KAZ_SIGN_ERROR_VERIFY     -4
#define KAZ_SIGN_ERROR_DER        -5
#define KAZ_SIGN_ERROR_X509       -6
#define KAZ_SIGN_ERROR_P12        -7
#define KAZ_SIGN_ERROR_HASH       -8
#define KAZ_SIGN_ERROR_BUFFER     -9

/* ============================================================================
 * Random State Management
 * ============================================================================ */

/**
 * Initialize the global random state with proper entropy
 * MUST be called before any signing operations
 *
 * @return KAZ_SIGN_SUCCESS on success, error code otherwise
 */
int kaz_sign_init_random(void);

/**
 * Clear and free the global random state
 * Should be called when done with signing operations
 */
void kaz_sign_clear_random(void);

/**
 * Check if random state has been initialized
 *
 * @return 1 if initialized, 0 otherwise
 */
int kaz_sign_is_initialized(void);

/* ============================================================================
 * Core KAZ-SIGN API
 * ============================================================================ */

/**
 * Generate a KAZ-SIGN key pair
 *
 * @param pk  Output: public verification key (KAZ_SIGN_PUBLICKEYBYTES bytes)
 * @param sk  Output: secret signing key (KAZ_SIGN_SECRETKEYBYTES bytes)
 * @return KAZ_SIGN_SUCCESS on success, error code otherwise
 */
int kaz_sign_keypair(unsigned char *pk, unsigned char *sk);

/**
 * Sign a message
 *
 * @param sig      Output: signature (KAZ_SIGN_SIGNATURE_OVERHEAD + mlen bytes)
 * @param siglen   Output: length of signature
 * @param msg      Input: message to sign
 * @param msglen   Input: length of message
 * @param sk       Input: secret signing key
 * @return KAZ_SIGN_SUCCESS on success, error code otherwise
 */
int kaz_sign_signature(unsigned char *sig,
                       unsigned long long *siglen,
                       const unsigned char *msg,
                       unsigned long long msglen,
                       const unsigned char *sk);

/**
 * Verify a signature and extract the message
 *
 * @param msg      Output: extracted message
 * @param msglen   Output: length of extracted message
 * @param sig      Input: signature (signature || message)
 * @param siglen   Input: length of signature
 * @param pk       Input: public verification key
 * @return KAZ_SIGN_SUCCESS if valid, KAZ_SIGN_ERROR_VERIFY if invalid
 */
int kaz_sign_verify(unsigned char *msg,
                    unsigned long long *msglen,
                    const unsigned char *sig,
                    unsigned long long siglen,
                    const unsigned char *pk);

/**
 * Hash a message using the appropriate hash function for the security level
 *
 * @param msg     Input: message to hash
 * @param msglen  Input: length of message
 * @param hash    Output: hash value (KAZ_SIGN_BYTES bytes)
 * @return KAZ_SIGN_SUCCESS on success, error code otherwise
 */
int kaz_sign_hash(const unsigned char *msg,
                  unsigned long long msglen,
                  unsigned char *hash);

/* ============================================================================
 * Version API
 * ============================================================================ */

/**
 * Get the version string
 *
 * @return Version string (e.g., "3.0.0")
 */
const char *kaz_sign_version(void);

/**
 * Get the version number as integer
 *
 * @return Version number (major * 10000 + minor * 100 + patch)
 */
int kaz_sign_version_number(void);

/* ============================================================================
 * Runtime Security Level API (NEW in 2.1)
 *
 * These functions allow selecting the security level at runtime.
 * Use these for applications that need to support multiple security levels.
 * ============================================================================ */

/**
 * Initialize the library for a specific security level
 * Can be called multiple times with different levels.
 *
 * @param level  Security level (KAZ_LEVEL_128, KAZ_LEVEL_192, or KAZ_LEVEL_256)
 * @return KAZ_SIGN_SUCCESS on success, error code otherwise
 */
int kaz_sign_init_level(kaz_sign_level_t level);

/**
 * Clear resources for a specific security level
 *
 * @param level  Security level to clear
 */
void kaz_sign_clear_level(kaz_sign_level_t level);

/**
 * Clear resources for all security levels
 */
void kaz_sign_clear_all(void);

/**
 * Generate a key pair for a specific security level
 *
 * @param level  Security level
 * @param pk     Output: public key (size from kaz_sign_get_level_params)
 * @param sk     Output: secret key (size from kaz_sign_get_level_params)
 * @return KAZ_SIGN_SUCCESS on success, error code otherwise
 */
int kaz_sign_keypair_ex(kaz_sign_level_t level,
                        unsigned char *pk,
                        unsigned char *sk);

/**
 * Sign a message with a specific security level
 *
 * @param level   Security level
 * @param sig     Output: signature (overhead + msglen bytes)
 * @param siglen  Output: length of signature
 * @param msg     Input: message to sign
 * @param msglen  Input: length of message
 * @param sk      Input: secret key
 * @return KAZ_SIGN_SUCCESS on success, error code otherwise
 */
int kaz_sign_signature_ex(kaz_sign_level_t level,
                          unsigned char *sig,
                          unsigned long long *siglen,
                          const unsigned char *msg,
                          unsigned long long msglen,
                          const unsigned char *sk);

/**
 * Verify a signature with a specific security level
 *
 * @param level   Security level
 * @param msg     Output: extracted message
 * @param msglen  Output: length of extracted message
 * @param sig     Input: signature
 * @param siglen  Input: length of signature
 * @param pk      Input: public key
 * @return KAZ_SIGN_SUCCESS if valid, KAZ_SIGN_ERROR_VERIFY if invalid
 */
int kaz_sign_verify_ex(kaz_sign_level_t level,
                       unsigned char *msg,
                       unsigned long long *msglen,
                       const unsigned char *sig,
                       unsigned long long siglen,
                       const unsigned char *pk);

/**
 * Hash a message with the hash function for a specific security level
 *
 * @param level   Security level
 * @param msg     Input: message to hash
 * @param msglen  Input: length of message
 * @param hash    Output: hash value (size from kaz_sign_get_level_params)
 * @return KAZ_SIGN_SUCCESS on success, error code otherwise
 */
int kaz_sign_hash_ex(kaz_sign_level_t level,
                     const unsigned char *msg,
                     unsigned long long msglen,
                     unsigned char *hash);

/* ============================================================================
 * SHA3-256 Standalone API
 * ============================================================================ */

/**
 * Compute SHA3-256 hash of a message in one shot
 *
 * @param msg     Input: message to hash
 * @param msglen  Input: length of message in bytes
 * @param out     Output: 32-byte hash digest
 * @return KAZ_SIGN_SUCCESS on success, KAZ_SIGN_ERROR_HASH on failure
 */
int kaz_sha3_256(const unsigned char *msg,
                 unsigned long long msglen,
                 unsigned char *out);

/** Opaque context for incremental SHA3-256 hashing */
typedef struct kaz_sha3_ctx_st kaz_sha3_ctx_t;

/**
 * Initialize an incremental SHA3-256 context
 *
 * @return Allocated context, or NULL on failure
 */
kaz_sha3_ctx_t *kaz_sha3_256_init(void);

/**
 * Feed data into an incremental SHA3-256 context
 *
 * @param ctx     SHA3-256 context from kaz_sha3_256_init()
 * @param data    Input data
 * @param len     Length of input data
 * @return KAZ_SIGN_SUCCESS on success, KAZ_SIGN_ERROR_HASH on failure
 */
int kaz_sha3_256_update(kaz_sha3_ctx_t *ctx,
                        const unsigned char *data,
                        unsigned long long len);

/**
 * Finalize SHA3-256 and produce digest
 *
 * @param ctx     SHA3-256 context (consumed; do not reuse)
 * @param out     Output: 32-byte hash digest
 * @return KAZ_SIGN_SUCCESS on success, KAZ_SIGN_ERROR_HASH on failure
 */
int kaz_sha3_256_final(kaz_sha3_ctx_t *ctx,
                       unsigned char *out);

/**
 * Free a SHA3-256 context without finalizing
 *
 * @param ctx     SHA3-256 context to free (may be NULL)
 */
void kaz_sha3_256_free(kaz_sha3_ctx_t *ctx);

/* ============================================================================
 * Detached Signature API
 * ============================================================================ */

/**
 * Get the detached signature size for a security level
 *
 * @param level  Security level
 * @return Signature size in bytes, or 0 if level is invalid
 */
size_t kaz_sign_detached_sig_bytes(kaz_sign_level_t level);

/**
 * Create a detached signature (signature does not include the message)
 *
 * @param level   Security level
 * @param sig     Output: detached signature
 * @param siglen  Output: length of detached signature
 * @param msg     Input: message to sign
 * @param msglen  Input: length of message
 * @param sk      Input: secret signing key
 * @return KAZ_SIGN_SUCCESS on success, error code otherwise
 */
int kaz_sign_detached_ex(kaz_sign_level_t level,
                         unsigned char *sig,
                         unsigned long long *siglen,
                         const unsigned char *msg,
                         unsigned long long msglen,
                         const unsigned char *sk);

/**
 * Verify a detached signature
 *
 * @param level   Security level
 * @param sig     Input: detached signature
 * @param siglen  Input: length of detached signature
 * @param msg     Input: original message
 * @param msglen  Input: length of message
 * @param pk      Input: public verification key
 * @return KAZ_SIGN_SUCCESS if valid, KAZ_SIGN_ERROR_VERIFY if invalid
 */
int kaz_sign_verify_detached_ex(kaz_sign_level_t level,
                                const unsigned char *sig,
                                unsigned long long siglen,
                                const unsigned char *msg,
                                unsigned long long msglen,
                                const unsigned char *pk);

/**
 * Create a detached signature over a pre-hashed message
 *
 * @param level    Security level
 * @param sig      Output: detached signature
 * @param siglen   Output: length of detached signature
 * @param hash     Input: pre-computed hash digest
 * @param hashlen  Input: length of hash digest
 * @param sk       Input: secret signing key
 * @return KAZ_SIGN_SUCCESS on success, error code otherwise
 */
int kaz_sign_detached_prehashed_ex(kaz_sign_level_t level,
                                   unsigned char *sig,
                                   unsigned long long *siglen,
                                   const unsigned char *hash,
                                   unsigned long long hashlen,
                                   const unsigned char *sk);

/**
 * Verify a detached signature over a pre-hashed message
 *
 * @param level    Security level
 * @param sig      Input: detached signature
 * @param siglen   Input: length of detached signature
 * @param hash     Input: pre-computed hash digest
 * @param hashlen  Input: length of hash digest
 * @param pk       Input: public verification key
 * @return KAZ_SIGN_SUCCESS if valid, KAZ_SIGN_ERROR_VERIFY if invalid
 */
int kaz_sign_verify_detached_prehashed_ex(kaz_sign_level_t level,
                                          const unsigned char *sig,
                                          unsigned long long siglen,
                                          const unsigned char *hash,
                                          unsigned long long hashlen,
                                          const unsigned char *pk);

/* ============================================================================
 * DER Key Encoding API
 * ============================================================================ */

/**
 * Encode a public key to DER format
 *
 * @param level    Security level
 * @param pk       Input: raw public key
 * @param der      Output: DER-encoded public key (caller-allocated)
 * @param derlen   In/Out: on input, buffer size; on output, bytes written
 * @return KAZ_SIGN_SUCCESS on success, KAZ_SIGN_ERROR_DER or KAZ_SIGN_ERROR_BUFFER on failure
 */
int kaz_sign_pubkey_to_der(kaz_sign_level_t level,
                           const unsigned char *pk,
                           unsigned char *der,
                           unsigned long long *derlen);

/**
 * Decode a public key from DER format
 *
 * @param level    Security level
 * @param der      Input: DER-encoded public key
 * @param derlen   Input: length of DER data
 * @param pk       Output: raw public key (caller-allocated)
 * @return KAZ_SIGN_SUCCESS on success, KAZ_SIGN_ERROR_DER on failure
 */
int kaz_sign_pubkey_from_der(kaz_sign_level_t level,
                             const unsigned char *der,
                             unsigned long long derlen,
                             unsigned char *pk);

/**
 * Encode a private key to DER format
 *
 * @param level    Security level
 * @param sk       Input: raw secret key
 * @param der      Output: DER-encoded private key (caller-allocated)
 * @param derlen   In/Out: on input, buffer size; on output, bytes written
 * @return KAZ_SIGN_SUCCESS on success, KAZ_SIGN_ERROR_DER or KAZ_SIGN_ERROR_BUFFER on failure
 */
int kaz_sign_privkey_to_der(kaz_sign_level_t level,
                            const unsigned char *sk,
                            unsigned char *der,
                            unsigned long long *derlen);

/**
 * Decode a private key from DER format
 *
 * @param level    Security level
 * @param der      Input: DER-encoded private key
 * @param derlen   Input: length of DER data
 * @param sk       Output: raw secret key (caller-allocated)
 * @return KAZ_SIGN_SUCCESS on success, KAZ_SIGN_ERROR_DER on failure
 */
int kaz_sign_privkey_from_der(kaz_sign_level_t level,
                              const unsigned char *der,
                              unsigned long long derlen,
                              unsigned char *sk);

/* ============================================================================
 * X.509 Certificate API
 * ============================================================================ */

/**
 * Generate a PKCS#10 Certificate Signing Request (CSR)
 *
 * @param level      Security level
 * @param sk         Input: secret signing key
 * @param pk         Input: public key
 * @param subject    Input: subject distinguished name (e.g., "CN=test")
 * @param csr        Output: DER-encoded CSR (caller-allocated)
 * @param csrlen     In/Out: on input, buffer size; on output, bytes written
 * @return KAZ_SIGN_SUCCESS on success, KAZ_SIGN_ERROR_X509 on failure
 */
int kaz_sign_generate_csr(kaz_sign_level_t level,
                          const unsigned char *sk,
                          const unsigned char *pk,
                          const char *subject,
                          unsigned char *csr,
                          unsigned long long *csrlen);

/**
 * Verify a PKCS#10 CSR self-signature
 *
 * @param level      Security level
 * @param csr        Input: DER-encoded CSR
 * @param csrlen     Input: length of CSR
 * @return KAZ_SIGN_SUCCESS if valid, KAZ_SIGN_ERROR_VERIFY if invalid
 */
int kaz_sign_verify_csr(kaz_sign_level_t level,
                        const unsigned char *csr,
                        unsigned long long csrlen);

/**
 * Issue an X.509 certificate by signing a CSR
 *
 * @param level       Security level
 * @param issuer_sk   Input: issuer secret key
 * @param issuer_pk   Input: issuer public key
 * @param issuer_name Input: issuer distinguished name
 * @param csr         Input: DER-encoded CSR from subject
 * @param csrlen      Input: length of CSR
 * @param serial      Input: certificate serial number
 * @param days        Input: validity period in days
 * @param cert        Output: DER-encoded certificate (caller-allocated)
 * @param certlen     In/Out: on input, buffer size; on output, bytes written
 * @return KAZ_SIGN_SUCCESS on success, KAZ_SIGN_ERROR_X509 on failure
 */
int kaz_sign_issue_certificate(kaz_sign_level_t level,
                               const unsigned char *issuer_sk,
                               const unsigned char *issuer_pk,
                               const char *issuer_name,
                               const unsigned char *csr,
                               unsigned long long csrlen,
                               unsigned long long serial,
                               int days,
                               unsigned char *cert,
                               unsigned long long *certlen);

/**
 * Extract the public key from an X.509 certificate
 *
 * @param level      Security level
 * @param cert       Input: DER-encoded certificate
 * @param certlen    Input: length of certificate
 * @param pk         Output: extracted public key (caller-allocated)
 * @return KAZ_SIGN_SUCCESS on success, KAZ_SIGN_ERROR_X509 on failure
 */
int kaz_sign_cert_extract_pubkey(kaz_sign_level_t level,
                                 const unsigned char *cert,
                                 unsigned long long certlen,
                                 unsigned char *pk);

/**
 * Verify an X.509 certificate signature against an issuer public key
 *
 * @param level       Security level
 * @param cert        Input: DER-encoded certificate
 * @param certlen     Input: length of certificate
 * @param issuer_pk   Input: issuer public key
 * @return KAZ_SIGN_SUCCESS if valid, KAZ_SIGN_ERROR_VERIFY if invalid
 */
int kaz_sign_verify_certificate(kaz_sign_level_t level,
                                const unsigned char *cert,
                                unsigned long long certlen,
                                const unsigned char *issuer_pk);

/* ============================================================================
 * PKCS#12 Keystore API
 * ============================================================================ */

/**
 * Create a PKCS#12 keystore containing a key pair and optional certificate
 *
 * @param level      Security level
 * @param sk         Input: secret key
 * @param pk         Input: public key
 * @param cert       Input: DER-encoded certificate (may be NULL)
 * @param certlen    Input: length of certificate (0 if cert is NULL)
 * @param password   Input: password to protect the keystore
 * @param name       Input: friendly name for the key entry
 * @param p12        Output: PKCS#12 data (caller-allocated)
 * @param p12len     In/Out: on input, buffer size; on output, bytes written
 * @return KAZ_SIGN_SUCCESS on success, KAZ_SIGN_ERROR_P12 on failure
 */
int kaz_sign_create_p12(kaz_sign_level_t level,
                        const unsigned char *sk,
                        const unsigned char *pk,
                        const unsigned char *cert,
                        unsigned long long certlen,
                        const char *password,
                        const char *name,
                        unsigned char *p12,
                        unsigned long long *p12len);

/**
 * Load a key pair and certificate from a PKCS#12 keystore
 *
 * @param level      Security level
 * @param p12        Input: PKCS#12 data
 * @param p12len     Input: length of PKCS#12 data
 * @param password   Input: password to unlock the keystore
 * @param sk         Output: secret key (caller-allocated, may be NULL to skip)
 * @param pk         Output: public key (caller-allocated, may be NULL to skip)
 * @param cert       Output: DER-encoded certificate (caller-allocated, may be NULL)
 * @param certlen    In/Out: on input, buffer size; on output, bytes written (may be NULL)
 * @return KAZ_SIGN_SUCCESS on success, KAZ_SIGN_ERROR_P12 on failure
 */
int kaz_sign_load_p12(kaz_sign_level_t level,
                      const unsigned char *p12,
                      unsigned long long p12len,
                      const char *password,
                      unsigned char *sk,
                      unsigned char *pk,
                      unsigned char *cert,
                      unsigned long long *certlen);

/* ============================================================================
 * KazWire Encoding/Decoding API
 * ============================================================================ */

/**
 * Encode a public key to KazWire format (5-byte header + raw key)
 */
int kaz_sign_pubkey_to_wire(kaz_sign_level_t level,
                            const unsigned char *pk, size_t pk_len,
                            unsigned char *out, size_t *out_len);

/**
 * Decode a public key from KazWire format
 */
int kaz_sign_pubkey_from_wire(const unsigned char *wire, size_t wire_len,
                              kaz_sign_level_t *level,
                              unsigned char *pk, size_t *pk_len);

/**
 * Encode a private key to KazWire format (5-byte header + raw key)
 */
int kaz_sign_privkey_to_wire(kaz_sign_level_t level,
                             const unsigned char *sk, size_t sk_len,
                             unsigned char *out, size_t *out_len);

/**
 * Decode a private key from KazWire format
 */
int kaz_sign_privkey_from_wire(const unsigned char *wire, size_t wire_len,
                               kaz_sign_level_t *level,
                               unsigned char *sk, size_t *sk_len);

/**
 * Encode a detached signature to KazWire format (5-byte header + raw sig)
 */
int kaz_sign_sig_to_wire(kaz_sign_level_t level,
                         const unsigned char *sig, size_t sig_len,
                         unsigned char *out, size_t *out_len);

/**
 * Decode a detached signature from KazWire format
 */
int kaz_sign_sig_from_wire(const unsigned char *wire, size_t wire_len,
                           kaz_sign_level_t *level,
                           unsigned char *sig, size_t *sig_len);

#ifdef __cplusplus
}
#endif

#endif /* KAZ_SIGN_H */
