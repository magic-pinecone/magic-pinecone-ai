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
            .navigationTitle(MR.strings().shell_tab_home.localized)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(MR.strings().shell_settings.localized, systemImage: "gear", action: onOpenSettings)
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
                ContentUnavailableView(MR.strings().eeclass_courses_empty.localized, systemImage: "books.vertical")
            case .loaded:
                List {
                    Section(MR.strings().eeclass_courses_header.localized) {
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
                    title: MR.strings().eeclass_needs_portal_title.localized,
                    systemImage: "person.crop.circle.badge.exclamationmark",
                    description: MR.strings().eeclass_needs_portal_description.localized
                ) { reloadID += 1 }
            case .unavailable:
                EeclassUnavailableView(
                    title: MR.strings().eeclass_courses_unavailable.localized,
                    systemImage: "wifi.exclamationmark",
                    description: MR.strings().eeclass_try_again_later.localized
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
