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
                .navigationTitle("已選課程")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .principal) {
                        VStack(spacing: 2) {
                            Text("已選課程")
                                .font(.headline)
                                .accessibilityAddTraits(.isHeader)
                            Text(
                                """
                                \(courses.count) 門課 · \
                                \(totalCredits, format: .number.precision(.fractionLength(0...2))) 學分
                                """
                            )
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .accessibilityIdentifier("selected-courses-summary")
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("完成") { dismiss() }
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
                "還沒有已選課程",
                systemImage: "cart",
                description: Text("加入的課程會顯示於此。")
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
                                    Label("移除", systemImage: "trash")
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
                Text(course.courseType.description_)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if !course.teachers.isEmpty {
                    Text(course.teachers.joined(separator: "、"))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Text(course.scheduleDescription)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text("\(course.credit, format: .number.precision(.fractionLength(0...2))) 學分")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize()
        }
        .padding(.vertical, 4)
    }
}
