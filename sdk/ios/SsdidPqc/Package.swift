// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "SsdidPqc",
    platforms: [.iOS(.v15), .macOS(.v13)],
    products: [
        .library(name: "SsdidPqc", targets: ["SsdidPqc"]),
    ],
    dependencies: [
        .package(path: "../SsdidCore"),
    ],
    targets: [
        .target(
            name: "SsdidPqc",
            dependencies: ["SsdidCore"],
            path: "Sources/SsdidPqc"
        ),
    ]
)
