#!/bin/bash
#
# Build liboqs XCFramework for iOS and macOS (arm64 only)
# Only builds ML-DSA and SLH-DSA signature algorithms (no KEMs)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_DIR/build/liboqs"
OUTPUT_DIR="$PROJECT_DIR/Packages/LibOQS"

LIBOQS_VERSION="0.15.0"
LIBOQS_URL="https://github.com/open-quantum-safe/liboqs/archive/refs/tags/${LIBOQS_VERSION}.tar.gz"

IOS_MIN_VERSION="17.0"
MACOS_MIN_VERSION="11.0"

OQS_ALGOS="SIG_ml_dsa_44;SIG_ml_dsa_65;SIG_ml_dsa_87;SIG_slh_dsa_pure_sha2_128s;SIG_slh_dsa_pure_sha2_128f;SIG_slh_dsa_pure_sha2_192s;SIG_slh_dsa_pure_sha2_192f;SIG_slh_dsa_pure_sha2_256s;SIG_slh_dsa_pure_sha2_256f;SIG_slh_dsa_pure_shake_128s;SIG_slh_dsa_pure_shake_128f;SIG_slh_dsa_pure_shake_192s;SIG_slh_dsa_pure_shake_192f;SIG_slh_dsa_pure_shake_256s;SIG_slh_dsa_pure_shake_256f"

info() { echo "[INFO] $1"; }
step() { echo "[STEP] $1"; }
error() { echo "[ERROR] $1" >&2; exit 1; }

download_liboqs() {
    step "Downloading liboqs ${LIBOQS_VERSION}..."
    mkdir -p "$BUILD_DIR/downloads"
    cd "$BUILD_DIR/downloads"
    if [ ! -f "liboqs-${LIBOQS_VERSION}.tar.gz" ]; then
        curl -L -o "liboqs-${LIBOQS_VERSION}.tar.gz" "$LIBOQS_URL"
    fi
    if [ ! -d "liboqs-${LIBOQS_VERSION}" ]; then
        tar -xzf "liboqs-${LIBOQS_VERSION}.tar.gz"
    fi
}

build_liboqs_platform() {
    local PLATFORM=$1
    local SRC="$BUILD_DIR/downloads/liboqs-${LIBOQS_VERSION}"
    local INSTALL="$BUILD_DIR/install/$PLATFORM"

    if [ -f "$INSTALL/lib/liboqs.a" ]; then
        info "liboqs for $PLATFORM already built"
        return
    fi

    step "Building liboqs for $PLATFORM..."

    local BUILD_PLATFORM="$BUILD_DIR/build/$PLATFORM"
    rm -rf "$BUILD_PLATFORM"
    mkdir -p "$BUILD_PLATFORM" "$INSTALL"
    cd "$BUILD_PLATFORM"

    local CMAKE_ARGS=(
        -DCMAKE_INSTALL_PREFIX="$INSTALL"
        -DCMAKE_BUILD_TYPE=Release
        -DBUILD_SHARED_LIBS=OFF
        -DOQS_BUILD_ONLY_LIB=ON
        -DOQS_MINIMAL_BUILD="$OQS_ALGOS"
        -DOQS_USE_OPENSSL=OFF
        -DOQS_DIST_BUILD=ON
        -DOQS_PERMIT_UNSUPPORTED_ARCHITECTURE=ON
    )

    case $PLATFORM in
        macos-arm64)
            CMAKE_ARGS+=(
                -DCMAKE_OSX_ARCHITECTURES=arm64
                -DCMAKE_OSX_DEPLOYMENT_TARGET=$MACOS_MIN_VERSION
                -DCMAKE_SYSTEM_PROCESSOR=aarch64
            )
            ;;
        ios-arm64)
            CMAKE_ARGS+=(
                -DCMAKE_SYSTEM_NAME=iOS
                -DCMAKE_OSX_ARCHITECTURES=arm64
                -DCMAKE_OSX_DEPLOYMENT_TARGET=$IOS_MIN_VERSION
                -DCMAKE_SYSTEM_PROCESSOR=aarch64
            )
            ;;
        ios-simulator-arm64)
            CMAKE_ARGS+=(
                -DCMAKE_SYSTEM_NAME=iOS
                -DCMAKE_OSX_SYSROOT=iphonesimulator
                -DCMAKE_OSX_ARCHITECTURES=arm64
                -DCMAKE_OSX_DEPLOYMENT_TARGET=$IOS_MIN_VERSION
                -DCMAKE_SYSTEM_PROCESSOR=aarch64
            )
            ;;
    esac

    cmake "$SRC" "${CMAKE_ARGS[@]}" || error "CMake configure failed for $PLATFORM"
    cmake --build . --parallel "$(sysctl -n hw.ncpu)" || error "CMake build failed for $PLATFORM"
    cmake --install . || error "CMake install failed for $PLATFORM"

    if [ ! -f "$INSTALL/lib/liboqs.a" ]; then
        error "liboqs.a not found after build for $PLATFORM"
    fi

    info "liboqs for $PLATFORM built successfully"
}

create_xcframework() {
    step "Creating XCFramework..."

    local XCFRAMEWORK="$OUTPUT_DIR/LibOQSNative.xcframework"
    rm -rf "$XCFRAMEWORK"

    local COMBINED="$BUILD_DIR/combined"
    rm -rf "$COMBINED"
    mkdir -p "$COMBINED"/{macos,ios-device,ios-simulator}

    # macOS arm64
    cp "$BUILD_DIR/install/macos-arm64/lib/liboqs.a" "$COMBINED/macos/libLibOQSNative.a"

    # iOS device arm64
    cp "$BUILD_DIR/install/ios-arm64/lib/liboqs.a" "$COMBINED/ios-device/libLibOQSNative.a"

    # iOS simulator arm64
    cp "$BUILD_DIR/install/ios-simulator-arm64/lib/liboqs.a" "$COMBINED/ios-simulator/libLibOQSNative.a"

    # Copy all headers preserving the oqs/ prefix that internal includes expect
    mkdir -p "$COMBINED/include/oqs"
    cp "$BUILD_DIR/install/macos-arm64/include/oqs/"*.h "$COMBINED/include/oqs/"

    # Module map at oqs/ level — avoids collision with KazSignNative's root module.modulemap
    cat > "$COMBINED/include/oqs/module.modulemap" << 'EOF'
module LibOQSNative {
    header "oqs.h"
    export *
}
EOF

    xcodebuild -create-xcframework \
        -library "$COMBINED/macos/libLibOQSNative.a" \
        -headers "$COMBINED/include" \
        -library "$COMBINED/ios-device/libLibOQSNative.a" \
        -headers "$COMBINED/include" \
        -library "$COMBINED/ios-simulator/libLibOQSNative.a" \
        -headers "$COMBINED/include" \
        -output "$XCFRAMEWORK"

    info "Created $XCFRAMEWORK"
}

main() {
    echo "================================"
    echo "liboqs XCFramework Builder"
    echo "================================"

    mkdir -p "$BUILD_DIR"
    download_liboqs

    for PLATFORM in macos-arm64 ios-arm64 ios-simulator-arm64; do
        build_liboqs_platform "$PLATFORM"
    done

    create_xcframework

    info "Build complete!"
    echo "XCFramework: $OUTPUT_DIR/LibOQSNative.xcframework"
}

main "$@"
