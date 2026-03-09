# CMakeLists.txt for KAZ-SIGN Android JNI Library
# Builds the native library with OpenSSL for Android

cmake_minimum_required(VERSION 3.22.1)
project("kazsign" C)

# Set C standard
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Paths to KAZ-SIGN source (embedded in cpp/kazsign/)
set(KAZSIGN_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/kazsign")
set(KAZSIGN_INCLUDE "${KAZSIGN_ROOT}/include")
set(KAZSIGN_SRC "${KAZSIGN_ROOT}/src/internal")

# OpenSSL for Android - search in multiple locations
set(OPENSSL_FOUND FALSE)

# Location 1: Project libs/openssl directory (built by script)
set(OPENSSL_LIBS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../libs/openssl/${ANDROID_ABI}")
# Location 2: Project jniLibs directory (pre-built)
set(OPENSSL_JNILIBS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
# Location 3: Build output directory
set(OPENSSL_BUILD_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../build/openssl/${ANDROID_ABI}")
# Location 4: Environment variable
set(OPENSSL_ENV_DIR "$ENV{OPENSSL_ANDROID_HOME}/${ANDROID_ABI}")

# Try each location
if(EXISTS "${OPENSSL_LIBS_DIR}/lib/libcrypto.a" OR EXISTS "${OPENSSL_LIBS_DIR}/lib/libcrypto.so")
    set(OPENSSL_ROOT_DIR "${OPENSSL_LIBS_DIR}")
    set(OPENSSL_FOUND TRUE)
    message(STATUS "Found OpenSSL in libs/openssl: ${OPENSSL_ROOT_DIR}")
elseif(EXISTS "${OPENSSL_JNILIBS_DIR}/libcrypto.a" OR EXISTS "${OPENSSL_JNILIBS_DIR}/libcrypto.so")
    set(OPENSSL_ROOT_DIR "${OPENSSL_JNILIBS_DIR}")
    set(OPENSSL_FOUND TRUE)
    message(STATUS "Found OpenSSL in jniLibs: ${OPENSSL_ROOT_DIR}")
elseif(EXISTS "${OPENSSL_BUILD_DIR}/lib/libcrypto.a" OR EXISTS "${OPENSSL_BUILD_DIR}/lib/libcrypto.so")
    set(OPENSSL_ROOT_DIR "${OPENSSL_BUILD_DIR}")
    set(OPENSSL_FOUND TRUE)
    message(STATUS "Found OpenSSL in build directory: ${OPENSSL_ROOT_DIR}")
elseif(EXISTS "${OPENSSL_ENV_DIR}/lib/libcrypto.a" OR EXISTS "${OPENSSL_ENV_DIR}/lib/libcrypto.so")
    set(OPENSSL_ROOT_DIR "${OPENSSL_ENV_DIR}")
    set(OPENSSL_FOUND TRUE)
    message(STATUS "Found OpenSSL from environment: ${OPENSSL_ROOT_DIR}")
endif()

if(NOT OPENSSL_FOUND)
    message(FATAL_ERROR "
================================================================================
OpenSSL for Android not found!

Please build OpenSSL for Android or provide pre-built libraries.

Option 1: Build OpenSSL using the provided script:
  cd bindings/android
  ./scripts/build-openssl-android.sh

Option 2: Set OPENSSL_ANDROID_HOME environment variable:
  export OPENSSL_ANDROID_HOME=/path/to/openssl-android

Option 3: Place pre-built libraries in:
  bindings/android/kazsign/src/main/jniLibs/${ANDROID_ABI}/libcrypto.a

For pre-built OpenSSL for Android, you can download from:
  https://github.com/aspect/aspect-build-openssl-android/releases
================================================================================
")
endif()

# Include directories
include_directories(
    ${KAZSIGN_INCLUDE}
    ${KAZSIGN_SRC}
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Add OpenSSL include path
if(EXISTS "${OPENSSL_ROOT_DIR}/include")
    include_directories(${OPENSSL_ROOT_DIR}/include)
elseif(EXISTS "${OPENSSL_ROOT_DIR}/../include")
    include_directories(${OPENSSL_ROOT_DIR}/../include)
endif()

# Source files
set(KAZSIGN_SOURCES
    ${KAZSIGN_SRC}/sign.c
    ${KAZSIGN_SRC}/nist_wrapper.c
    ${KAZSIGN_SRC}/security.c
    ${KAZSIGN_SRC}/kdf.c
    ${KAZSIGN_SRC}/sha3.c
    ${KAZSIGN_SRC}/detached.c
    ${KAZSIGN_SRC}/der.c
    ${KAZSIGN_SRC}/x509.c
    ${KAZSIGN_SRC}/p12.c
)

set(JNI_SOURCES
    kazsign_jni.c
)

# Compile definitions - support all security levels at runtime
add_definitions(-DKAZ_RUNTIME_LEVEL_SUPPORT=1)

# Build the shared library
add_library(kazsign SHARED
    ${KAZSIGN_SOURCES}
    ${JNI_SOURCES}
)

# Find OpenSSL crypto library
if(EXISTS "${OPENSSL_ROOT_DIR}/lib/libcrypto.a")
    set(CRYPTO_LIB "${OPENSSL_ROOT_DIR}/lib/libcrypto.a")
elseif(EXISTS "${OPENSSL_ROOT_DIR}/libcrypto.a")
    set(CRYPTO_LIB "${OPENSSL_ROOT_DIR}/libcrypto.a")
elseif(EXISTS "${OPENSSL_ROOT_DIR}/lib/libcrypto.so")
    set(CRYPTO_LIB "${OPENSSL_ROOT_DIR}/lib/libcrypto.so")
elseif(EXISTS "${OPENSSL_ROOT_DIR}/libcrypto.so")
    set(CRYPTO_LIB "${OPENSSL_ROOT_DIR}/libcrypto.so")
else()
    message(FATAL_ERROR "Could not find libcrypto in ${OPENSSL_ROOT_DIR}")
endif()

message(STATUS "Using OpenSSL crypto library: ${CRYPTO_LIB}")

# Link libraries
target_link_libraries(kazsign
    ${CRYPTO_LIB}
    log  # Android logging
    m    # Math library
)

# Compiler flags
target_compile_options(kazsign PRIVATE
    -Wall
    -Wextra
    -O2
    -fPIC
    -fvisibility=hidden
)

# Export only necessary symbols
set_target_properties(kazsign PROPERTIES
    C_VISIBILITY_PRESET hidden
)
