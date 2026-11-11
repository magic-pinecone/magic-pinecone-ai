import Shared
import SwiftUI

struct SelectedCoursesSheet: View {
    let planBridge: CoursePlanBridge
    let courses: [CourseSummary]
    let canEditPlan: Bool
    @Environment(\.dismiss) private var dismiss

    private var totalCredits: Double {
        courses.reduce(0) { $0 + $1.credit }
    }

    var body: some View {
        NavigationStack {
            SelectedCoursesList(
                courses: courses,
                canEditPlan: canEditPlan,
                onRemoveCourse: { planBridge.requestToggleCourse(course: $0) }
            )
                .navigationTitle(MR.strings().planning_selected_title.localized)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .principal) {
                        VStack(spacing: 2) {
                            Text(MR.strings().planning_selected_title.localized)
                                .font(.headline)
                                .accessibilityAddTraits(.isHeader)
                            Text(
                                MR.plurals().planning_selected_summary
                                    .localized(courses.count, totalCredits.creditString)
                            )
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .accessibilityIdentifier("selected-courses-summary")
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(MR.strings().portal_done.localized) { dismiss() }
                            .accessibilityIdentifier("selected-courses-done")
                    }
                }
        }
    }
}

private struct SelectedCoursesList: View {
    let courses: [CourseSummary]
    let canEditPlan: Bool
    let onRemoveCourse: (CourseSummary) -> Void

    var body: some View {
        if courses.isEmpty {
            ContentUnavailableView(
                MR.strings().planning_selected_empty_title.localized,
                systemImage: "cart",
                description: Text(MR.strings().planning_selected_empty_hint.localized)
            )
        } else {
            List {
                ForEach(courses, id: \.serialNumber) { course in
                    SelectedCourseRow(course: course)
                        .courseEntityContext(
                            CourseIdentity(semester: CourseIdentity.currentSemester, serialNumber: course.serialNumber)
                        )
                        .accessibilityIdentifier("selected-course-\(course.serialNumber)")
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            if canEditPlan {
                                Button(role: .destructive) {
                                    onRemoveCourse(course)
                                } label: {
                                    Label(MR.strings().planning_course_remove.localized, systemImage: "trash")
                                }
                                .accessibilityIdentifier("selected-course-remove-\(course.serialNumber)")
                            }
                        }
                }
            }
            .listStyle(.insetGrouped)
        }
    }
}

private struct SelectedCourseRow: View {
    let course: CourseSummary

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(course.title)
                    .font(.body)
                Text(course.courseType.localizedLabel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if !course.teachers.isEmpty {
                    Text(course.teachers.joined(separator: MR.strings().planning_list_separator.localized))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Text(course.scheduleDescription)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text(MR.strings().planning_credits_value.localized(course.credit.creditString))
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize()
        }
        .padding(.vertical, 4)
    }
}
