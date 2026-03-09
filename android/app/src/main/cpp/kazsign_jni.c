/*
 * KAZ-SIGN JNI Bridge
 * Version 2.0.0
 *
 * JNI native interface for Android applications.
 * Provides runtime security level selection (128, 192, 256).
 */

#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>

#include "kaz/sign.h"
#include "kaz/security.h"

#define LOG_TAG "KazSign"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/* ============================================================================
 * Helper Functions
 * ============================================================================ */

static kaz_sign_level_t int_to_level(jint level) {
    switch (level) {
        case 128: return KAZ_LEVEL_128;
        case 192: return KAZ_LEVEL_192;
        case 256: return KAZ_LEVEL_256;
        default: return (kaz_sign_level_t)0; /* Invalid - rejected by kaz_sign_get_level_params */
    }
}

static void throw_exception(JNIEnv *env, const char *class_name, const char *message) {
    jclass exc_class = (*env)->FindClass(env, class_name);
    if (exc_class != NULL) {
        (*env)->ThrowNew(env, exc_class, message);
        (*env)->DeleteLocalRef(env, exc_class);
    }
}

static void throw_kazsign_exception(JNIEnv *env, int error_code) {
    const char *message;
    switch (error_code) {
        case KAZ_SIGN_ERROR_MEMORY:
            message = "Memory allocation failed";
            break;
        case KAZ_SIGN_ERROR_RNG:
            message = "Random number generation failed";
            break;
        case KAZ_SIGN_ERROR_INVALID:
            message = "Invalid parameter";
            break;
        case KAZ_SIGN_ERROR_VERIFY:
            message = "Signature verification failed";
            break;
        case KAZ_SIGN_ERROR_DER:
            message = "DER encoding/decoding failed";
            break;
        case KAZ_SIGN_ERROR_X509:
            message = "X.509 certificate operation failed";
            break;
        case KAZ_SIGN_ERROR_P12:
            message = "PKCS#12 keystore operation failed";
            break;
        case KAZ_SIGN_ERROR_HASH:
            message = "Hash computation failed";
            break;
        case KAZ_SIGN_ERROR_BUFFER:
            message = "Buffer too small";
            break;
        default:
            message = "Unknown error";
            break;
    }
    throw_exception(env, "my/ssdid/wallet/domain/crypto/kazsign/KazSignException", message);
}

/* ============================================================================
 * Version API
 * ============================================================================ */

JNIEXPORT jstring JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeGetVersion(JNIEnv *env, jclass clazz) {
    (void)clazz;
    const char *version = kaz_sign_version();
    return (*env)->NewStringUTF(env, version);
}

JNIEXPORT jint JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeGetVersionNumber(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    return kaz_sign_version_number();
}

/* ============================================================================
 * Initialization API
 * ============================================================================ */

JNIEXPORT jint JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeInitLevel(JNIEnv *env, jclass clazz, jint level) {
    (void)env;
    (void)clazz;
    return kaz_sign_init_level(int_to_level(level));
}

JNIEXPORT void JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeClearLevel(JNIEnv *env, jclass clazz, jint level) {
    (void)env;
    (void)clazz;
    kaz_sign_clear_level(int_to_level(level));
}

JNIEXPORT void JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeClearAll(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    kaz_sign_clear_all();
}

/* ============================================================================
 * Parameter API
 * ============================================================================ */

JNIEXPORT jint JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeGetSecretKeyBytes(JNIEnv *env, jclass clazz, jint level) {
    (void)env;
    (void)clazz;
    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    return params ? (jint)params->secret_key_bytes : 0;
}

JNIEXPORT jint JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeGetPublicKeyBytes(JNIEnv *env, jclass clazz, jint level) {
    (void)env;
    (void)clazz;
    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    return params ? (jint)params->public_key_bytes : 0;
}

JNIEXPORT jint JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeGetSignatureOverhead(JNIEnv *env, jclass clazz, jint level) {
    (void)env;
    (void)clazz;
    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    return params ? (jint)params->signature_overhead : 0;
}

JNIEXPORT jint JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeGetHashBytes(JNIEnv *env, jclass clazz, jint level) {
    (void)env;
    (void)clazz;
    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    return params ? (jint)params->hash_bytes : 0;
}

/* ============================================================================
 * Key Generation API
 * ============================================================================ */

JNIEXPORT jobject JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeGenerateKeyPair(JNIEnv *env, jclass clazz, jint level) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return NULL;
    }

    // Allocate buffers
    unsigned char *pk = malloc(params->public_key_bytes);
    unsigned char *sk = malloc(params->secret_key_bytes);

    if (!pk || !sk) {
        free(pk);
        free(sk);
        throw_kazsign_exception(env, KAZ_SIGN_ERROR_MEMORY);
        return NULL;
    }

    // Generate key pair
    int result = kaz_sign_keypair_ex(int_to_level(level), pk, sk);
    if (result != KAZ_SIGN_SUCCESS) {
        free(pk);
        kaz_secure_zero(sk, params->secret_key_bytes);
        free(sk);
        throw_kazsign_exception(env, result);
        return NULL;
    }

    // Create byte arrays
    jbyteArray publicKeyArray = (*env)->NewByteArray(env, params->public_key_bytes);
    if (!publicKeyArray) {
        free(pk);
        kaz_secure_zero(sk, params->secret_key_bytes);
        free(sk);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate byte arrays");
        return NULL;
    }

    jbyteArray secretKeyArray = (*env)->NewByteArray(env, params->secret_key_bytes);
    if (!secretKeyArray) {
        (*env)->DeleteLocalRef(env, publicKeyArray);
        free(pk);
        kaz_secure_zero(sk, params->secret_key_bytes);
        free(sk);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate byte arrays");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, publicKeyArray, 0, params->public_key_bytes, (jbyte *)pk);
    (*env)->SetByteArrayRegion(env, secretKeyArray, 0, params->secret_key_bytes, (jbyte *)sk);

    free(pk);
    kaz_secure_zero(sk, params->secret_key_bytes);
    free(sk);

    // Create KeyPair object
    jclass keyPairClass = (*env)->FindClass(env, "my/ssdid/wallet/domain/crypto/kazsign/KeyPair");
    if (!keyPairClass) {
        return NULL;
    }

    jmethodID constructor = (*env)->GetMethodID(env, keyPairClass, "<init>", "([B[BI)V");
    if (!constructor) {
        return NULL;
    }

    jobject keyPair = (*env)->NewObject(env, keyPairClass, constructor,
                                        publicKeyArray, secretKeyArray, level);

    return keyPair;
}

/* ============================================================================
 * Signing API
 * ============================================================================ */

JNIEXPORT jbyteArray JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeSign(JNIEnv *env, jclass clazz,
                                               jint level, jbyteArray message, jbyteArray secretKey) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return NULL;
    }

    // Get input data
    jsize msgLen = (*env)->GetArrayLength(env, message);
    jsize skLen = (*env)->GetArrayLength(env, secretKey);

    if (skLen != (jsize)params->secret_key_bytes) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid secret key size");
        return NULL;
    }

    jbyte *msgData = (*env)->GetByteArrayElements(env, message, NULL);
    jbyte *skData = (*env)->GetByteArrayElements(env, secretKey, NULL);

    if (!msgData || !skData) {
        if (msgData) (*env)->ReleaseByteArrayElements(env, message, msgData, JNI_ABORT);
        if (skData) (*env)->ReleaseByteArrayElements(env, secretKey, skData, JNI_ABORT);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return NULL;
    }

    // Allocate signature buffer
    size_t maxSigLen = params->signature_overhead + msgLen;
    unsigned char *sig = malloc(maxSigLen);
    if (!sig) {
        (*env)->ReleaseByteArrayElements(env, message, msgData, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, secretKey, skData, JNI_ABORT);
        throw_kazsign_exception(env, KAZ_SIGN_ERROR_MEMORY);
        return NULL;
    }

    // Sign
    unsigned long long sigLen = 0;
    int result = kaz_sign_signature_ex(int_to_level(level), sig, &sigLen,
                                       (unsigned char *)msgData, msgLen,
                                       (unsigned char *)skData);

    (*env)->ReleaseByteArrayElements(env, message, msgData, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, secretKey, skData, JNI_ABORT);

    if (result != KAZ_SIGN_SUCCESS) {
        free(sig);
        throw_kazsign_exception(env, result);
        return NULL;
    }

    // Create result array
    jbyteArray signatureArray = (*env)->NewByteArray(env, (jsize)sigLen);
    if (!signatureArray) {
        free(sig);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate signature array");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, signatureArray, 0, (jsize)sigLen, (jbyte *)sig);
    free(sig);

    return signatureArray;
}

/* ============================================================================
 * Verification API
 * ============================================================================ */

JNIEXPORT jobject JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeVerify(JNIEnv *env, jclass clazz,
                                                 jint level, jbyteArray signature, jbyteArray publicKey) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return NULL;
    }

    // Get input data
    jsize sigLen = (*env)->GetArrayLength(env, signature);
    jsize pkLen = (*env)->GetArrayLength(env, publicKey);

    if (pkLen != (jsize)params->public_key_bytes) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid public key size");
        return NULL;
    }

    jbyte *sigData = (*env)->GetByteArrayElements(env, signature, NULL);
    jbyte *pkData = (*env)->GetByteArrayElements(env, publicKey, NULL);

    if (!sigData || !pkData) {
        if (sigData) (*env)->ReleaseByteArrayElements(env, signature, sigData, JNI_ABORT);
        if (pkData) (*env)->ReleaseByteArrayElements(env, publicKey, pkData, JNI_ABORT);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return NULL;
    }

    // Allocate message buffer
    size_t maxMsgLen = sigLen > (jsize)params->signature_overhead ?
                       sigLen - params->signature_overhead : 0;
    unsigned char *msg = malloc(maxMsgLen > 0 ? maxMsgLen : 1);

    if (!msg) {
        (*env)->ReleaseByteArrayElements(env, signature, sigData, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, publicKey, pkData, JNI_ABORT);
        throw_kazsign_exception(env, KAZ_SIGN_ERROR_MEMORY);
        return NULL;
    }

    // Verify
    unsigned long long msgLen = 0;
    int result = kaz_sign_verify_ex(int_to_level(level), msg, &msgLen,
                                    (unsigned char *)sigData, sigLen,
                                    (unsigned char *)pkData);

    (*env)->ReleaseByteArrayElements(env, signature, sigData, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, publicKey, pkData, JNI_ABORT);

    // Create VerificationResult object
    jclass resultClass = (*env)->FindClass(env, "my/ssdid/wallet/domain/crypto/kazsign/VerificationResult");
    if (!resultClass) {
        free(msg);
        return NULL;
    }

    jmethodID constructor = (*env)->GetMethodID(env, resultClass, "<init>", "(Z[BI)V");
    if (!constructor) {
        free(msg);
        return NULL;
    }

    jboolean isValid = (result == KAZ_SIGN_SUCCESS) ? JNI_TRUE : JNI_FALSE;
    jbyteArray messageArray = NULL;

    if (isValid && msgLen > 0) {
        messageArray = (*env)->NewByteArray(env, (jsize)msgLen);
        if (messageArray) {
            (*env)->SetByteArrayRegion(env, messageArray, 0, (jsize)msgLen, (jbyte *)msg);
        }
    }

    free(msg);

    jobject verificationResult = (*env)->NewObject(env, resultClass, constructor,
                                                   isValid, messageArray, level);

    return verificationResult;
}

/* ============================================================================
 * Hash API
 * ============================================================================ */

JNIEXPORT jbyteArray JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeHash(JNIEnv *env, jclass clazz,
                                               jint level, jbyteArray message) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return NULL;
    }

    // Get input data
    jsize msgLen = (*env)->GetArrayLength(env, message);
    jbyte *msgData = (*env)->GetByteArrayElements(env, message, NULL);

    if (!msgData) {
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return NULL;
    }

    // Allocate hash buffer
    unsigned char *hash = malloc(params->hash_bytes);
    if (!hash) {
        (*env)->ReleaseByteArrayElements(env, message, msgData, JNI_ABORT);
        throw_kazsign_exception(env, KAZ_SIGN_ERROR_MEMORY);
        return NULL;
    }

    // Hash
    int result = kaz_sign_hash_ex(int_to_level(level),
                                  (unsigned char *)msgData, msgLen, hash);

    (*env)->ReleaseByteArrayElements(env, message, msgData, JNI_ABORT);

    if (result != KAZ_SIGN_SUCCESS) {
        free(hash);
        throw_kazsign_exception(env, result);
        return NULL;
    }

    // Create result array
    jbyteArray hashArray = (*env)->NewByteArray(env, params->hash_bytes);
    if (!hashArray) {
        free(hash);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate hash array");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, hashArray, 0, params->hash_bytes, (jbyte *)hash);
    free(hash);

    return hashArray;
}

/* ============================================================================
 * Detached Signature API
 * ============================================================================ */

JNIEXPORT jbyteArray JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeSignDetached(JNIEnv *env, jclass clazz,
                                                        jint level, jbyteArray message, jbyteArray secretKey) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return NULL;
    }

    // Get input data
    jsize msgLen = (*env)->GetArrayLength(env, message);
    jsize skLen = (*env)->GetArrayLength(env, secretKey);

    if (skLen != (jsize)params->secret_key_bytes) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid secret key size");
        return NULL;
    }

    jbyte *msgData = (*env)->GetByteArrayElements(env, message, NULL);
    jbyte *skData = (*env)->GetByteArrayElements(env, secretKey, NULL);

    if (!msgData || !skData) {
        if (msgData) (*env)->ReleaseByteArrayElements(env, message, msgData, JNI_ABORT);
        if (skData) (*env)->ReleaseByteArrayElements(env, secretKey, skData, JNI_ABORT);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return NULL;
    }

    // Allocate signature buffer
    size_t maxSigLen = kaz_sign_detached_sig_bytes(int_to_level(level));
    unsigned char *sig = malloc(maxSigLen);
    if (!sig) {
        (*env)->ReleaseByteArrayElements(env, message, msgData, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, secretKey, skData, JNI_ABORT);
        throw_kazsign_exception(env, KAZ_SIGN_ERROR_MEMORY);
        return NULL;
    }

    // Sign detached
    unsigned long long sigLen = 0;
    int result = kaz_sign_detached_ex(int_to_level(level), sig, &sigLen,
                                       (unsigned char *)msgData, msgLen,
                                       (unsigned char *)skData);

    (*env)->ReleaseByteArrayElements(env, message, msgData, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, secretKey, skData, JNI_ABORT);

    if (result != KAZ_SIGN_SUCCESS) {
        free(sig);
        throw_kazsign_exception(env, result);
        return NULL;
    }

    // Create result array
    jbyteArray signatureArray = (*env)->NewByteArray(env, (jsize)sigLen);
    if (!signatureArray) {
        free(sig);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate signature array");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, signatureArray, 0, (jsize)sigLen, (jbyte *)sig);
    free(sig);

    return signatureArray;
}

JNIEXPORT jboolean JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeVerifyDetached(JNIEnv *env, jclass clazz,
                                                           jint level, jbyteArray message,
                                                           jbyteArray signature, jbyteArray publicKey) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return JNI_FALSE;
    }

    // Get input data
    jsize msgLen = (*env)->GetArrayLength(env, message);
    jsize sigLen = (*env)->GetArrayLength(env, signature);
    jsize pkLen = (*env)->GetArrayLength(env, publicKey);

    if (pkLen != (jsize)params->public_key_bytes) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid public key size");
        return JNI_FALSE;
    }

    jbyte *msgData = (*env)->GetByteArrayElements(env, message, NULL);
    jbyte *sigData = (*env)->GetByteArrayElements(env, signature, NULL);
    jbyte *pkData = (*env)->GetByteArrayElements(env, publicKey, NULL);

    if (!msgData || !sigData || !pkData) {
        if (msgData) (*env)->ReleaseByteArrayElements(env, message, msgData, JNI_ABORT);
        if (sigData) (*env)->ReleaseByteArrayElements(env, signature, sigData, JNI_ABORT);
        if (pkData) (*env)->ReleaseByteArrayElements(env, publicKey, pkData, JNI_ABORT);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return JNI_FALSE;
    }

    // Verify detached
    int result = kaz_sign_verify_detached_ex(int_to_level(level),
                                              (unsigned char *)sigData, sigLen,
                                              (unsigned char *)msgData, msgLen,
                                              (unsigned char *)pkData);

    (*env)->ReleaseByteArrayElements(env, message, msgData, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, signature, sigData, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, publicKey, pkData, JNI_ABORT);

    return (result == KAZ_SIGN_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

/* ============================================================================
 * SHA3-256 API
 * ============================================================================ */

JNIEXPORT jbyteArray JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeSha3_1256(JNIEnv *env, jclass clazz,
                                                      jbyteArray data) {
    (void)clazz;

    // Get input data
    jsize dataLen = (*env)->GetArrayLength(env, data);
    jbyte *dataBytes = (*env)->GetByteArrayElements(env, data, NULL);

    if (!dataBytes) {
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return NULL;
    }

    // Allocate hash buffer (SHA3-256 = 32 bytes)
    unsigned char hash[32];

    // Hash
    int result = kaz_sha3_256((unsigned char *)dataBytes, dataLen, hash);

    (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);

    if (result != KAZ_SIGN_SUCCESS) {
        throw_kazsign_exception(env, result);
        return NULL;
    }

    // Create result array
    jbyteArray hashArray = (*env)->NewByteArray(env, 32);
    if (!hashArray) {
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate hash array");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, hashArray, 0, 32, (jbyte *)hash);

    return hashArray;
}

/* ============================================================================
 * DER Key Encoding API
 * ============================================================================ */

JNIEXPORT jbyteArray JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativePublicKeyToDer(JNIEnv *env, jclass clazz,
                                                           jint level, jbyteArray publicKey) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return NULL;
    }

    jsize pkLen = (*env)->GetArrayLength(env, publicKey);
    if (pkLen != (jsize)params->public_key_bytes) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid public key size");
        return NULL;
    }

    jbyte *pkData = (*env)->GetByteArrayElements(env, publicKey, NULL);
    if (!pkData) {
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return NULL;
    }

    // Allocate DER buffer (generous size)
    unsigned long long derLen = params->public_key_bytes + 256;
    unsigned char *der = malloc(derLen);
    if (!der) {
        (*env)->ReleaseByteArrayElements(env, publicKey, pkData, JNI_ABORT);
        throw_kazsign_exception(env, KAZ_SIGN_ERROR_MEMORY);
        return NULL;
    }

    int result = kaz_sign_pubkey_to_der(int_to_level(level),
                                         (unsigned char *)pkData, der, &derLen);

    (*env)->ReleaseByteArrayElements(env, publicKey, pkData, JNI_ABORT);

    if (result != KAZ_SIGN_SUCCESS) {
        free(der);
        throw_kazsign_exception(env, result);
        return NULL;
    }

    jbyteArray derArray = (*env)->NewByteArray(env, (jsize)derLen);
    if (!derArray) {
        free(der);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate DER array");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, derArray, 0, (jsize)derLen, (jbyte *)der);
    free(der);

    return derArray;
}

JNIEXPORT jbyteArray JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativePublicKeyFromDer(JNIEnv *env, jclass clazz,
                                                             jint level, jbyteArray der) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return NULL;
    }

    jsize derLen = (*env)->GetArrayLength(env, der);
    jbyte *derData = (*env)->GetByteArrayElements(env, der, NULL);
    if (!derData) {
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return NULL;
    }

    unsigned char *pk = malloc(params->public_key_bytes);
    if (!pk) {
        (*env)->ReleaseByteArrayElements(env, der, derData, JNI_ABORT);
        throw_kazsign_exception(env, KAZ_SIGN_ERROR_MEMORY);
        return NULL;
    }

    int result = kaz_sign_pubkey_from_der(int_to_level(level),
                                           (unsigned char *)derData, derLen, pk);

    (*env)->ReleaseByteArrayElements(env, der, derData, JNI_ABORT);

    if (result != KAZ_SIGN_SUCCESS) {
        free(pk);
        throw_kazsign_exception(env, result);
        return NULL;
    }

    jbyteArray pkArray = (*env)->NewByteArray(env, (jsize)params->public_key_bytes);
    if (!pkArray) {
        free(pk);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate public key array");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, pkArray, 0, (jsize)params->public_key_bytes, (jbyte *)pk);
    free(pk);

    return pkArray;
}

JNIEXPORT jbyteArray JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativePrivateKeyToDer(JNIEnv *env, jclass clazz,
                                                            jint level, jbyteArray secretKey) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return NULL;
    }

    jsize skLen = (*env)->GetArrayLength(env, secretKey);
    if (skLen != (jsize)params->secret_key_bytes) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid secret key size");
        return NULL;
    }

    jbyte *skData = (*env)->GetByteArrayElements(env, secretKey, NULL);
    if (!skData) {
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return NULL;
    }

    // Allocate DER buffer (generous size)
    unsigned long long derLen = params->secret_key_bytes + 256;
    unsigned char *der = malloc(derLen);
    if (!der) {
        (*env)->ReleaseByteArrayElements(env, secretKey, skData, JNI_ABORT);
        throw_kazsign_exception(env, KAZ_SIGN_ERROR_MEMORY);
        return NULL;
    }

    int result = kaz_sign_privkey_to_der(int_to_level(level),
                                          (unsigned char *)skData, der, &derLen);

    (*env)->ReleaseByteArrayElements(env, secretKey, skData, JNI_ABORT);

    if (result != KAZ_SIGN_SUCCESS) {
        free(der);
        throw_kazsign_exception(env, result);
        return NULL;
    }

    jbyteArray derArray = (*env)->NewByteArray(env, (jsize)derLen);
    if (!derArray) {
        free(der);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate DER array");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, derArray, 0, (jsize)derLen, (jbyte *)der);
    free(der);

    return derArray;
}

JNIEXPORT jbyteArray JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativePrivateKeyFromDer(JNIEnv *env, jclass clazz,
                                                              jint level, jbyteArray der) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return NULL;
    }

    jsize derLen = (*env)->GetArrayLength(env, der);
    jbyte *derData = (*env)->GetByteArrayElements(env, der, NULL);
    if (!derData) {
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return NULL;
    }

    unsigned char *sk = malloc(params->secret_key_bytes);
    if (!sk) {
        (*env)->ReleaseByteArrayElements(env, der, derData, JNI_ABORT);
        throw_kazsign_exception(env, KAZ_SIGN_ERROR_MEMORY);
        return NULL;
    }

    int result = kaz_sign_privkey_from_der(int_to_level(level),
                                            (unsigned char *)derData, derLen, sk);

    (*env)->ReleaseByteArrayElements(env, der, derData, JNI_ABORT);

    if (result != KAZ_SIGN_SUCCESS) {
        kaz_secure_zero(sk, params->secret_key_bytes);
        free(sk);
        throw_kazsign_exception(env, result);
        return NULL;
    }

    jbyteArray skArray = (*env)->NewByteArray(env, (jsize)params->secret_key_bytes);
    if (!skArray) {
        kaz_secure_zero(sk, params->secret_key_bytes);
        free(sk);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate secret key array");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, skArray, 0, (jsize)params->secret_key_bytes, (jbyte *)sk);
    kaz_secure_zero(sk, params->secret_key_bytes);
    free(sk);

    return skArray;
}

/* ============================================================================
 * X.509 Certificate API
 * ============================================================================ */

JNIEXPORT jbyteArray JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeGenerateCsr(JNIEnv *env, jclass clazz,
                                                        jint level, jbyteArray secretKey,
                                                        jbyteArray publicKey, jstring subject) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return NULL;
    }

    jsize skLen = (*env)->GetArrayLength(env, secretKey);
    jsize pkLen = (*env)->GetArrayLength(env, publicKey);

    if (skLen != (jsize)params->secret_key_bytes) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid secret key size");
        return NULL;
    }
    if (pkLen != (jsize)params->public_key_bytes) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid public key size");
        return NULL;
    }

    jbyte *skData = (*env)->GetByteArrayElements(env, secretKey, NULL);
    jbyte *pkData = (*env)->GetByteArrayElements(env, publicKey, NULL);
    const char *subjectStr = (*env)->GetStringUTFChars(env, subject, NULL);

    if (!skData || !pkData || !subjectStr) {
        if (skData) (*env)->ReleaseByteArrayElements(env, secretKey, skData, JNI_ABORT);
        if (pkData) (*env)->ReleaseByteArrayElements(env, publicKey, pkData, JNI_ABORT);
        if (subjectStr) (*env)->ReleaseStringUTFChars(env, subject, subjectStr);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return NULL;
    }

    // Allocate CSR buffer
    unsigned long long csrLen = 4096;
    unsigned char *csr = malloc(csrLen);
    if (!csr) {
        (*env)->ReleaseByteArrayElements(env, secretKey, skData, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, publicKey, pkData, JNI_ABORT);
        (*env)->ReleaseStringUTFChars(env, subject, subjectStr);
        throw_kazsign_exception(env, KAZ_SIGN_ERROR_MEMORY);
        return NULL;
    }

    int result = kaz_sign_generate_csr(int_to_level(level),
                                        (unsigned char *)skData,
                                        (unsigned char *)pkData,
                                        subjectStr, csr, &csrLen);

    (*env)->ReleaseByteArrayElements(env, secretKey, skData, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, publicKey, pkData, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, subject, subjectStr);

    if (result != KAZ_SIGN_SUCCESS) {
        free(csr);
        throw_kazsign_exception(env, result);
        return NULL;
    }

    jbyteArray csrArray = (*env)->NewByteArray(env, (jsize)csrLen);
    if (!csrArray) {
        free(csr);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate CSR array");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, csrArray, 0, (jsize)csrLen, (jbyte *)csr);
    free(csr);

    return csrArray;
}

JNIEXPORT jboolean JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeVerifyCsr(JNIEnv *env, jclass clazz,
                                                        jint level, jbyteArray csrData) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return JNI_FALSE;
    }

    jsize csrLen = (*env)->GetArrayLength(env, csrData);
    jbyte *csrBytes = (*env)->GetByteArrayElements(env, csrData, NULL);

    if (!csrBytes) {
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return JNI_FALSE;
    }

    int result = kaz_sign_verify_csr(int_to_level(level),
                                      (unsigned char *)csrBytes, csrLen);

    (*env)->ReleaseByteArrayElements(env, csrData, csrBytes, JNI_ABORT);

    return result == KAZ_SIGN_SUCCESS ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeIssueCertificate(JNIEnv *env, jclass clazz,
                                                             jint level, jbyteArray issuerSk,
                                                             jbyteArray issuerPk,
                                                             jstring issuerName,
                                                             jbyteArray csr,
                                                             jlong serial, jint days) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return NULL;
    }

    jsize skLen = (*env)->GetArrayLength(env, issuerSk);
    jsize pkLen = (*env)->GetArrayLength(env, issuerPk);
    jsize csrLen = (*env)->GetArrayLength(env, csr);

    if (skLen != (jsize)params->secret_key_bytes) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid issuer secret key size");
        return NULL;
    }
    if (pkLen != (jsize)params->public_key_bytes) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid issuer public key size");
        return NULL;
    }

    jbyte *skData = (*env)->GetByteArrayElements(env, issuerSk, NULL);
    jbyte *pkData = (*env)->GetByteArrayElements(env, issuerPk, NULL);
    jbyte *csrData = (*env)->GetByteArrayElements(env, csr, NULL);
    const char *issuerNameStr = (*env)->GetStringUTFChars(env, issuerName, NULL);

    if (!skData || !pkData || !csrData || !issuerNameStr) {
        if (skData) (*env)->ReleaseByteArrayElements(env, issuerSk, skData, JNI_ABORT);
        if (pkData) (*env)->ReleaseByteArrayElements(env, issuerPk, pkData, JNI_ABORT);
        if (csrData) (*env)->ReleaseByteArrayElements(env, csr, csrData, JNI_ABORT);
        if (issuerNameStr) (*env)->ReleaseStringUTFChars(env, issuerName, issuerNameStr);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return NULL;
    }

    // Allocate certificate buffer
    unsigned long long certLen = 8192;
    unsigned char *cert = malloc(certLen);
    if (!cert) {
        (*env)->ReleaseByteArrayElements(env, issuerSk, skData, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, issuerPk, pkData, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, csr, csrData, JNI_ABORT);
        (*env)->ReleaseStringUTFChars(env, issuerName, issuerNameStr);
        throw_kazsign_exception(env, KAZ_SIGN_ERROR_MEMORY);
        return NULL;
    }

    int result = kaz_sign_issue_certificate(int_to_level(level),
                                             (unsigned char *)skData,
                                             (unsigned char *)pkData,
                                             issuerNameStr,
                                             (unsigned char *)csrData, csrLen,
                                             (unsigned long long)serial, days,
                                             cert, &certLen);

    (*env)->ReleaseByteArrayElements(env, issuerSk, skData, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, issuerPk, pkData, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, csr, csrData, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, issuerName, issuerNameStr);

    if (result != KAZ_SIGN_SUCCESS) {
        free(cert);
        throw_kazsign_exception(env, result);
        return NULL;
    }

    jbyteArray certArray = (*env)->NewByteArray(env, (jsize)certLen);
    if (!certArray) {
        free(cert);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate certificate array");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, certArray, 0, (jsize)certLen, (jbyte *)cert);
    free(cert);

    return certArray;
}

JNIEXPORT jboolean JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeVerifyCertificate(JNIEnv *env, jclass clazz,
                                                              jint level, jbyteArray cert,
                                                              jbyteArray issuerPk) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return JNI_FALSE;
    }

    jsize certLen = (*env)->GetArrayLength(env, cert);
    jsize pkLen = (*env)->GetArrayLength(env, issuerPk);

    if (pkLen != (jsize)params->public_key_bytes) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid issuer public key size");
        return JNI_FALSE;
    }

    jbyte *certData = (*env)->GetByteArrayElements(env, cert, NULL);
    jbyte *pkData = (*env)->GetByteArrayElements(env, issuerPk, NULL);

    if (!certData || !pkData) {
        if (certData) (*env)->ReleaseByteArrayElements(env, cert, certData, JNI_ABORT);
        if (pkData) (*env)->ReleaseByteArrayElements(env, issuerPk, pkData, JNI_ABORT);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return JNI_FALSE;
    }

    int result = kaz_sign_verify_certificate(int_to_level(level),
                                              (unsigned char *)certData, certLen,
                                              (unsigned char *)pkData);

    (*env)->ReleaseByteArrayElements(env, cert, certData, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, issuerPk, pkData, JNI_ABORT);

    return (result == KAZ_SIGN_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeExtractPublicKey(JNIEnv *env, jclass clazz,
                                                             jint level, jbyteArray cert) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return NULL;
    }

    jsize certLen = (*env)->GetArrayLength(env, cert);
    jbyte *certData = (*env)->GetByteArrayElements(env, cert, NULL);
    if (!certData) {
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return NULL;
    }

    unsigned char *pk = malloc(params->public_key_bytes);
    if (!pk) {
        (*env)->ReleaseByteArrayElements(env, cert, certData, JNI_ABORT);
        throw_kazsign_exception(env, KAZ_SIGN_ERROR_MEMORY);
        return NULL;
    }

    int result = kaz_sign_cert_extract_pubkey(int_to_level(level),
                                               (unsigned char *)certData, certLen, pk);

    (*env)->ReleaseByteArrayElements(env, cert, certData, JNI_ABORT);

    if (result != KAZ_SIGN_SUCCESS) {
        free(pk);
        throw_kazsign_exception(env, result);
        return NULL;
    }

    jbyteArray pkArray = (*env)->NewByteArray(env, (jsize)params->public_key_bytes);
    if (!pkArray) {
        free(pk);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate public key array");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, pkArray, 0, (jsize)params->public_key_bytes, (jbyte *)pk);
    free(pk);

    return pkArray;
}

/* ============================================================================
 * PKCS#12 Keystore API
 * ============================================================================ */

JNIEXPORT jbyteArray JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeCreateP12(JNIEnv *env, jclass clazz,
                                                      jint level, jbyteArray secretKey,
                                                      jbyteArray publicKey, jbyteArray cert,
                                                      jstring password, jstring name) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return NULL;
    }

    jsize skLen = (*env)->GetArrayLength(env, secretKey);
    jsize pkLen = (*env)->GetArrayLength(env, publicKey);

    if (skLen != (jsize)params->secret_key_bytes) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid secret key size");
        return NULL;
    }
    if (pkLen != (jsize)params->public_key_bytes) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid public key size");
        return NULL;
    }

    jbyte *skData = (*env)->GetByteArrayElements(env, secretKey, NULL);
    jbyte *pkData = (*env)->GetByteArrayElements(env, publicKey, NULL);
    const char *passwordStr = (*env)->GetStringUTFChars(env, password, NULL);
    const char *nameStr = (*env)->GetStringUTFChars(env, name, NULL);

    // Certificate is optional
    jbyte *certData = NULL;
    jsize certLen = 0;
    if (cert != NULL) {
        certLen = (*env)->GetArrayLength(env, cert);
        certData = (*env)->GetByteArrayElements(env, cert, NULL);
    }

    if (!skData || !pkData || !passwordStr || !nameStr) {
        if (skData) (*env)->ReleaseByteArrayElements(env, secretKey, skData, JNI_ABORT);
        if (pkData) (*env)->ReleaseByteArrayElements(env, publicKey, pkData, JNI_ABORT);
        if (certData) (*env)->ReleaseByteArrayElements(env, cert, certData, JNI_ABORT);
        if (passwordStr) (*env)->ReleaseStringUTFChars(env, password, passwordStr);
        if (nameStr) (*env)->ReleaseStringUTFChars(env, name, nameStr);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return NULL;
    }

    // Allocate P12 buffer
    unsigned long long p12Len = 16384;
    unsigned char *p12 = malloc(p12Len);
    if (!p12) {
        (*env)->ReleaseByteArrayElements(env, secretKey, skData, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, publicKey, pkData, JNI_ABORT);
        if (certData) (*env)->ReleaseByteArrayElements(env, cert, certData, JNI_ABORT);
        (*env)->ReleaseStringUTFChars(env, password, passwordStr);
        (*env)->ReleaseStringUTFChars(env, name, nameStr);
        throw_kazsign_exception(env, KAZ_SIGN_ERROR_MEMORY);
        return NULL;
    }

    int result = kaz_sign_create_p12(int_to_level(level),
                                      (unsigned char *)skData,
                                      (unsigned char *)pkData,
                                      certData ? (unsigned char *)certData : NULL,
                                      certLen,
                                      passwordStr, nameStr, p12, &p12Len);

    (*env)->ReleaseByteArrayElements(env, secretKey, skData, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, publicKey, pkData, JNI_ABORT);
    if (certData) (*env)->ReleaseByteArrayElements(env, cert, certData, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, password, passwordStr);
    (*env)->ReleaseStringUTFChars(env, name, nameStr);

    if (result != KAZ_SIGN_SUCCESS) {
        free(p12);
        throw_kazsign_exception(env, result);
        return NULL;
    }

    jbyteArray p12Array = (*env)->NewByteArray(env, (jsize)p12Len);
    if (!p12Array) {
        free(p12);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate P12 array");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, p12Array, 0, (jsize)p12Len, (jbyte *)p12);
    free(p12);

    return p12Array;
}

JNIEXPORT jobject JNICALL
Java_my_ssdid_wallet_domain_crypto_kazsign_KazSignNative_nativeLoadP12(JNIEnv *env, jclass clazz,
                                                    jint level, jbyteArray p12Data,
                                                    jstring password) {
    (void)clazz;

    const kaz_sign_level_params_t *params = kaz_sign_get_level_params(int_to_level(level));
    if (!params) {
        throw_exception(env, "java/lang/IllegalArgumentException", "Invalid security level");
        return NULL;
    }

    jsize p12Len = (*env)->GetArrayLength(env, p12Data);
    jbyte *p12Bytes = (*env)->GetByteArrayElements(env, p12Data, NULL);
    const char *passwordStr = (*env)->GetStringUTFChars(env, password, NULL);

    if (!p12Bytes || !passwordStr) {
        if (p12Bytes) (*env)->ReleaseByteArrayElements(env, p12Data, p12Bytes, JNI_ABORT);
        if (passwordStr) (*env)->ReleaseStringUTFChars(env, password, passwordStr);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to get array elements");
        return NULL;
    }

    // Allocate output buffers
    unsigned char *sk = malloc(params->secret_key_bytes);
    unsigned char *pk = malloc(params->public_key_bytes);
    unsigned long long certLen = 8192;
    unsigned char *cert = malloc(certLen);

    if (!sk || !pk || !cert) {
        if (sk) kaz_secure_zero(sk, params->secret_key_bytes);
        free(sk);
        free(pk);
        free(cert);
        (*env)->ReleaseByteArrayElements(env, p12Data, p12Bytes, JNI_ABORT);
        (*env)->ReleaseStringUTFChars(env, password, passwordStr);
        throw_kazsign_exception(env, KAZ_SIGN_ERROR_MEMORY);
        return NULL;
    }

    int result = kaz_sign_load_p12(int_to_level(level),
                                    (unsigned char *)p12Bytes, p12Len,
                                    passwordStr, sk, pk, cert, &certLen);

    (*env)->ReleaseByteArrayElements(env, p12Data, p12Bytes, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, password, passwordStr);

    if (result != KAZ_SIGN_SUCCESS) {
        kaz_secure_zero(sk, params->secret_key_bytes);
        free(sk);
        free(pk);
        free(cert);
        throw_kazsign_exception(env, result);
        return NULL;
    }

    // Create byte arrays
    jbyteArray skArray = (*env)->NewByteArray(env, (jsize)params->secret_key_bytes);
    if (!skArray) {
        kaz_secure_zero(sk, params->secret_key_bytes);
        free(sk);
        free(pk);
        free(cert);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate byte arrays");
        return NULL;
    }

    jbyteArray pkArray = (*env)->NewByteArray(env, (jsize)params->public_key_bytes);
    if (!pkArray) {
        (*env)->DeleteLocalRef(env, skArray);
        kaz_secure_zero(sk, params->secret_key_bytes);
        free(sk);
        free(pk);
        free(cert);
        throw_exception(env, "java/lang/OutOfMemoryError", "Failed to allocate byte arrays");
        return NULL;
    }

    jbyteArray certArray = NULL;

    (*env)->SetByteArrayRegion(env, skArray, 0, (jsize)params->secret_key_bytes, (jbyte *)sk);
    (*env)->SetByteArrayRegion(env, pkArray, 0, (jsize)params->public_key_bytes, (jbyte *)pk);

    if (certLen > 0) {
        certArray = (*env)->NewByteArray(env, (jsize)certLen);
        if (certArray) {
            (*env)->SetByteArrayRegion(env, certArray, 0, (jsize)certLen, (jbyte *)cert);
        }
    }

    kaz_secure_zero(sk, params->secret_key_bytes);
    free(sk);
    free(pk);
    free(cert);

    // Create P12Contents object
    jclass p12Class = (*env)->FindClass(env, "my/ssdid/wallet/domain/crypto/kazsign/P12Contents");
    if (!p12Class) {
        return NULL;
    }

    jmethodID constructor = (*env)->GetMethodID(env, p12Class, "<init>", "([B[B[BI)V");
    if (!constructor) {
        return NULL;
    }

    jobject p12Contents = (*env)->NewObject(env, p12Class, constructor,
                                            skArray, pkArray, certArray, level);

    return p12Contents;
}

/* ============================================================================
 * JNI OnLoad
 * ============================================================================ */

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)vm;
    (void)reserved;
    LOGI("KAZ-SIGN JNI library loaded (version %s)", kaz_sign_version());
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
    (void)vm;
    (void)reserved;
    kaz_sign_clear_all();
    LOGI("KAZ-SIGN JNI library unloaded");
}
