// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "course-translate",
    platforms: [.macOS("26.4")],
    targets: [
        .executableTarget(name: "course-translate")
    ]
)
