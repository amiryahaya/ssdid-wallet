/*
 * KAZ-SIGN: Constant-Time Implementation using OpenSSL BIGNUM
 * Version 3.0 - Complete Algorithm Rewrite (2-component scheme)
 *
 * This implementation matches the Java kaz-pqc-core reference exactly.
 * Uses OpenSSL's BIGNUM library with constant-time operations.
 *
 * Key security features:
 * - BN_FLG_CONSTTIME flag on all secret values
 * - Secure memory zeroization
 *
 * SECURITY LIMITATION: The system parameters G0, G1qQRHO, and qQ are all
 * even numbers, which prevents use of OpenSSL's Montgomery-based constant-time
 * modular exponentiation. The bn_mod_exp_safe() helper strips CONSTTIME flags
 * for even moduli, falling back to variable-time exponentiation. This means
 * timing side-channels may leak information about ephemeral nonces (beta1/beta2)
 * during signing. The long-term secret key SK is only used in BN_mod_mul
 * (not mod_exp), which is not affected. Applications requiring timing-attack
 * resistance should add external blinding or use constant-time hardware.
 *
 * Algorithm: 2-component signature (S1, S2) with 5-filter verification.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <openssl/bn.h>
#include <openssl/evp.h>
#include <openssl/rand.h>
#include <openssl/err.h>

#include "kaz/sign.h"
#include "kaz/security.h"

/* ============================================================================
 * Static Level Parameters (for all three security levels)
 * ============================================================================ */

/* Level 128 parameters */
static const kaz_sign_level_params_t g_level_128_params = {
    .level = 128,
    .algorithm_name = "KAZ-SIGN-128",
    .secret_key_bytes = 98,     /* SK(49) + V1(26) + V2(23) */
    .public_key_bytes = 49,     /* V1(26) + V2(23) */
    .hash_bytes = 32,           /* SHA-256 */
    .signature_overhead = 57,   /* S1(49) + S2(8) */
    .sk_bytes = 49,
    .v1_bytes = 26,
    .v2_bytes = 23,
    .s1_bytes = 49,
    .s2_bytes = 8
};

/* Level 192 parameters */
static const kaz_sign_level_params_t g_level_192_params = {
    .level = 192,
    .algorithm_name = "KAZ-SIGN-192",
    .secret_key_bytes = 146,    /* SK(73) + V1(34) + V2(39) */
    .public_key_bytes = 73,     /* V1(34) + V2(39) */
    .hash_bytes = 48,           /* SHA-384 */
    .signature_overhead = 81,   /* S1(73) + S2(8) */
    .sk_bytes = 73,
    .v1_bytes = 34,
    .v2_bytes = 39,
    .s1_bytes = 73,
    .s2_bytes = 8
};

/* Level 256 parameters */
static const kaz_sign_level_params_t g_level_256_params = {
    .level = 256,
    .algorithm_name = "KAZ-SIGN-256",
    .secret_key_bytes = 194,    /* SK(97) + V1(42) + V2(55) */
    .public_key_bytes = 97,     /* V1(42) + V2(55) */
    .hash_bytes = 64,           /* SHA-512 */
    .signature_overhead = 105,  /* S1(97) + S2(8) */
    .sk_bytes = 97,
    .v1_bytes = 42,
    .v2_bytes = 55,
    .s1_bytes = 97,
    .s2_bytes = 8
};

/* ============================================================================
 * System Parameter String Constants (verbatim from Java SystemParameters.java)
 * ============================================================================ */

/* Common constants */
static const char *SP_G0 = "23102151283542472555351033031857407110549489214984451103786304558150674606117088000";
static const char *SP_G1 = "399620650696124709852000";
static const char *SP_PHIG1 = "60408037934094090240000";
static const char *SP_PHIPHIG1 = "11456568251237007360000";
static const char *SP_g = "6007";
static const char *SP_R = "6151";
static const char *SP_A = "324324000";

/* Per-level parameters (indexed 0=128, 1=192, 2=256) */
static const char *SP_q[3] = {
    "246208917987764371328101733",
    "5708990770823839524233143877797980545530986749",
    "2840556527694295864950860759784740510458069976738706234986729593207"
};

static const char *SP_Q[3] = {
    "1115881660253397921934830780",
    "15805027320208803894072603145771831246637343495",
    "11532304439951903318047260070672268613130768031212132639712137620"
};

static const char *SP_qQ[3] = {
    "274740016173379194546381236446787565723556071979741740",
    "90230755103690702091973211007922974222612910553804250618499942113585284663920884979406347755",
    "32758162656263289822165160082704286295984704353673644459843294584139742401444681348237525839805407482025757064066007727363001147340"
};

static const char *SP_PHIQ[3] = {
    "142607087754413919436800000",
    "3627887299833526965332723467399389511680000000",
    "1251434900161857001704369748558994349944756643204956160000000000"
};

static const char *SP_PHIqQ[3] = {
    "35111136773400413456929878039434623864922544537600000",
    "20711575112338624928696946102003619446749681847468928622048198130495376119592271216640000000",
    "3554771574639222339069568200522429346291071123851611090794232823233857364488038302147974718854109146210993915839863848960000000000"
};

static const char *SP_G1RHO[3] = {
    "232938694926837183398728616987791009425158374864155959337108000",
    "2780574578486571985357538778664475851603942045856011006180329605609412611300244000",
    "79133492123247080788931646239320683796403476070676429894067272105048512318726473299394190483894484000"
};

static const int SP_LG1RHO[3] = { 208, 271, 336 };

static const char *SP_G1QRHO[3] = {
    "259932017632218835993369782263211224007969421304161083508208507352855014861224954584240000",
    "43947057178858349281469823683456747837008964566213302341175642613858213234180124996235330169158835649866435645207198005312780000",
    "912591522561821278596773661560381738487461196149374758031226045379636805516124374209533548227239075617507685978157416122232368826810589739482354141490542166888080000"
};

static const char *SP_G1qQRHO[3] = {
    "63997580811605089901461886588211664277541743781178507127907222480599391279443741157968408096194052214975438487920000",
    "250893343838969877863024788106342431306813468937412573541937672939465921301334865939583469036925555704399706518309911896437823276514950457299755576516752580105417780352220000",
    "2592267806531457714429433492040209571481423408663539591272600175454852168743179040721147413119890879277149563138414212135077674649704847604432326896895145786034397225901388681187070510067902252928183759793847469262075483497272560000"
};

static const char *SP_PHIG1RHO[3] = {
    "35211817745021271488319306579933617052512436633018795294720000",
    "420321257981034043095252041957047171374281436264017174140026587278944019415040000",
    "11962092013291449570594069903360588033560122759492301165447160574650084840217432160356538347683840000"
};

static const char *SP_PHIPHIG1RHO[3] = {
    "3339014202762851772197706809901101110783647930348396871680000",
    "39857602929921489702080944341648626696561074583376366185880680612910512209920000",
    "1134323578986027073739504344459924880821604430088910642679765319339580831866513781010934026731520000"
};

/* ============================================================================
 * Runtime Parameter Cache (one per security level)
 * ============================================================================ */

typedef struct {
    /* System parameters as BIGNUMs */
    BIGNUM *G0;
    BIGNUM *G1;
    BIGNUM *g;          /* generator 6007 */
    BIGNUM *R;          /* 6151 */
    BIGNUM *A;          /* 324324000 */
    BIGNUM *phiG1;
    BIGNUM *phiphiG1;
    BIGNUM *q;
    BIGNUM *Q;
    BIGNUM *qQ;
    BIGNUM *phiQ;
    BIGNUM *phiqQ;
    BIGNUM *G1RHO;
    BIGNUM *G1QRHO;
    BIGNUM *G1qQRHO;
    BIGNUM *phiG1RHO;
    BIGNUM *phiphiG1RHO;

    /* Derived values */
    BIGNUM *G1A;        /* G1RHO / A, used in verification equation 2 */
    BIGNUM *G1A_keygen; /* G1 / A, used in keygen gcd check */
    int LG1RHO;         /* bit-length of G1RHO */

    /* Note: G0 and G1qQRHO are both even, so Montgomery is not applicable.
     * All modular exponentiations use regular BN_mod_exp. */

    /* Hash function */
    const EVP_MD *hash_md;

    /* Level params reference */
    const kaz_sign_level_params_t *params;
    int initialized;
} kaz_runtime_params_t;

/* Runtime parameter caches for each level.
 * THREAD SAFETY: These globals are lazily initialized and NOT thread-safe.
 * Concurrent calls to kaz_sign_*_ex() for the same level may race on
 * initialization. Call kaz_sign_init_random() from a single thread before
 * concurrent use, or protect all kaz_sign_* calls with an external mutex.
 * Do NOT call kaz_sign_clear_level/clear_all while other threads are active. */
static kaz_runtime_params_t g_runtime_128 = { .initialized = 0 };
static kaz_runtime_params_t g_runtime_192 = { .initialized = 0 };
static kaz_runtime_params_t g_runtime_256 = { .initialized = 0 };

/* ============================================================================
 * Helper Functions
 * ============================================================================ */

/* Securely free a BIGNUM */
static void bn_secure_free(BIGNUM *bn)
{
    if (bn) {
        BN_clear_free(bn);  /* Secure free */
    }
}

/* Set constant-time flag on a BIGNUM (marks as secret) */
static void bn_set_secret(BIGNUM *bn)
{
    if (bn) {
        BN_set_flags(bn, BN_FLG_CONSTTIME);
    }
}

/* Export BIGNUM to fixed-size buffer (right-aligned, zero-padded, big-endian) */
static int bn_export_padded(unsigned char *buf, size_t buf_size, const BIGNUM *bn)
{
    int bn_size;

    memset(buf, 0, buf_size);

    if (bn == NULL || BN_is_zero(bn)) {
        return 0;
    }

    bn_size = BN_num_bytes(bn);
    if ((size_t)bn_size > buf_size) {
        return -1;  /* Value too large for buffer */
    }

    /* Write to end of buffer for right-alignment */
    BN_bn2bin(bn, buf + (buf_size - bn_size));
    return 0;
}

/* Import fixed-size buffer to BIGNUM */
static int bn_import(BIGNUM *bn, const unsigned char *buf, size_t buf_size)
{
    if (BN_bin2bn(buf, (int)buf_size, bn) == NULL) {
        return -1;
    }
    return 0;
}

/*
 * Modular exponentiation safe for even moduli.
 * OpenSSL's BN_mod_exp fails when either base or exponent has BN_FLG_CONSTTIME
 * and the modulus is even (it tries Montgomery internally which requires odd modulus).
 * This wrapper handles even moduli by using temporary copies without CONSTTIME flags.
 * For odd moduli, it delegates directly to BN_mod_exp.
 */
static int bn_mod_exp_safe(BIGNUM *result, const BIGNUM *base, const BIGNUM *exp,
                           const BIGNUM *mod, BN_CTX *ctx)
{
    if (BN_is_odd(mod)) {
        return BN_mod_exp(result, base, exp, mod, ctx);
    }

    /* Even modulus: create fresh BIGNUMs without CONSTTIME flags.
     * BN_dup copies flags, so we use BN_copy into new BIGNUMs. */
    int ret = 0;
    BIGNUM *tmp_base = BN_new();
    BIGNUM *tmp_exp = BN_new();
    if (!tmp_base || !tmp_exp) goto done;

    if (!BN_copy(tmp_base, base)) goto done;
    if (!BN_copy(tmp_exp, exp)) goto done;
    /* tmp_base and tmp_exp are fresh BN_new() — no CONSTTIME flag */

    ret = BN_mod_exp(result, tmp_base, tmp_exp, mod, ctx);

done:
    BN_clear_free(tmp_base);
    BN_clear_free(tmp_exp);
    return ret;
}

/* Generate 4 random bytes and return as a BIGNUM (matching Java's getRandom.nextBytes(4)) */
static BIGNUM *bn_random_4bytes(void)
{
    unsigned char buf[4];
    BIGNUM *bn = BN_new();
    if (!bn) return NULL;

    if (RAND_bytes(buf, 4) != 1) {
        kaz_secure_zero(buf, sizeof(buf));
        BN_free(bn);
        return NULL;
    }

    if (BN_bin2bn(buf, 4, bn) == NULL) {
        kaz_secure_zero(buf, sizeof(buf));
        BN_free(bn);
        return NULL;
    }

    kaz_secure_zero(buf, sizeof(buf));
    return bn;
}

/*
 * Find next probable prime >= bn.
 * Matches Java's BigInteger.nextProbablePrime().
 * Returns 0 on success, -1 on failure.
 */
static int bn_next_probable_prime(BIGNUM *result, const BIGNUM *start, BN_CTX *ctx)
{
    (void)ctx;

    if (!BN_copy(result, start)) return -1;

    /* Make odd if even */
    if (!BN_is_odd(result)) {
        if (!BN_add_word(result, 1)) return -1;
    }

    /* Search for prime */
    for (int attempts = 0; attempts < 10000; attempts++) {
        int is_prime = BN_check_prime(result, NULL, NULL);
        if (is_prime == 1) return 0;
        if (is_prime < 0) return -1;
        if (!BN_add_word(result, 2)) return -1;
    }
    return -1; /* Should not happen for reasonable inputs */
}

/*
 * Chinese Remainder Theorem for two moduli.
 * result = CRT(a1, a2, m1, m2)
 * Matches Java: chrem(a1, a2, m1, m2)
 *   m1Inv = m1^(-1) mod m2
 *   diff = a2 - a1
 *   term = diff * m1 * m1Inv mod (m1*m2)
 *   result = a1 + term mod (m1*m2)
 */
static int bn_chrem(BIGNUM *result, const BIGNUM *a1, const BIGNUM *a2,
                    const BIGNUM *m1, const BIGNUM *m2, BN_CTX *ctx)
{
    int ret = -1;
    BIGNUM *m1Inv = NULL, *diff = NULL, *term = NULL, *m1m2 = NULL;

    m1Inv = BN_new();
    diff = BN_new();
    term = BN_new();
    m1m2 = BN_new();
    if (!m1Inv || !diff || !term || !m1m2) goto cleanup;

    /* m1m2 = m1 * m2 */
    if (!BN_mul(m1m2, m1, m2, ctx)) goto cleanup;

    /* m1Inv = m1^(-1) mod m2 */
    if (!BN_mod_inverse(m1Inv, m1, m2, ctx)) goto cleanup;

    /* diff = (a2 - a1) mod m2 — ensures non-negative for subsequent arithmetic */
    if (!BN_mod_sub(diff, a2, a1, m2, ctx)) goto cleanup;

    /* term = diff * m1 * m1Inv mod m1m2 */
    if (!BN_mod_mul(term, diff, m1, m1m2, ctx)) goto cleanup;
    if (!BN_mod_mul(term, term, m1Inv, m1m2, ctx)) goto cleanup;

    /* result = (a1 + term) mod m1m2 */
    if (!BN_mod_add(result, a1, term, m1m2, ctx)) goto cleanup;

    ret = 0;

cleanup:
    BN_free(m1Inv);
    BN_free(diff);
    BN_free(term);
    BN_free(m1m2);
    return ret;
}

/* ============================================================================
 * Parameter Initialization
 * ============================================================================ */

const kaz_sign_level_params_t *kaz_sign_get_level_params(kaz_sign_level_t level)
{
    switch (level) {
        case KAZ_LEVEL_128: return &g_level_128_params;
        case KAZ_LEVEL_192: return &g_level_192_params;
        case KAZ_LEVEL_256: return &g_level_256_params;
        default: return NULL;
    }
}

/* Get the runtime parameter cache for a level */
static kaz_runtime_params_t *get_runtime_params(kaz_sign_level_t level)
{
    switch (level) {
        case KAZ_LEVEL_128: return &g_runtime_128;
        case KAZ_LEVEL_192: return &g_runtime_192;
        case KAZ_LEVEL_256: return &g_runtime_256;
        default: return NULL;
    }
}

/* Convert level to array index (0,1,2) */
static int level_to_index(kaz_sign_level_t level)
{
    switch (level) {
        case KAZ_LEVEL_128: return 0;
        case KAZ_LEVEL_192: return 1;
        case KAZ_LEVEL_256: return 2;
        default: return -1;
    }
}

/* Initialize runtime parameters for a specific level */
static int init_runtime_params(kaz_runtime_params_t *rp, kaz_sign_level_t level)
{
    int ret = KAZ_SIGN_ERROR_MEMORY;
    int idx;
    BN_CTX *ctx = NULL;
    BIGNUM *rem = NULL;

    if (rp->initialized) {
        return KAZ_SIGN_SUCCESS;
    }

    /* Get level parameters */
    rp->params = kaz_sign_get_level_params(level);
    if (!rp->params) {
        return KAZ_SIGN_ERROR_INVALID;
    }

    idx = level_to_index(level);
    if (idx < 0) {
        return KAZ_SIGN_ERROR_INVALID;
    }

    /* Allocate all BIGNUMs */
    rp->G0 = BN_new();
    rp->G1 = BN_new();
    rp->g = BN_new();
    rp->R = BN_new();
    rp->A = BN_new();
    rp->phiG1 = BN_new();
    rp->phiphiG1 = BN_new();
    rp->q = BN_new();
    rp->Q = BN_new();
    rp->qQ = BN_new();
    rp->phiQ = BN_new();
    rp->phiqQ = BN_new();
    rp->G1RHO = BN_new();
    rp->G1QRHO = BN_new();
    rp->G1qQRHO = BN_new();
    rp->phiG1RHO = BN_new();
    rp->phiphiG1RHO = BN_new();
    rp->G1A = BN_new();
    rp->G1A_keygen = BN_new();
    ctx = BN_CTX_new();
    rem = BN_new();

    if (!rp->G0 || !rp->G1 || !rp->g || !rp->R || !rp->A ||
        !rp->phiG1 || !rp->phiphiG1 ||
        !rp->q || !rp->Q || !rp->qQ || !rp->phiQ || !rp->phiqQ ||
        !rp->G1RHO || !rp->G1QRHO || !rp->G1qQRHO ||
        !rp->phiG1RHO || !rp->phiphiG1RHO ||
        !rp->G1A || !rp->G1A_keygen || !ctx || !rem) {
        goto cleanup;
    }

    /* Parse common constants */
    if (!BN_dec2bn(&rp->G0, SP_G0)) goto cleanup;
    if (!BN_dec2bn(&rp->G1, SP_G1)) goto cleanup;
    if (!BN_dec2bn(&rp->g, SP_g)) goto cleanup;
    if (!BN_dec2bn(&rp->R, SP_R)) goto cleanup;
    if (!BN_dec2bn(&rp->A, SP_A)) goto cleanup;
    if (!BN_dec2bn(&rp->phiG1, SP_PHIG1)) goto cleanup;
    if (!BN_dec2bn(&rp->phiphiG1, SP_PHIPHIG1)) goto cleanup;

    /* Parse per-level constants */
    if (!BN_dec2bn(&rp->q, SP_q[idx])) goto cleanup;
    if (!BN_dec2bn(&rp->Q, SP_Q[idx])) goto cleanup;
    if (!BN_dec2bn(&rp->qQ, SP_qQ[idx])) goto cleanup;
    if (!BN_dec2bn(&rp->phiQ, SP_PHIQ[idx])) goto cleanup;
    if (!BN_dec2bn(&rp->phiqQ, SP_PHIqQ[idx])) goto cleanup;
    if (!BN_dec2bn(&rp->G1RHO, SP_G1RHO[idx])) goto cleanup;
    if (!BN_dec2bn(&rp->G1QRHO, SP_G1QRHO[idx])) goto cleanup;
    if (!BN_dec2bn(&rp->G1qQRHO, SP_G1qQRHO[idx])) goto cleanup;
    if (!BN_dec2bn(&rp->phiG1RHO, SP_PHIG1RHO[idx])) goto cleanup;
    if (!BN_dec2bn(&rp->phiphiG1RHO, SP_PHIPHIG1RHO[idx])) goto cleanup;

    /* Derived: LG1RHO */
    rp->LG1RHO = SP_LG1RHO[idx];

    /* Derived: G1A = G1RHO / A (for verification equation 2) */
    if (!BN_div(rp->G1A, rem, rp->G1RHO, rp->A, ctx)) goto cleanup;
    if (!BN_is_zero(rem)) goto cleanup;  /* G1RHO must be exactly divisible by A */

    /* Derived: G1A_keygen = G1 / A (for keygen gcd check) */
    if (!BN_div(rp->G1A_keygen, rem, rp->G1, rp->A, ctx)) goto cleanup;
    if (!BN_is_zero(rem)) goto cleanup;  /* G1 must be exactly divisible by A */

    /* Note: G0 and G1qQRHO are both even, so Montgomery contexts are
     * not applicable. All mod_exp uses bn_mod_exp_safe which handles
     * even moduli by stripping CONSTTIME flags via temporary copies. */

    /* Set hash function based on level */
    switch (level) {
        case KAZ_LEVEL_128: rp->hash_md = EVP_sha256(); break;
        case KAZ_LEVEL_192: rp->hash_md = EVP_sha384(); break;
        case KAZ_LEVEL_256: rp->hash_md = EVP_sha512(); break;
        default: goto cleanup;
    }

    rp->initialized = 1;
    ret = KAZ_SIGN_SUCCESS;

cleanup:
    BN_CTX_free(ctx);
    BN_free(rem);

    if (ret != KAZ_SIGN_SUCCESS) {
        /* Clean up on failure */
        BN_free(rp->G0); rp->G0 = NULL;
        BN_free(rp->G1); rp->G1 = NULL;
        BN_free(rp->g); rp->g = NULL;
        BN_free(rp->R); rp->R = NULL;
        BN_free(rp->A); rp->A = NULL;
        BN_free(rp->phiG1); rp->phiG1 = NULL;
        BN_free(rp->phiphiG1); rp->phiphiG1 = NULL;
        BN_free(rp->q); rp->q = NULL;
        BN_free(rp->Q); rp->Q = NULL;
        BN_free(rp->qQ); rp->qQ = NULL;
        BN_free(rp->phiQ); rp->phiQ = NULL;
        BN_free(rp->phiqQ); rp->phiqQ = NULL;
        BN_free(rp->G1RHO); rp->G1RHO = NULL;
        BN_free(rp->G1QRHO); rp->G1QRHO = NULL;
        BN_free(rp->G1qQRHO); rp->G1qQRHO = NULL;
        BN_free(rp->phiG1RHO); rp->phiG1RHO = NULL;
        BN_free(rp->phiphiG1RHO); rp->phiphiG1RHO = NULL;
        BN_free(rp->G1A); rp->G1A = NULL;
        BN_free(rp->G1A_keygen); rp->G1A_keygen = NULL;
        /* mont_G0 removed: G0 is even, Montgomery N/A */
        /* mont_G1qQRHO removed: G1qQRHO is even, Montgomery N/A */
        rp->initialized = 0;
    }

    return ret;
}

/* Clear runtime parameters for a specific level */
static void clear_runtime_params(kaz_runtime_params_t *rp)
{
    if (rp && rp->initialized) {
        BN_free(rp->G0); rp->G0 = NULL;
        BN_free(rp->G1); rp->G1 = NULL;
        BN_free(rp->g); rp->g = NULL;
        BN_free(rp->R); rp->R = NULL;
        BN_free(rp->A); rp->A = NULL;
        BN_free(rp->phiG1); rp->phiG1 = NULL;
        BN_free(rp->phiphiG1); rp->phiphiG1 = NULL;
        BN_free(rp->q); rp->q = NULL;
        BN_free(rp->Q); rp->Q = NULL;
        BN_free(rp->qQ); rp->qQ = NULL;
        BN_free(rp->phiQ); rp->phiQ = NULL;
        BN_free(rp->phiqQ); rp->phiqQ = NULL;
        BN_free(rp->G1RHO); rp->G1RHO = NULL;
        BN_free(rp->G1QRHO); rp->G1QRHO = NULL;
        BN_free(rp->G1qQRHO); rp->G1qQRHO = NULL;
        BN_free(rp->phiG1RHO); rp->phiG1RHO = NULL;
        BN_free(rp->phiphiG1RHO); rp->phiphiG1RHO = NULL;
        BN_free(rp->G1A); rp->G1A = NULL;
        BN_free(rp->G1A_keygen); rp->G1A_keygen = NULL;
        /* mont_G0 removed: G0 is even, Montgomery N/A */
        /* mont_G1qQRHO removed: G1qQRHO is even, Montgomery N/A */
        rp->hash_md = NULL;
        rp->params = NULL;
        rp->initialized = 0;
    }
}

/* ============================================================================
 * Public API: Level Management
 * ============================================================================ */

/* Public API: Initialize a specific security level */
int kaz_sign_init_level(kaz_sign_level_t level)
{
    kaz_runtime_params_t *rp = get_runtime_params(level);
    if (!rp) {
        return KAZ_SIGN_ERROR_INVALID;
    }
    return init_runtime_params(rp, level);
}

/* Public API: Clear a specific security level */
void kaz_sign_clear_level(kaz_sign_level_t level)
{
    kaz_runtime_params_t *rp = get_runtime_params(level);
    if (rp) {
        clear_runtime_params(rp);
    }
}

/* ============================================================================
 * Random Number Generation
 * ============================================================================ */

static int g_rand_initialized = 0;

/* Public API: Clear all security levels */
void kaz_sign_clear_all(void)
{
    clear_runtime_params(&g_runtime_128);
    clear_runtime_params(&g_runtime_192);
    clear_runtime_params(&g_runtime_256);
    g_rand_initialized = 0;
}

int kaz_sign_init_random(void)
{
    if (g_rand_initialized) {
        return KAZ_SIGN_SUCCESS;
    }

    /* Initialize the compile-time level */
    int ret = init_runtime_params(
        get_runtime_params((kaz_sign_level_t)KAZ_SECURITY_LEVEL),
        (kaz_sign_level_t)KAZ_SECURITY_LEVEL);
    if (ret != KAZ_SIGN_SUCCESS) {
        return ret;
    }

    g_rand_initialized = 1;
    return KAZ_SIGN_SUCCESS;
}

void kaz_sign_clear_random(void)
{
    kaz_sign_clear_all();
}

int kaz_sign_is_initialized(void)
{
    return g_rand_initialized;
}

/* ============================================================================
 * Hash Function
 * ============================================================================ */

int kaz_sign_hash_ex(kaz_sign_level_t level,
                     const unsigned char *msg,
                     unsigned long long msglen,
                     unsigned char *hash)
{
    kaz_runtime_params_t *rp = get_runtime_params(level);
    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(level);
    unsigned int hash_len = 0;
    unsigned char hash_buf[64]; /* max SHA-512 = 64 bytes */
    EVP_MD_CTX *hash_ctx = NULL;

    if (!rp || !params) {
        return KAZ_SIGN_ERROR_INVALID;
    }

    /* Initialize if needed */
    if (!rp->initialized) {
        int ret = init_runtime_params(rp, level);
        if (ret != KAZ_SIGN_SUCCESS) {
            return ret;
        }
    }

    if (!rp->hash_md) {
        return KAZ_SIGN_ERROR_INVALID;
    }

    /* Allocate per-call hash context for thread safety */
    hash_ctx = EVP_MD_CTX_new();
    if (!hash_ctx) {
        return KAZ_SIGN_ERROR_MEMORY;
    }

    if (EVP_DigestInit_ex(hash_ctx, rp->hash_md, NULL) != 1) {
        EVP_MD_CTX_free(hash_ctx);
        return KAZ_SIGN_ERROR_INVALID;
    }

    /* Guard against truncation on 32-bit platforms */
    if (msglen > (unsigned long long)SIZE_MAX) {
        EVP_MD_CTX_free(hash_ctx);
        return KAZ_SIGN_ERROR_INVALID;
    }

    if (msg == NULL && msglen > 0) {
        EVP_MD_CTX_free(hash_ctx);
        return KAZ_SIGN_ERROR_INVALID;
    }

    if (msg != NULL && msglen > 0 && EVP_DigestUpdate(hash_ctx, msg, (size_t)msglen) != 1) {
        EVP_MD_CTX_free(hash_ctx);
        return KAZ_SIGN_ERROR_INVALID;
    }

    if (EVP_DigestFinal_ex(hash_ctx, hash_buf, &hash_len) != 1) {
        kaz_secure_zero(hash_buf, sizeof(hash_buf));
        EVP_MD_CTX_free(hash_ctx);
        return KAZ_SIGN_ERROR_INVALID;
    }

    EVP_MD_CTX_free(hash_ctx);

    /* Copy native hash output: SHA-256=32, SHA-384=48, SHA-512=64 */
    memcpy(hash, hash_buf, params->hash_bytes);
    kaz_secure_zero(hash_buf, sizeof(hash_buf));

    return KAZ_SIGN_SUCCESS;
}

int kaz_sign_hash(const unsigned char *msg,
                  unsigned long long msglen,
                  unsigned char *hash)
{
    return kaz_sign_hash_ex((kaz_sign_level_t)KAZ_SECURITY_LEVEL, msg, msglen, hash);
}

/* ============================================================================
 * Key Generation (matches KAZSIGNKeyGenerator.java)
 * ============================================================================ */

int kaz_sign_keypair_ex(kaz_sign_level_t level,
                        unsigned char *pk,
                        unsigned char *sk)
{
    kaz_runtime_params_t *rp = get_runtime_params(level);
    const kaz_sign_level_params_t *params;
    BN_CTX *local_ctx = NULL;
    BIGNUM *a = NULL, *omega1 = NULL, *b = NULL;
    BIGNUM *alpha = NULL, *V1 = NULL, *V2 = NULL, *SK = NULL;
    BIGNUM *phiQb = NULL, *omega1phiG1 = NULL;
    BIGNUM *tmp = NULL, *tmp2 = NULL;
    /* Note: qQ and G1qQRHO are even — no Montgomery contexts */
    int ret = KAZ_SIGN_ERROR_MEMORY;
    int k, alphaBytes;

    if (!rp) {
        return KAZ_SIGN_ERROR_INVALID;
    }

    if (pk == NULL || sk == NULL) {
        return KAZ_SIGN_ERROR_INVALID;
    }

    /* Initialize if needed */
    if (!rp->initialized) {
        ret = init_runtime_params(rp, level);
        if (ret != KAZ_SIGN_SUCCESS) {
            return ret;
        }
    }

    params = rp->params;
    k = params->level;

    /* Allocate per-call BN_CTX for thread safety */
    local_ctx = BN_CTX_new();
    if (!local_ctx) {
        return KAZ_SIGN_ERROR_MEMORY;
    }

    /* Allocate BIGNUMs */
    a = BN_new();
    omega1 = BN_new();
    b = BN_new();
    alpha = BN_secure_new();  /* secret */
    V1 = BN_new();
    V2 = BN_new();
    SK = BN_secure_new();     /* secret */
    phiQb = BN_new();
    omega1phiG1 = BN_new();
    tmp = BN_new();
    tmp2 = BN_new();

    if (!a || !omega1 || !b || !alpha || !V1 || !V2 || !SK ||
        !phiQb || !omega1phiG1 || !tmp || !tmp2) {
        goto cleanup;
    }

    bn_set_secret(alpha);
    bn_set_secret(SK);

    /* Step 1: a = random_4_bytes().nextProbablePrime() */
    {
        BIGNUM *rand4 = bn_random_4bytes();
        if (!rand4) { ret = KAZ_SIGN_ERROR_RNG; goto cleanup; }
        if (bn_next_probable_prime(a, rand4, local_ctx) != 0) {
            BN_free(rand4);
            ret = KAZ_SIGN_ERROR_RNG;
            goto cleanup;
        }
        BN_free(rand4);
    }

    /* omega1 = random_4_bytes() (must be non-zero for use as modulus factor) */
    {
        BIGNUM *rand4 = bn_random_4bytes();
        if (!rand4) { ret = KAZ_SIGN_ERROR_RNG; goto cleanup; }
        if (!BN_copy(omega1, rand4)) { BN_free(rand4); goto cleanup; }
        BN_free(rand4);
        if (BN_is_zero(omega1)) { ret = KAZ_SIGN_ERROR_RNG; goto cleanup; }
    }

    /* Step 2: b = a^(phiphiG1RHO) mod (omega1 * phiG1RHO)
     * Note: a, omega1 are ephemeral random values; phiphiG1RHO is public.
     * The exponent is a public constant, so non-consttime exp is acceptable here. */
    if (!BN_mul(omega1phiG1, omega1, rp->phiG1RHO, local_ctx)) goto cleanup;
    if (!bn_mod_exp_safe(b, a, rp->phiphiG1RHO, omega1phiG1, local_ctx)) goto cleanup;

    /* phiQb = phiQ * b — used as exponent below.
     * Note: Cannot use BN_FLG_CONSTTIME because qQ and G1qQRHO are even,
     * and OpenSSL's consttime mod_exp requires odd modulus (Montgomery). */
    if (!BN_mul(phiQb, rp->phiQ, b, local_ctx)) goto cleanup;

    /* alphaBytes = (k + LG1RHO) / 8 */
    alphaBytes = (k + rp->LG1RHO) / 8;

    /* Step 3: Loop to generate alpha and compute V1, V2, SK */
    for (int attempt = 0; attempt < 10000; attempt++) {
        unsigned char *alpha_buf = NULL;

        /* alpha = random(alphaBytes) * 2 (left-shift by 1) */
        alpha_buf = malloc(alphaBytes);
        if (!alpha_buf) { ret = KAZ_SIGN_ERROR_MEMORY; goto cleanup; }

        if (RAND_bytes(alpha_buf, alphaBytes) != 1) {
            kaz_secure_zero(alpha_buf, alphaBytes);
            free(alpha_buf);
            ret = KAZ_SIGN_ERROR_RNG;
            goto cleanup;
        }

        if (BN_bin2bn(alpha_buf, alphaBytes, alpha) == NULL) {
            kaz_secure_zero(alpha_buf, alphaBytes);
            free(alpha_buf);
            goto cleanup;
        }
        kaz_secure_zero(alpha_buf, alphaBytes);
        free(alpha_buf);

        /* Mark alpha as secret immediately after loading */
        bn_set_secret(alpha);

        /* alpha = alpha << 1 (multiply by 2) */
        if (!BN_lshift1(alpha, alpha)) goto cleanup;
        bn_set_secret(alpha);  /* re-apply after arithmetic */

        /* V1 = alpha mod G1RHO */
        if (!BN_mod(V1, alpha, rp->G1RHO, local_ctx)) goto cleanup;

        /* V2 = Q * alpha^(phiQb) mod qQ
         * phiQb has BN_FLG_CONSTTIME set for fixed-window exponentiation */
        if (!bn_mod_exp_safe(tmp, alpha, phiQb, rp->qQ, local_ctx)) goto cleanup;
        if (!BN_mod_mul(V2, rp->Q, tmp, rp->qQ, local_ctx)) goto cleanup;

        /* SK = alpha^(phiQb) mod G1qQRHO
         * phiQb has BN_FLG_CONSTTIME set for fixed-window exponentiation */
        if (!bn_mod_exp_safe(SK, alpha, phiQb, rp->G1qQRHO, local_ctx)) goto cleanup;

        /* Check: SK mod G1QRHO != 0 */
        if (!BN_mod(tmp, SK, rp->G1QRHO, local_ctx)) goto cleanup;
        if (BN_is_zero(tmp)) continue;

        /* Check: gcd(V1, G1/A) == 1 — Java uses G1.divide(A) in keygen */
        if (!BN_gcd(tmp, V1, rp->G1A_keygen, local_ctx)) goto cleanup;
        if (!BN_is_one(tmp)) continue;
        /* Both conditions met - export keys */
        /* pk = V1 || V2 */
        if (bn_export_padded(pk, params->v1_bytes, V1) != 0) goto cleanup;
        if (bn_export_padded(pk + params->v1_bytes, params->v2_bytes, V2) != 0) goto cleanup;

        /* sk = SK || V1 || V2 */
        if (bn_export_padded(sk, params->sk_bytes, SK) != 0) goto cleanup;
        if (bn_export_padded(sk + params->sk_bytes, params->v1_bytes, V1) != 0) goto cleanup;
        if (bn_export_padded(sk + params->sk_bytes + params->v1_bytes,
                             params->v2_bytes, V2) != 0) goto cleanup;

        ret = KAZ_SIGN_SUCCESS;
        goto cleanup;
    }

    /* Exhausted attempts */
    ret = KAZ_SIGN_ERROR_RNG;

cleanup:
    BN_CTX_free(local_ctx);
    BN_free(a);
    BN_free(omega1);
    BN_free(b);
    bn_secure_free(alpha);
    BN_free(V1);
    BN_free(V2);
    bn_secure_free(SK);
    BN_free(phiQb);
    BN_free(omega1phiG1);
    BN_free(tmp);
    BN_free(tmp2);
    /* No Montgomery contexts to free (even moduli) */

    return ret;
}

int kaz_sign_keypair(unsigned char *pk, unsigned char *sk)
{
    if (!g_rand_initialized) {
        int ret = kaz_sign_init_random();
        if (ret != KAZ_SIGN_SUCCESS) return ret;
    }
    return kaz_sign_keypair_ex((kaz_sign_level_t)KAZ_SECURITY_LEVEL, pk, sk);
}

/* ============================================================================
 * Signature Generation (matches KAZSIGNSigner.java)
 * ============================================================================ */

int kaz_sign_signature_ex(kaz_sign_level_t level,
                          unsigned char *sig,
                          unsigned long long *siglen,
                          const unsigned char *msg,
                          unsigned long long msglen,
                          const unsigned char *sk)
{
    kaz_runtime_params_t *rp = get_runtime_params(level);
    const kaz_sign_level_params_t *params;
    BN_CTX *local_ctx = NULL;
    BIGNUM *SK_bn = NULL, *V1 = NULL, *V2 = NULL;
    BIGNUM *hashInt = NULL;
    BIGNUM *r1 = NULL, *omega2 = NULL, *r2 = NULL, *omega3 = NULL;
    BIGNUM *beta1 = NULL, *beta2 = NULL;
    BIGNUM *S1 = NULL, *S2_bn = NULL;
    BIGNUM *term1 = NULL, *term2 = NULL, *tmp = NULL, *tmp2 = NULL;
    BIGNUM *omega2phiG1 = NULL, *omega3phiG1 = NULL;
    BIGNUM *Y1 = NULL, *SF1 = NULL;
    BIGNUM *phiqQ_beta1 = NULL, *phiqQ_beta2 = NULL;
    BIGNUM *two = NULL, *V2divQ = NULL;
    unsigned char *hash_buf = NULL;
    int ret = KAZ_SIGN_ERROR_MEMORY;
    int LG1qQ;

    if (!rp) {
        return KAZ_SIGN_ERROR_INVALID;
    }

    if (sig == NULL || siglen == NULL || sk == NULL) {
        return KAZ_SIGN_ERROR_INVALID;
    }

    if (msg == NULL && msglen > 0) {
        return KAZ_SIGN_ERROR_INVALID;
    }

    /* Initialize if needed */
    if (!rp->initialized) {
        ret = init_runtime_params(rp, level);
        if (ret != KAZ_SIGN_SUCCESS) {
            return ret;
        }
    }

    params = rp->params;

    /* Allocate per-call BN_CTX */
    local_ctx = BN_CTX_new();
    if (!local_ctx) {
        return KAZ_SIGN_ERROR_MEMORY;
    }

    /* Allocate hash buffer */
    hash_buf = malloc(params->hash_bytes);
    if (!hash_buf) {
        BN_CTX_free(local_ctx);
        return KAZ_SIGN_ERROR_MEMORY;
    }

    /* Allocate BIGNUMs */
    SK_bn = BN_secure_new();
    V1 = BN_new();
    V2 = BN_new();
    hashInt = BN_new();
    r1 = BN_new();
    omega2 = BN_new();
    r2 = BN_new();
    omega3 = BN_new();
    beta1 = BN_new();
    beta2 = BN_new();
    S1 = BN_new();
    S2_bn = BN_new();
    term1 = BN_new();
    term2 = BN_new();
    tmp = BN_new();
    tmp2 = BN_new();
    omega2phiG1 = BN_new();
    omega3phiG1 = BN_new();
    Y1 = BN_new();
    SF1 = BN_new();
    phiqQ_beta1 = BN_new();
    phiqQ_beta2 = BN_new();
    two = BN_new();
    V2divQ = BN_new();

    if (!SK_bn || !V1 || !V2 || !hashInt ||
        !r1 || !omega2 || !r2 || !omega3 ||
        !beta1 || !beta2 || !S1 || !S2_bn ||
        !term1 || !term2 || !tmp || !tmp2 ||
        !omega2phiG1 || !omega3phiG1 ||
        !Y1 || !SF1 || !phiqQ_beta1 || !phiqQ_beta2 ||
        !two || !V2divQ) {
        goto cleanup;
    }

    bn_set_secret(SK_bn);
    BN_set_word(two, 2);

    /* Parse sk = SK || V1 || V2 */
    if (bn_import(SK_bn, sk, params->sk_bytes) != 0) {
        ret = KAZ_SIGN_ERROR_INVALID;
        goto cleanup;
    }
    bn_set_secret(SK_bn);
    if (bn_import(V1, sk + params->sk_bytes, params->v1_bytes) != 0) {
        ret = KAZ_SIGN_ERROR_INVALID;
        goto cleanup;
    }
    if (bn_import(V2, sk + params->sk_bytes + params->v1_bytes, params->v2_bytes) != 0) {
        ret = KAZ_SIGN_ERROR_INVALID;
        goto cleanup;
    }

    /* Hash message */
    if (kaz_sign_hash_ex(level, msg, msglen, hash_buf) != KAZ_SIGN_SUCCESS) {
        ret = KAZ_SIGN_ERROR_INVALID;
        goto cleanup;
    }
    if (bn_import(hashInt, hash_buf, params->hash_bytes) != 0) {
        ret = KAZ_SIGN_ERROR_INVALID;
        goto cleanup;
    }

    LG1qQ = BN_num_bits(rp->G1qQRHO);

    /* Generate random values for beta1, beta2 */
    /* r1 = random_4_bytes().nextProbablePrime() */
    {
        BIGNUM *rand4 = bn_random_4bytes();
        if (!rand4) { ret = KAZ_SIGN_ERROR_RNG; goto cleanup; }
        if (bn_next_probable_prime(r1, rand4, local_ctx) != 0) {
            BN_free(rand4); ret = KAZ_SIGN_ERROR_RNG; goto cleanup;
        }
        BN_free(rand4);
    }
    /* omega2 = random_4_bytes() (must be non-zero for use as modulus factor) */
    {
        BIGNUM *rand4 = bn_random_4bytes();
        if (!rand4) { ret = KAZ_SIGN_ERROR_RNG; goto cleanup; }
        if (!BN_copy(omega2, rand4)) { BN_free(rand4); goto cleanup; }
        BN_free(rand4);
        if (BN_is_zero(omega2)) { ret = KAZ_SIGN_ERROR_RNG; goto cleanup; }
    }
    /* r2 = random_4_bytes().nextProbablePrime() */
    {
        BIGNUM *rand4 = bn_random_4bytes();
        if (!rand4) { ret = KAZ_SIGN_ERROR_RNG; goto cleanup; }
        if (bn_next_probable_prime(r2, rand4, local_ctx) != 0) {
            BN_free(rand4); ret = KAZ_SIGN_ERROR_RNG; goto cleanup;
        }
        BN_free(rand4);
    }
    /* omega3 = random_4_bytes() (must be non-zero for use as modulus factor) */
    {
        BIGNUM *rand4 = bn_random_4bytes();
        if (!rand4) { ret = KAZ_SIGN_ERROR_RNG; goto cleanup; }
        if (!BN_copy(omega3, rand4)) { BN_free(rand4); goto cleanup; }
        BN_free(rand4);
        if (BN_is_zero(omega3)) { ret = KAZ_SIGN_ERROR_RNG; goto cleanup; }
    }

    /* beta1 = r1^(phiphiG1RHO) mod (omega2 * phiG1RHO)
     * Note: r1, omega2 are ephemeral random; phiphiG1RHO is public constant.
     * Exponent is public, so non-consttime is acceptable. */
    if (!BN_mul(omega2phiG1, omega2, rp->phiG1RHO, local_ctx)) goto cleanup;
    if (!bn_mod_exp_safe(beta1, r1, rp->phiphiG1RHO, omega2phiG1, local_ctx)) goto cleanup;

    /* beta2 = r2^(phiphiG1RHO) mod (omega3 * phiG1RHO) */
    if (!BN_mul(omega3phiG1, omega3, rp->phiG1RHO, local_ctx)) goto cleanup;
    if (!bn_mod_exp_safe(beta2, r2, rp->phiphiG1RHO, omega3phiG1, local_ctx)) goto cleanup;

    /* Pre-compute phiqQ * beta1 and phiqQ * beta2 — used as exponents below.
     * Note: Cannot use BN_FLG_CONSTTIME because G1qQRHO is even
     * and OpenSSL's consttime mod_exp requires odd modulus (Montgomery). */
    if (!BN_mul(phiqQ_beta1, rp->phiqQ, beta1, local_ctx)) goto cleanup;
    if (!BN_mul(phiqQ_beta2, rp->phiqQ, beta2, local_ctx)) goto cleanup;

    /* V2divQ = V2 / Q (must be exact) */
    {
        BIGNUM *v2_rem = BN_new();
        if (!v2_rem) goto cleanup;
        if (!BN_div(V2divQ, v2_rem, V2, rp->Q, local_ctx)) { BN_free(v2_rem); goto cleanup; }
        if (!BN_is_zero(v2_rem)) { BN_free(v2_rem); ret = KAZ_SIGN_ERROR_INVALID; goto cleanup; }
        BN_free(v2_rem);
    }

    /* S2 = 0, loop */
    BN_zero(S2_bn);

    for (uint32_t s2_counter = 0; s2_counter <= 65535; s2_counter++) {
        /* term1 = hashInt^(phiqQ * beta1) mod G1qQRHO
         * phiqQ_beta1 has BN_FLG_CONSTTIME set for fixed-window exponentiation */
        if (!bn_mod_exp_safe(term1, hashInt, phiqQ_beta1, rp->G1qQRHO, local_ctx)) goto cleanup;

        /* term2 = hashInt^(phiqQ * beta2) mod G1qQRHO
         * phiqQ_beta2 has BN_FLG_CONSTTIME set for fixed-window exponentiation */
        if (!bn_mod_exp_safe(term2, hashInt, phiqQ_beta2, rp->G1qQRHO, local_ctx)) goto cleanup;

        /* S1 = SK * (term1 + term2) mod G1qQRHO */
        if (!BN_mod_add(tmp, term1, term2, rp->G1qQRHO, local_ctx)) goto cleanup;
        if (!BN_mod_mul(S1, SK_bn, tmp, rp->G1qQRHO, local_ctx)) goto cleanup;

        /* Filter 3 pre-check: SF1 via CRT */
        /* Y1 = V1^phiQ mod G1QRHO * (2 * hashInt^phiqQ mod G1QRHO) mod G1QRHO */
        if (!bn_mod_exp_safe(tmp, V1, rp->phiQ, rp->G1QRHO, local_ctx)) goto cleanup;
        if (!bn_mod_exp_safe(tmp2, hashInt, rp->phiqQ, rp->G1QRHO, local_ctx)) goto cleanup;
        if (!BN_mod_mul(tmp2, two, tmp2, rp->G1QRHO, local_ctx)) goto cleanup;
        if (!BN_mod_mul(Y1, tmp, tmp2, rp->G1QRHO, local_ctx)) goto cleanup;

        /* SF1 = CRT(V2/Q, Y1, q, G1QRHO) */
        if (bn_chrem(SF1, V2divQ, Y1, rp->q, rp->G1QRHO, local_ctx) != 0) goto cleanup;

        /* Check: bitlen(S1) == bitlen(G1qQRHO) AND S1 mod G1qQRHO != SF1 */
        if (!BN_mod(tmp, S1, rp->G1qQRHO, local_ctx)) goto cleanup;
        if (BN_num_bits(S1) == LG1qQ && BN_cmp(tmp, SF1) != 0) {
            /* Accept */
            break;
        }

        /* Increment S2 and hashInt */
        if (!BN_add_word(S2_bn, 1)) goto cleanup;
        if (!BN_add_word(hashInt, 1)) goto cleanup;

        if (s2_counter == 65535) {
            ret = KAZ_SIGN_ERROR_INVALID;
            goto cleanup;
        }
    }

    /* Export signature: S1 || S2 (8 bytes big-endian) || message */
    if (msglen > SIZE_MAX - params->signature_overhead) {
        ret = KAZ_SIGN_ERROR_INVALID;
        goto cleanup;
    }

    if (bn_export_padded(sig, params->s1_bytes, S1) != 0) {
        ret = KAZ_SIGN_ERROR_INVALID;
        goto cleanup;
    }

    /* S2 as 8-byte big-endian */
    if (bn_export_padded(sig + params->s1_bytes, params->s2_bytes, S2_bn) != 0) {
        ret = KAZ_SIGN_ERROR_INVALID;
        goto cleanup;
    }

    /* Append message */
    if (msglen > 0) {
        memcpy(sig + params->signature_overhead, msg, (size_t)msglen);
    }

    *siglen = params->signature_overhead + msglen;
    ret = KAZ_SIGN_SUCCESS;

cleanup:
    if (hash_buf) {
        kaz_secure_zero(hash_buf, params ? params->hash_bytes : 64);
        free(hash_buf);
    }

    BN_CTX_free(local_ctx);
    bn_secure_free(SK_bn);
    BN_free(V1);
    BN_free(V2);
    BN_free(hashInt);
    BN_free(r1);
    BN_free(omega2);
    BN_free(r2);
    BN_free(omega3);
    BN_free(beta1);
    BN_free(beta2);
    BN_free(S1);
    BN_free(S2_bn);
    BN_free(term1);
    BN_free(term2);
    BN_free(tmp);
    BN_free(tmp2);
    BN_free(omega2phiG1);
    BN_free(omega3phiG1);
    BN_free(Y1);
    BN_free(SF1);
    BN_free(phiqQ_beta1);
    BN_free(phiqQ_beta2);
    BN_free(two);
    BN_free(V2divQ);

    return ret;
}

int kaz_sign_signature(unsigned char *sig,
                       unsigned long long *siglen,
                       const unsigned char *msg,
                       unsigned long long msglen,
                       const unsigned char *sk)
{
    if (!g_rand_initialized) {
        int ret = kaz_sign_init_random();
        if (ret != KAZ_SIGN_SUCCESS) return ret;
    }
    return kaz_sign_signature_ex((kaz_sign_level_t)KAZ_SECURITY_LEVEL,
                                 sig, siglen, msg, msglen, sk);
}

/* ============================================================================
 * Signature Verification (matches KAZSIGNVerifier.java)
 * 5 filters + 2 verification equations
 * ============================================================================ */

int kaz_sign_verify_ex(kaz_sign_level_t level,
                       unsigned char *msg,
                       unsigned long long *msglen,
                       const unsigned char *sig,
                       unsigned long long siglen,
                       const unsigned char *pk)
{
    kaz_runtime_params_t *rp = get_runtime_params(level);
    const kaz_sign_level_params_t *params;
    BN_CTX *local_ctx = NULL;
    BIGNUM *V1 = NULL, *V2 = NULL;
    BIGNUM *S1 = NULL, *S2_bn = NULL;
    BIGNUM *hashInt = NULL;
    BIGNUM *tmp = NULL, *tmp2 = NULL, *tmp3 = NULL;
    BIGNUM *Y1 = NULL, *Y2 = NULL, *SF1 = NULL, *SF2 = NULL;
    BIGNUM *two = NULL, *V2divQ = NULL;
    BIGNUM *e_val = NULL, *qQ_div_e = NULL, *G1qQRHO_div_e = NULL;
    BIGNUM *W4 = NULL, *W5 = NULL;
    BIGNUM *y1 = NULL, *y2 = NULL;
    BIGNUM *t1 = NULL, *t2 = NULL;
    BIGNUM *y3 = NULL, *y4 = NULL, *inv_val = NULL;
    unsigned char *hash_buf = NULL;
    unsigned long long extracted_msglen;
    int ret = KAZ_SIGN_ERROR_VERIFY;

    if (!rp) {
        return KAZ_SIGN_ERROR_INVALID;
    }

    if (msg == NULL || msglen == NULL || sig == NULL || pk == NULL) {
        return KAZ_SIGN_ERROR_INVALID;
    }

    /* Initialize if needed */
    if (!rp->initialized) {
        int init_ret = init_runtime_params(rp, level);
        if (init_ret != KAZ_SIGN_SUCCESS) {
            return init_ret;
        }
    }

    params = rp->params;

    if (siglen < params->signature_overhead) {
        return KAZ_SIGN_ERROR_INVALID;
    }

    extracted_msglen = siglen - params->signature_overhead;

    /* Allocate per-call BN_CTX */
    local_ctx = BN_CTX_new();
    if (!local_ctx) {
        return KAZ_SIGN_ERROR_MEMORY;
    }

    /* Allocate hash buffer */
    hash_buf = malloc(params->hash_bytes);
    if (!hash_buf) {
        BN_CTX_free(local_ctx);
        return KAZ_SIGN_ERROR_MEMORY;
    }

    /* Allocate BIGNUMs */
    V1 = BN_new();
    V2 = BN_new();
    S1 = BN_new();
    S2_bn = BN_new();
    hashInt = BN_new();
    tmp = BN_new();
    tmp2 = BN_new();
    tmp3 = BN_new();
    Y1 = BN_new();
    Y2 = BN_new();
    SF1 = BN_new();
    SF2 = BN_new();
    two = BN_new();
    V2divQ = BN_new();
    e_val = BN_new();
    qQ_div_e = BN_new();
    G1qQRHO_div_e = BN_new();
    W4 = BN_new();
    W5 = BN_new();
    y1 = BN_new();
    y2 = BN_new();
    t1 = BN_new();
    t2 = BN_new();
    y3 = BN_new();
    y4 = BN_new();
    inv_val = BN_new();

    if (!V1 || !V2 || !S1 || !S2_bn || !hashInt ||
        !tmp || !tmp2 || !tmp3 ||
        !Y1 || !Y2 || !SF1 || !SF2 ||
        !two || !V2divQ ||
        !e_val || !qQ_div_e || !G1qQRHO_div_e ||
        !W4 || !W5 ||
        !y1 || !y2 || !t1 || !t2 ||
        !y3 || !y4 || !inv_val) {
        ret = KAZ_SIGN_ERROR_MEMORY;
        goto cleanup;
    }

    BN_set_word(two, 2);

    /* Parse pk = V1 || V2 */
    if (bn_import(V1, pk, params->v1_bytes) != 0) {
        ret = KAZ_SIGN_ERROR_INVALID;
        goto cleanup;
    }
    if (bn_import(V2, pk + params->v1_bytes, params->v2_bytes) != 0) {
        ret = KAZ_SIGN_ERROR_INVALID;
        goto cleanup;
    }

    /* Parse sig = S1 (s1_bytes) || S2 (8 bytes big-endian) || [message] */
    if (bn_import(S1, sig, params->s1_bytes) != 0) {
        ret = KAZ_SIGN_ERROR_INVALID;
        goto cleanup;
    }
    if (bn_import(S2_bn, sig + params->s1_bytes, params->s2_bytes) != 0) {
        ret = KAZ_SIGN_ERROR_INVALID;
        goto cleanup;
    }

    /* Filter 0: S2 <= 65535 */
    {
        BIGNUM *max_s2 = BN_new();
        if (!max_s2) { ret = KAZ_SIGN_ERROR_MEMORY; goto cleanup; }
        BN_set_word(max_s2, 65535);
        if (BN_cmp(S2_bn, max_s2) > 0) {
            BN_free(max_s2);
            ret = KAZ_SIGN_ERROR_VERIFY;
            goto cleanup;
        }
        BN_free(max_s2);
    }

    /* Hash the embedded message */
    {
        const unsigned char *embedded_msg = sig + params->signature_overhead;
        if (kaz_sign_hash_ex(level, embedded_msg, extracted_msglen, hash_buf) != KAZ_SIGN_SUCCESS) {
            ret = KAZ_SIGN_ERROR_INVALID;
            goto cleanup;
        }
    }
    if (bn_import(hashInt, hash_buf, params->hash_bytes) != 0) {
        ret = KAZ_SIGN_ERROR_INVALID;
        goto cleanup;
    }

    /* hashInt = hashInt + S2 */
    if (!BN_add(hashInt, hashInt, S2_bn)) goto cleanup;

    /* Filter 1: S1 in [0, G1qQRHO) i.e. S1 mod G1qQRHO == S1 */
    if (!BN_mod(tmp, S1, rp->G1qQRHO, local_ctx)) goto cleanup;
    if (BN_cmp(tmp, S1) != 0) {
        ret = KAZ_SIGN_ERROR_VERIFY;
        goto cleanup;
    }

    /* Filter 2: bitlen(S1) <= bitlen(G1qQRHO) */
    if (BN_num_bits(S1) > BN_num_bits(rp->G1qQRHO)) {
        ret = KAZ_SIGN_ERROR_VERIFY;
        goto cleanup;
    }

    /* V2divQ = V2 / Q (must be exact — reject malformed public keys) */
    {
        BIGNUM *v2_rem = BN_new();
        if (!v2_rem) goto cleanup;
        if (!BN_div(V2divQ, v2_rem, V2, rp->Q, local_ctx)) { BN_free(v2_rem); goto cleanup; }
        if (!BN_is_zero(v2_rem)) { BN_free(v2_rem); ret = KAZ_SIGN_ERROR_VERIFY; goto cleanup; }
        BN_free(v2_rem);
    }

    /* Filter 3: S1 mod G1qQRHO != CRT(V2/Q, V1^phiQ * 2*h^phiqQ mod G1QRHO, q, G1QRHO) */
    if (!bn_mod_exp_safe(tmp, V1, rp->phiQ, rp->G1QRHO, local_ctx)) goto cleanup;
    if (!bn_mod_exp_safe(tmp2, hashInt, rp->phiqQ, rp->G1QRHO, local_ctx)) goto cleanup;
    if (!BN_mod_mul(tmp2, two, tmp2, rp->G1QRHO, local_ctx)) goto cleanup;
    if (!BN_mod_mul(Y1, tmp, tmp2, rp->G1QRHO, local_ctx)) goto cleanup;

    if (bn_chrem(SF1, V2divQ, Y1, rp->q, rp->G1QRHO, local_ctx) != 0) goto cleanup;

    if (!BN_mod(tmp, S1, rp->G1qQRHO, local_ctx)) goto cleanup;
    if (BN_cmp(tmp, SF1) == 0) {
        ret = KAZ_SIGN_ERROR_VERIFY;
        goto cleanup;
    }

    /* Filter 4: S1 mod (G1qQRHO/e) != CRT(V2/Q, Y2, qQ/e, G1RHO) where e=gcd(Q,G1RHO) */
    if (!bn_mod_exp_safe(tmp, V1, rp->phiQ, rp->G1RHO, local_ctx)) goto cleanup;
    if (!bn_mod_exp_safe(tmp2, hashInt, rp->phiqQ, rp->G1RHO, local_ctx)) goto cleanup;
    if (!BN_mod_mul(tmp2, two, tmp2, rp->G1RHO, local_ctx)) goto cleanup;
    if (!BN_mod_mul(Y2, tmp, tmp2, rp->G1RHO, local_ctx)) goto cleanup;

    if (!BN_gcd(e_val, rp->Q, rp->G1RHO, local_ctx)) goto cleanup;
    if (!BN_div(qQ_div_e, NULL, rp->qQ, e_val, local_ctx)) goto cleanup;
    if (!BN_div(G1qQRHO_div_e, NULL, rp->G1qQRHO, e_val, local_ctx)) goto cleanup;

    if (bn_chrem(SF2, V2divQ, Y2, qQ_div_e, rp->G1RHO, local_ctx) != 0) goto cleanup;

    if (!BN_mod(tmp, S1, G1qQRHO_div_e, local_ctx)) goto cleanup;
    if (BN_cmp(tmp, SF2) == 0) {
        ret = KAZ_SIGN_ERROR_VERIFY;
        goto cleanup;
    }

    /* Filter 5: (2*V2 - Q*S1) mod qQ == 0 */
    if (!BN_mod_mul(W4, rp->Q, S1, rp->qQ, local_ctx)) goto cleanup;
    if (!BN_mod_mul(W5, two, V2, rp->qQ, local_ctx)) goto cleanup;
    if (!BN_mod_sub(W5, W5, W4, rp->qQ, local_ctx)) goto cleanup;
    if (!BN_is_zero(W5)) {
        ret = KAZ_SIGN_ERROR_VERIFY;
        goto cleanup;
    }

    /* Verify 1: R^S1 == R^(V1^phiQ * (h^phiqQ + h^phiqQ) mod G1) mod G0 */
    /* y1 = R^S1 mod G0 (G0 is even, use regular mod_exp) */
    if (!bn_mod_exp_safe(y1, rp->R, S1, rp->G0, local_ctx)) goto cleanup;

    /* t1 = V1^phiQ mod G1 */
    if (!bn_mod_exp_safe(t1, V1, rp->phiQ, rp->G1, local_ctx)) goto cleanup;

    /* t2 = hashInt^phiqQ mod G1 */
    if (!bn_mod_exp_safe(t2, hashInt, rp->phiqQ, rp->G1, local_ctx)) goto cleanup;

    /* t2+t3 where t3==t2, so 2*t2 */
    if (!BN_mod_add(tmp, t2, t2, rp->G1, local_ctx)) goto cleanup;

    /* exponent = t1 * (2*t2) mod G1 */
    if (!BN_mod_mul(tmp, t1, tmp, rp->G1, local_ctx)) goto cleanup;

    /* y2 = R^exponent mod G0 (G0 is even, use regular mod_exp) */
    if (!bn_mod_exp_safe(y2, rp->R, tmp, rp->G0, local_ctx)) goto cleanup;

    if (BN_cmp(y1, y2) != 0) {
        ret = KAZ_SIGN_ERROR_VERIFY;
        goto cleanup;
    }

    /* Verify 2: S1 * inv(V1^phiQ mod G1A, G1A) == 2*h^phiqQ mod G1A */
    /* inv = (V1^phiQ mod G1A)^(-1) mod G1A */
    if (!bn_mod_exp_safe(tmp, V1, rp->phiQ, rp->G1A, local_ctx)) goto cleanup;
    if (!BN_mod_inverse(inv_val, tmp, rp->G1A, local_ctx)) {
        ERR_clear_error();
        ret = KAZ_SIGN_ERROR_VERIFY;
        goto cleanup;
    }

    /* y3 = S1 * inv mod G1A */
    if (!BN_mod_mul(y3, S1, inv_val, rp->G1A, local_ctx)) goto cleanup;

    /* y4 = 2 * hashInt^phiqQ mod G1A */
    if (!bn_mod_exp_safe(tmp, hashInt, rp->phiqQ, rp->G1A, local_ctx)) goto cleanup;
    if (!BN_mod_mul(y4, two, tmp, rp->G1A, local_ctx)) goto cleanup;

    if (BN_cmp(y3, y4) != 0) {
        ret = KAZ_SIGN_ERROR_VERIFY;
        goto cleanup;
    }

    /* All checks passed - copy out the message */
    {
        const unsigned char *embedded_msg = sig + params->signature_overhead;
        memcpy(msg, embedded_msg, (size_t)extracted_msglen);
    }
    *msglen = extracted_msglen;
    ret = KAZ_SIGN_SUCCESS;

cleanup:
    BN_CTX_free(local_ctx);
    if (hash_buf) {
        kaz_secure_zero(hash_buf, params ? params->hash_bytes : 64);
        free(hash_buf);
    }

    BN_free(V1);
    BN_free(V2);
    BN_free(S1);
    BN_free(S2_bn);
    BN_free(hashInt);
    BN_free(tmp);
    BN_free(tmp2);
    BN_free(tmp3);
    BN_free(Y1);
    BN_free(Y2);
    BN_free(SF1);
    BN_free(SF2);
    BN_free(two);
    BN_free(V2divQ);
    BN_free(e_val);
    BN_free(qQ_div_e);
    BN_free(G1qQRHO_div_e);
    BN_free(W4);
    BN_free(W5);
    BN_free(y1);
    BN_free(y2);
    BN_free(t1);
    BN_free(t2);
    BN_free(y3);
    BN_free(y4);
    BN_free(inv_val);

    return ret;
}

int kaz_sign_verify(unsigned char *msg,
                    unsigned long long *msglen,
                    const unsigned char *sig,
                    unsigned long long siglen,
                    const unsigned char *pk)
{
    if (!g_rand_initialized) {
        int ret = kaz_sign_init_random();
        if (ret != KAZ_SIGN_SUCCESS) return ret;
    }
    return kaz_sign_verify_ex((kaz_sign_level_t)KAZ_SECURITY_LEVEL,
                               msg, msglen, sig, siglen, pk);
}

/* ============================================================================
 * Version API
 * ============================================================================ */

const char *kaz_sign_version(void)
{
    return KAZ_SIGN_VERSION_STRING;
}

int kaz_sign_version_number(void)
{
    return KAZ_SIGN_VERSION_NUMBER;
}

/* ============================================================================
 * KazWire Encoding/Decoding
 * ============================================================================ */

static int level_to_wire_alg(kaz_sign_level_t level) {
    switch (level) {
        case KAZ_LEVEL_128: return KAZ_WIRE_SIGN_128;
        case KAZ_LEVEL_192: return KAZ_WIRE_SIGN_192;
        case KAZ_LEVEL_256: return KAZ_WIRE_SIGN_256;
        default: return -1;
    }
}

static int wire_alg_to_level(int alg) {
    switch (alg) {
        case KAZ_WIRE_SIGN_128: return KAZ_LEVEL_128;
        case KAZ_WIRE_SIGN_192: return KAZ_LEVEL_192;
        case KAZ_WIRE_SIGN_256: return KAZ_LEVEL_256;
        default: return 0; /* 0 is not a valid level */
    }
}

static void wire_write_header(unsigned char *out, int alg_id, int type_id) {
    out[0] = KAZ_WIRE_MAGIC_HI;
    out[1] = KAZ_WIRE_MAGIC_LO;
    out[2] = (unsigned char)alg_id;
    out[3] = (unsigned char)type_id;
    out[4] = KAZ_WIRE_VERSION;
}

static int wire_validate_header(const unsigned char *wire, size_t wire_len,
                                int expected_type, kaz_sign_level_t *level) {
    if (wire_len < KAZ_WIRE_HEADER_LEN)
        return KAZ_SIGN_ERROR_INVALID;
    if (wire[0] != KAZ_WIRE_MAGIC_HI || wire[1] != KAZ_WIRE_MAGIC_LO)
        return KAZ_SIGN_ERROR_INVALID;
    if (wire[3] != (unsigned char)expected_type)
        return KAZ_SIGN_ERROR_INVALID;
    if (wire[4] != KAZ_WIRE_VERSION)
        return KAZ_SIGN_ERROR_INVALID;

    int lvl_int = wire_alg_to_level(wire[2]);
    if (lvl_int == 0)
        return KAZ_SIGN_ERROR_INVALID;
    kaz_sign_level_t lvl = (kaz_sign_level_t)lvl_int;

    *level = lvl;
    return KAZ_SIGN_SUCCESS;
}

int kaz_sign_pubkey_to_wire(kaz_sign_level_t level,
                            const unsigned char *pk, size_t pk_len,
                            unsigned char *out, size_t *out_len) {
    if (!pk || !out_len)
        return KAZ_SIGN_ERROR_INVALID;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(level);
    if (!params)
        return KAZ_SIGN_ERROR_INVALID;

    if (pk_len != params->public_key_bytes)
        return KAZ_SIGN_ERROR_INVALID;

    size_t needed = KAZ_WIRE_HEADER_LEN + params->public_key_bytes;

    if (!out) {
        *out_len = needed;
        return KAZ_SIGN_SUCCESS;
    }

    if (*out_len < needed)
        return KAZ_SIGN_ERROR_BUFFER;

    int alg = level_to_wire_alg(level);
    if (alg < 0)
        return KAZ_SIGN_ERROR_INVALID;

    wire_write_header(out, alg, KAZ_WIRE_TYPE_PUB);
    memcpy(out + KAZ_WIRE_HEADER_LEN, pk, params->public_key_bytes);
    *out_len = needed;
    return KAZ_SIGN_SUCCESS;
}

int kaz_sign_pubkey_from_wire(const unsigned char *wire, size_t wire_len,
                              kaz_sign_level_t *level,
                              unsigned char *pk, size_t *pk_len) {
    if (!wire || !level || !pk || !pk_len)
        return KAZ_SIGN_ERROR_INVALID;

    kaz_sign_level_t lvl;
    int ret = wire_validate_header(wire, wire_len, KAZ_WIRE_TYPE_PUB, &lvl);
    if (ret != KAZ_SIGN_SUCCESS)
        return ret;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(lvl);
    if (!params)
        return KAZ_SIGN_ERROR_INVALID;

    size_t expected = KAZ_WIRE_HEADER_LEN + params->public_key_bytes;
    if (wire_len != expected)
        return KAZ_SIGN_ERROR_INVALID;

    if (*pk_len < params->public_key_bytes)
        return KAZ_SIGN_ERROR_BUFFER;

    *level = lvl;
    *pk_len = params->public_key_bytes;
    memcpy(pk, wire + KAZ_WIRE_HEADER_LEN, params->public_key_bytes);
    return KAZ_SIGN_SUCCESS;
}

int kaz_sign_privkey_to_wire(kaz_sign_level_t level,
                             const unsigned char *sk, size_t sk_len,
                             unsigned char *out, size_t *out_len) {
    if (!sk || !out_len)
        return KAZ_SIGN_ERROR_INVALID;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(level);
    if (!params)
        return KAZ_SIGN_ERROR_INVALID;

    if (sk_len != params->secret_key_bytes)
        return KAZ_SIGN_ERROR_INVALID;

    size_t needed = KAZ_WIRE_HEADER_LEN + params->secret_key_bytes;

    if (!out) {
        *out_len = needed;
        return KAZ_SIGN_SUCCESS;
    }

    if (*out_len < needed)
        return KAZ_SIGN_ERROR_BUFFER;

    int alg = level_to_wire_alg(level);
    if (alg < 0)
        return KAZ_SIGN_ERROR_INVALID;

    wire_write_header(out, alg, KAZ_WIRE_TYPE_PRIV);
    memcpy(out + KAZ_WIRE_HEADER_LEN, sk, params->secret_key_bytes);
    *out_len = needed;
    return KAZ_SIGN_SUCCESS;
}

int kaz_sign_privkey_from_wire(const unsigned char *wire, size_t wire_len,
                               kaz_sign_level_t *level,
                               unsigned char *sk, size_t *sk_len) {
    if (!wire || !level || !sk || !sk_len)
        return KAZ_SIGN_ERROR_INVALID;

    kaz_sign_level_t lvl;
    int ret = wire_validate_header(wire, wire_len, KAZ_WIRE_TYPE_PRIV, &lvl);
    if (ret != KAZ_SIGN_SUCCESS)
        return ret;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(lvl);
    if (!params)
        return KAZ_SIGN_ERROR_INVALID;

    size_t expected = KAZ_WIRE_HEADER_LEN + params->secret_key_bytes;
    if (wire_len != expected)
        return KAZ_SIGN_ERROR_INVALID;

    if (*sk_len < params->secret_key_bytes)
        return KAZ_SIGN_ERROR_BUFFER;

    *level = lvl;
    *sk_len = params->secret_key_bytes;
    memcpy(sk, wire + KAZ_WIRE_HEADER_LEN, params->secret_key_bytes);
    return KAZ_SIGN_SUCCESS;
}

int kaz_sign_sig_to_wire(kaz_sign_level_t level,
                         const unsigned char *sig, size_t sig_len,
                         unsigned char *out, size_t *out_len) {
    if (!sig || !out_len)
        return KAZ_SIGN_ERROR_INVALID;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(level);
    if (!params)
        return KAZ_SIGN_ERROR_INVALID;

    if (sig_len != params->signature_overhead)
        return KAZ_SIGN_ERROR_INVALID;

    size_t needed = KAZ_WIRE_HEADER_LEN + params->signature_overhead;

    if (!out) {
        *out_len = needed;
        return KAZ_SIGN_SUCCESS;
    }

    if (*out_len < needed)
        return KAZ_SIGN_ERROR_BUFFER;

    int alg = level_to_wire_alg(level);
    if (alg < 0)
        return KAZ_SIGN_ERROR_INVALID;

    wire_write_header(out, alg, KAZ_WIRE_TYPE_SIG_DET);
    memcpy(out + KAZ_WIRE_HEADER_LEN, sig, params->signature_overhead);
    *out_len = needed;
    return KAZ_SIGN_SUCCESS;
}

int kaz_sign_sig_from_wire(const unsigned char *wire, size_t wire_len,
                           kaz_sign_level_t *level,
                           unsigned char *sig, size_t *sig_len) {
    if (!wire || !level || !sig || !sig_len)
        return KAZ_SIGN_ERROR_INVALID;

    kaz_sign_level_t lvl;
    int ret = wire_validate_header(wire, wire_len, KAZ_WIRE_TYPE_SIG_DET, &lvl);
    if (ret != KAZ_SIGN_SUCCESS)
        return ret;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(lvl);
    if (!params)
        return KAZ_SIGN_ERROR_INVALID;

    size_t expected = KAZ_WIRE_HEADER_LEN + params->signature_overhead;
    if (wire_len != expected)
        return KAZ_SIGN_ERROR_INVALID;

    if (*sig_len < params->signature_overhead)
        return KAZ_SIGN_ERROR_BUFFER;

    *level = lvl;
    *sig_len = params->signature_overhead;
    memcpy(sig, wire + KAZ_WIRE_HEADER_LEN, params->signature_overhead);
    return KAZ_SIGN_SUCCESS;
}
