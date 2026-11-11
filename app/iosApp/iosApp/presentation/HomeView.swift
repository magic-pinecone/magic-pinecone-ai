import Shared
import SwiftUI

struct HomeView<CourseDestination: View, PageDestination: View>: View {
    let loadCourses: () async throws -> EeclassCourses
    @ViewBuilder let courseDestination: (EeclassCourseLink) -> CourseDestination
    @ViewBuilder let internationalDestination: (InternationalPage) -> PageDestination
    let onOpenSettings: () -> Void

    @State private var courses: EeclassCourses?
    @State private var loadFailed = false
    @State private var reloadID = 0

    var body: some View {
        List {
            Section(MR.strings().eeclass_courses_header.localized) {
                eeclassRows
            }
            Section(MR.strings().intl_home_header.localized) {
                ForEach(InternationalPage.allCases, id: \.self) { page in
                    NavigationLink(value: page) {
                        Label(page.title.localized, systemImage: page.systemImage)
                    }
                }
            }
        }
        .refreshable { await load() }
        .navigationTitle(MR.strings().shell_tab_home.localized)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(MR.strings().shell_settings.localized, systemImage: "gear", action: onOpenSettings)
                    .labelStyle(.iconOnly)
            }
        }
        // Value-based links only: mixing them with destination-based links in one stack re-pushes screens.
        .navigationDestination(for: EeclassCourseLink.self) { courseDestination($0) }
        .navigationDestination(for: InternationalPage.self) { internationalDestination($0) }
        .task(id: reloadID) { await load() }
    }

    @ViewBuilder
    private var eeclassRows: some View {
        if let courses {
            switch courses.status {
            case .loaded where courses.courses.isEmpty:
                Text(MR.strings().eeclass_courses_empty.localized)
                    .foregroundStyle(.secondary)
            case .loaded:
                ForEach(courses.courses, id: \.number) { course in
                    NavigationLink(
                        course.name,
                        value: EeclassCourseLink(number: course.number, name: course.name)
                    )
                }
            case .needsPortal:
                EeclassStatusRow(
                    title: MR.strings().eeclass_needs_portal_title.localized,
                    systemImage: "person.crop.circle.badge.exclamationmark",
                    description: MR.strings().eeclass_needs_portal_description.localized
                ) { reloadID += 1 }
            case .unavailable:
                unavailableRow
            }
        } else if loadFailed {
            unavailableRow
        } else {
            ProgressView()
                .frame(maxWidth: .infinity)
        }
    }

    private var unavailableRow: some View {
        EeclassStatusRow(
            title: MR.strings().eeclass_courses_unavailable.localized,
            systemImage: "wifi.exclamationmark",
            description: MR.strings().eeclass_try_again_later.localized
        ) { reloadID += 1 }
    }

    private func load() async {
        do {
            courses = try await loadCourses()
            loadFailed = false
        } catch {
            loadFailed = courses == nil
        }
    }
}

/// Why the ee-class courses are missing, as one row so the rest of the home screen stays usable.
private struct EeclassStatusRow: View {
    let title: String
    let systemImage: String
    let description: String
    let onRetry: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: systemImage)
                .font(.title2)
                .foregroundStyle(.secondary)
                .frame(width: 30)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.headline)
                Text(description)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            Button(MR.strings().eeclass_refresh.localized, action: onRetry)
                .buttonStyle(.bordered)
                .buttonBorderShape(.capsule)
        }
        .padding(.vertical, 4)
    }
}
