#if DEBUG
import Shared
import SwiftUI
import UIKit

private enum Tab: Hashable {
    case home, coursePlanning, portal
}

private enum CoursePlanningSection: Hashable {
    case catalog, timetable
}

#Preview("Home tab") {
    TabPreviewShell(selectedTab: .home)
}

#Preview("Course Planning tab") {
    TabPreviewShell(selectedTab: .coursePlanning)
}

#Preview("Course Planning tab · timetable") {
    TabPreviewShell(selectedTab: .coursePlanning, courseSection: .timetable)
}

#Preview("Portal tab") {
    TabPreviewShell(selectedTab: .portal)
}

private struct TabPreviewShell: View {
    let selectedTab: Tab
    let courseSection: CoursePlanningSection

    init(selectedTab: Tab, courseSection: CoursePlanningSection = .catalog) {
        self.selectedTab = selectedTab
        self.courseSection = courseSection
    }

    var body: some View {
        TabView(selection: .constant(selectedTab)) {
            SwiftUI.Tab("首頁", systemImage: "house", value: .home) {
                NavigationStack {
                    HomeView(
                        loadCourses: { throw CancellationError() },
                        courseDestination: { _ in EmptyView() },
                        onOpenSettings: {}
                    )
                }
            }
            SwiftUI.Tab("選課", systemImage: "calendar", value: .coursePlanning) {
                NavigationStack { CoursePlanningTabPreview(initialSection: courseSection) }
            }
            SwiftUI.Tab("Portal", systemImage: "building.columns", value: .portal) {
                NavigationStack {
                    ComposeFixtureController(scenario: .portal)
                        .ignoresSafeArea(.container, edges: .bottom)
                        .navigationTitle("Portal")
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                Button("設定", systemImage: "gear") {}
                                    .labelStyle(.iconOnly)
                            }
                        }
                }
            }
        }
    }
}

private struct CoursePlanningTabPreview: View {
    @State private var activeSection: CoursePlanningSection
    @State private var query: String
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    init(initialSection: CoursePlanningSection) {
        _activeSection = State(initialValue: initialSection)
        _query = State(initialValue: "")
    }

    var body: some View {
        GeometryReader { geometry in
            let isWide = horizontalSizeClass == .regular || geometry.size.width >= 600
            VStack(spacing: isWide ? 0 : 12) {
                if !isWide {
                    Picker("選課檢視", selection: $activeSection) {
                        Text("搜尋").tag(CoursePlanningSection.catalog)
                        Text("課表").tag(CoursePlanningSection.timetable)
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal)
                    .padding(.top, 8)
                }

                if isWide {
                    CoursePlanningPaneLayout(
                        isExpanded: true,
                        division: geometry.coursePlanningDivision
                    ) {
                        CatalogFixturePane(query: $query)
                        Color(uiColor: .separator)
                        ComposeFixtureController(scenario: .timetablePopulated)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                } else {
                    switch activeSection {
                    case .catalog:
                        CatalogFixturePane(query: $query).id(activeSection)
                    case .timetable:
                        ComposeFixtureController(scenario: .timetablePopulated)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .id(activeSection)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .navigationTitle(isWide ? "選課" : (activeSection == .catalog ? "課程搜尋" : "我的課表"))
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("儲存", systemImage: "square.and.arrow.down") {}
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("已選課程", systemImage: "cart") {}
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("設定", systemImage: "gear") {}
                        .labelStyle(.iconOnly)
                }
            }
        }
        .ignoresSafeArea(.container, edges: .bottom)
    }
}

private struct CatalogFixturePane: View {
    @Binding var query: String

    var body: some View {
        CourseCatalogStaticPreview()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
            .searchable(
                text: $query,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: "搜尋課程名稱"
            )
    }
}

private struct ComposeFixtureController: UIViewControllerRepresentable {
    let scenario: ComposePreviewScenario

    func makeUIViewController(context: Context) -> UIViewController {
        IosComposePreviewHost.shared.createController(scenario: scenario, isDarkTheme: false)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
#endif
