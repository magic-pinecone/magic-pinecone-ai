import AppIntents
import Shared
import SwiftUI

@main
struct IOSApp: App {
    private let sharedHost: IosSharedHost
    @State private var router: AppRouter

    init() {
        let sharedHost = IosSharedHostKt.createIosSharedHost(htmlParser: SwiftSoupHtmlParser())
        let router = AppRouter()
        self.sharedHost = sharedHost
        _router = State(initialValue: router)
        // Intents can run before any scene exists, so they read these through @Dependency.
        AppDependencyManager.shared.add(dependency: sharedHost)
        AppDependencyManager.shared.add(dependency: router)
    }

    var body: some Scene {
        WindowGroup {
            ContentView(sharedHost: sharedHost)
                .environment(router)
                .task {
                    await Task.detached(priority: .utility) { [sharedHost] in
                        await SpotlightIndexer.indexAll(sharedHost: sharedHost)
                    }.value
                }
        }
    }
}
