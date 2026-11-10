import Shared
import SwiftUI

struct ContentView: View {
    let sharedHost: IosSharedHost
    @Environment(AppRouter.self) private var router
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var themeMode = AppThemeMode.system
    @State private var appLanguageTag = ""
    @State private var isShowingSettings = false

    var body: some View {
        @Bindable var router = router
        TabView(selection: $router.selectedTab) {
            Tab(MR.strings().shell_tab_home.localized, systemImage: "house", value: AppTab.home) {
                NavigationStack {
                    HomeView(
                        loadCourses: { try await sharedHost.loadEeclassCourses() },
                        courseDestination: { course in
                            EeclassCourseView(sharedHost: sharedHost, course: course)
                        },
                        onOpenSettings: openSettings
                    )
                }
                .ignoresSafeArea(.container, edges: bottomSafeAreaEdges)
            }
            Tab(
                MR.strings().shell_tab_course_selection.localized,
                systemImage: "calendar",
                value: AppTab.coursePlanning
            ) {
                NavigationStack {
                    CoursePlanningView(
                        sharedHost: sharedHost,
                        onOpenSettings: openSettings
                    )
                }
                .ignoresSafeArea(.container, edges: bottomSafeAreaEdges)
            }
            Tab(MR.strings().shell_tab_portal.localized, systemImage: "building.columns", value: AppTab.portal) {
                NavigationStack {
                    PortalView(
                        sharedHost: sharedHost,
                        onOpenSettings: openSettings
                    )
                }
                .ignoresSafeArea(.container, edges: bottomSafeAreaEdges)
            }
            Tab(MR.strings().shell_tab_international.localized, systemImage: "globe", value: AppTab.international) {
                NavigationStack {
                    InternationalComposeView(sharedHost: sharedHost)
                        .ignoresSafeArea(.container, edges: .bottom)
                        .navigationTitle(MR.strings().shell_tab_international.localized)
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                Button(
                                    MR.strings().shell_settings.localized,
                                    systemImage: "gearshape",
                                    action: openSettings
                                )
                            }
                        }
                }
            }
        }
        .id(appLanguageTag)
        .sheet(isPresented: $isShowingSettings) {
            NavigationStack {
                SettingsView(
                    sharedHost: sharedHost,
                    appLanguageTag: appLanguageTag
                )
            }
            .preferredColorScheme(preferredColorScheme)
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
        .minimizeTabBarOnScrolling()
        .ignoresSafeArea(.container, edges: bottomSafeAreaEdges)
        .preferredColorScheme(preferredColorScheme)
        .task(id: ObjectIdentifier(sharedHost)) {
            for await selectedMode in sharedHost.themeMode {
                themeMode = selectedMode
            }
        }
        .task(id: ObjectIdentifier(sharedHost)) {
            for await selectedLanguage in sharedHost.appLanguage {
                appLanguageTag = selectedLanguage.tag
            }
        }
    }

    // Floating tab bars overlay content on iOS 26 and later.
    private var bottomSafeAreaEdges: Edge.Set {
        if #available(iOS 26, *), horizontalSizeClass == .compact {
            return .bottom
        }
        return []
    }

    private var preferredColorScheme: ColorScheme? {
        switch themeMode {
        case .system:
            return nil
        case .light:
            return .light
        case .dark:
            return .dark
        }
    }

    private func openSettings() {
        isShowingSettings = true
    }
}

/// The graduation language requirements and government guides, drawn by the shared Compose screen.
private struct InternationalComposeView: UIViewControllerRepresentable {
    let sharedHost: IosSharedHost

    func makeUIViewController(context: Context) -> some UIViewController {
        sharedHost.internationalScreenController()
    }

    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
    }
}

extension View {
    @ViewBuilder
    fileprivate func minimizeTabBarOnScrolling() -> some View {
        if #available(iOS 26, *) {
            tabBarMinimizeBehavior(.onScrollDown)
        } else {
            self
        }
    }

}
