import AppIntents

// The system.open schema needs iOS 27; earlier systems open the app without routing to the result.
@available(iOS 27.0, *)
@AppIntent(schema: .system.open)
struct OpenCourseIntent: OpenIntent {
    static let title: LocalizedStringResource = "開啟課程"
    static let description = IntentDescription("在選課中顯示這門課的課程資訊。")

    @Parameter(title: "課程")
    var target: CourseEntity

    @Dependency private var router: AppRouter

    @MainActor
    func perform() async throws -> some IntentResult {
        if let identity = target.identity {
            router.open(.course(identity))
        }
        return .result()
    }
}

@available(iOS 27.0, *)
@AppIntent(schema: .system.open)
struct OpenPortalShortcutIntent: OpenIntent {
    static let title: LocalizedStringResource = "開啟 Portal 服務"
    static let description = IntentDescription("在 Portal 中開啟這項服務。")

    @Parameter(title: "服務")
    var target: PortalShortcutEntity

    @Dependency private var router: AppRouter

    @MainActor
    func perform() async throws -> some IntentResult {
        router.open(.portal(target.destination))
        return .result()
    }
}
