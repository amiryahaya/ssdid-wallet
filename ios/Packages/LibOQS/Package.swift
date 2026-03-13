// swift-tools-version:5.9

import PackageDescription

let package = Package(
    name: "LibOQS",
    platforms: [
        .iOS(.v17),
        .macOS(.v11)
    ],
    products: [
        .library(
            name: "LibOQS",
            targets: ["LibOQS"]
        ),
    ],
    targets: [
        .target(
            name: "LibOQS",
            dependencies: ["LibOQSNative"],
            path: "Sources/LibOQS"
        ),
        .binaryTarget(
            name: "LibOQSNative",
            path: "LibOQSNative.xcframework"
        ),
    ]
)
