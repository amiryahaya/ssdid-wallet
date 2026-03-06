cmake_minimum_required(VERSION 3.22.1)
project(kazsign)

# OpenSSL - prebuilt for Android
# Users must provide prebuilt OpenSSL at one of these locations:
# 1. android/app/src/main/cpp/libs/openssl/${ANDROID_ABI}/
# 2. Environment variable OPENSSL_ANDROID_HOME/${ANDROID_ABI}/

# Search for OpenSSL
set(OPENSSL_FOUND FALSE)
foreach(SEARCH_PATH
    "${CMAKE_CURRENT_SOURCE_DIR}/libs/openssl/${ANDROID_ABI}"
    "$ENV{OPENSSL_ANDROID_HOME}/${ANDROID_ABI}"
)
    if(EXISTS "${SEARCH_PATH}/lib/libcrypto.a")
        set(OPENSSL_ROOT "${SEARCH_PATH}")
        set(OPENSSL_FOUND TRUE)
        message(STATUS "Found OpenSSL at: ${OPENSSL_ROOT}")
        break()
    endif()
endforeach()

if(NOT OPENSSL_FOUND)
    message(WARNING "OpenSSL not found. Build will fail at link time. Provide prebuilt OpenSSL for Android.")
    set(OPENSSL_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/libs/openssl/${ANDROID_ABI}")
endif()

# KAZ-Sign library
add_library(kazsign SHARED
    kazsign/src/internal/sign.c
    kazsign/src/internal/nist_wrapper.c
    kazsign/src/internal/security.c
    kazsign/src/internal/kdf.c
    kazsign/src/internal/sha3.c
    kazsign/src/internal/detached.c
    kazsign/src/internal/der.c
    kazsign/src/internal/x509.c
    kazsign/src/internal/p12.c
    kazsign_jni.c
)

target_include_directories(kazsign PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/kazsign/include
    ${CMAKE_CURRENT_SOURCE_DIR}/kazsign/src/internal
    ${OPENSSL_ROOT}/include
)

target_compile_options(kazsign PRIVATE
    -Wall -Wextra -O2 -fPIC -fvisibility=hidden
    -std=c11
)

target_compile_definitions(kazsign PRIVATE
    KAZ_RUNTIME_LEVEL_SUPPORT=1
)

target_link_libraries(kazsign
    ${OPENSSL_ROOT}/lib/libcrypto.a
    log
    m
)
