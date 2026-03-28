// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "BasicIdentity",
    platforms: [.iOS(.v15), .macOS(.v13)],
    dependencies: [
        .package(path: "../../../ios/SsdidCore"),
    ],
    targets: [
        .executableTarget(
            name: "BasicIdentity",
            dependencies: [
                .product(name: "SsdidCore", package: "SsdidCore"),
            ],
            path: "Sources"
        ),
    ]
)
