// swift-tools-version:5.9

import PackageDescription

let package = Package(
    name: "KazSign",
    platforms: [
        .iOS(.v13),
        .macOS(.v11)
    ],
    products: [
        .library(
            name: "KazSign",
            targets: ["KazSign"]
        ),
    ],
    targets: [
        .target(
            name: "KazSign",
            dependencies: ["KazSignNative"],
            path: "Sources/KazSign"
        ),
        .binaryTarget(
            name: "KazSignNative",
            path: "KazSignNative.xcframework"
        ),
    ]
)
