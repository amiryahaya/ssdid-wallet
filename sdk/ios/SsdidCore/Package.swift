// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SsdidCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "SsdidCore", targets: ["SsdidCore"]),
    ],
    targets: [
        .target(
            name: "SsdidCore",
            path: "Sources/SsdidCore"
        ),
        .testTarget(
            name: "SsdidCoreTests",
            dependencies: ["SsdidCore"],
            path: "Tests/SsdidCoreTests"
        ),
    ]
)
