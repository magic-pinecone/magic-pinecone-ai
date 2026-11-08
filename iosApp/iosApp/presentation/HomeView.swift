import Shared
import SwiftUI

struct HomeView<CourseDestination: View>: View {
    let loadCourses: () async throws -> EeclassCourses
    @ViewBuilder let courseDestination: (EeclassCourseLink) -> CourseDestination
    let onOpenSettings: () -> Void

    @State private var courses: EeclassCourses?
    @State private var reloadID = 0

    var body: some View {
        content
            .navigationTitle("首頁")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("設定", systemImage: "gear", action: onOpenSettings)
                        .labelStyle(.iconOnly)
                }
            }
            // Value-based links only: mixing them with destination-based links in one stack re-pushes screens.
            .navigationDestination(for: EeclassCourseLink.self) { courseDestination($0) }
            .task(id: reloadID) { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if let courses {
            switch courses.status {
            case .loaded where courses.courses.isEmpty:
                ContentUnavailableView("沒有 ee-class 課程", systemImage: "books.vertical")
            case .loaded:
                List {
                    Section("ee-class 課程") {
                        ForEach(courses.courses, id: \.number) { course in
                            NavigationLink(
                                course.name,
                                value: EeclassCourseLink(number: course.number, name: course.name)
                            )
                        }
                    }
                }
                .refreshable { await load() }
            case .needsPortal:
                EeclassUnavailableView(
                    title: "需要登入 Portal",
                    systemImage: "person.crop.circle.badge.exclamationmark",
                    description: "在 Portal 分頁登入後再重新整理。"
                ) { reloadID += 1 }
            case .unavailable:
                EeclassUnavailableView(
                    title: "無法載入 ee-class 課程",
                    systemImage: "wifi.exclamationmark",
                    description: "請稍後再試。"
                ) { reloadID += 1 }
            }
        } else {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func load() async {
        guard let result = try? await loadCourses() else { return }
        courses = result
    }
}
