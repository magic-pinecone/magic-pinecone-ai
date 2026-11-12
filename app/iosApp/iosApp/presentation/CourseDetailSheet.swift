import Shared
import SwiftUI

struct CourseDetailSelection: Identifiable {
    let semester: String
    let course: CourseSummary

    var id: String { "\(semester)/\(course.serialNumber)" }
}

struct CourseDetailSheet: View {
    let selection: CourseDetailSelection
    let sharedHost: IosSharedHost
    let planBridge: CoursePlanBridge
    let isSelected: Bool
    let canEditPlan: Bool

    @Environment(\.dismiss) private var dismiss
    @State private var detail: CourseDetail?
    @State private var isLoading = true
    @State private var loadFailed = false
    @State private var retryID = 0
    @State private var translation = TextTranslation()

    /// The course's own Chinese text, which the reader can translate once the syllabus loads.
    private var translatableTexts: [String] {
        guard let detail else { return [] }
        return [selection.course.title, detail.objectives, detail.content, detail.books, detail.teachingMethod,
                detail.gradingPolicy] + detail.distributionConditions.map(\.rule)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    CourseDetailHeader(
                        title: translation.text(selection.course.title),
                        classNumber: selection.course.classNo,
                        isSelected: isSelected
                    )
                } footer: {
                    TranslationNote(translation: translation)
                }

                CourseDetailFacts(course: selection.course)

                if isLoading {
                    Section {
                        HStack(spacing: 12) {
                            ProgressView()
                            Text(MR.strings().coursedetail_loading_syllabus.localized)
                                .foregroundStyle(.secondary)
                        }
                    }
                } else if loadFailed {
                    Section {
                        Label(
                            MR.strings().coursedetail_detail_load_failed.localized,
                            systemImage: "exclamationmark.circle"
                        )
                        Text(MR.strings().coursedetail_load_failed_hint.localized)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Button(MR.strings().planning_search_reload.localized, systemImage: "arrow.clockwise") {
                            retryID += 1
                        }
                    }
                } else if let detail {
                    CourseDetailDescription(detail: detail, translation: translation)
                }

                if let url = URL(string: selection.course.detailUrl),
                   ["https", "http"].contains(url.scheme?.lowercased() ?? "") {
                    Section {
                        // App Transport Security blocks plain http in the in-app browser.
                        if url.scheme?.lowercased() == "https" {
                            NavigationLink {
                                WebPageView(
                                    sharedHost: sharedHost,
                                    title: MR.strings().shell_official_course_page.localized,
                                    url: url.absoluteString
                                )
                            } label: {
                                Label(MR.strings().coursedetail_official_page.localized, systemImage: "globe")
                            }
                        } else {
                            Link(destination: url) {
                                Label(
                                    MR.strings().coursedetail_official_page.localized,
                                    systemImage: "arrow.up.right.square"
                                )
                            }
                        }
                    }
                }
            }
            .onDeviceTranslation(translation, texts: translatableTexts)
            .courseEntityContext(
                CourseIdentity(semester: selection.semester, serialNumber: selection.course.serialNumber)
            )
            .navigationTitle(MR.strings().coursedetail_details_title.localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(MR.strings().common_close.localized, systemImage: "xmark") { dismiss() }
                        .labelStyle(.iconOnly)
                        .accessibilityLabel(MR.strings().coursedetail_close_details.localized)
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                CourseDetailSelectionAction(isSelected: isSelected) {
                    planBridge.requestToggleCourse(course: selection.course)
                }
                .disabled(!canEditPlan)
            }
        }
        .task(id: retryID) {
            isLoading = true
            loadFailed = false
            do {
                let result = try await sharedHost.loadCourseDetail(
                    semester: selection.semester,
                    serialNumber: selection.course.serialNumber
                )
                guard !Task.isCancelled else { return }
                detail = result
            } catch {
                guard !Task.isCancelled else { return }
                loadFailed = true
            }
            isLoading = false
        }
    }
}

private struct CourseDetailHeader: View {
    let title: String
    let classNumber: String
    let isSelected: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.title2.weight(.bold))
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Text(classNumber)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            if isSelected {
                Label(MR.strings().planning_course_in_timetable.localized, systemImage: "checkmark.circle.fill")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.tint)
            }
        }
        .padding(.vertical, 4)
    }
}

private struct CourseDetailFacts: View {
    let course: CourseSummary

    var body: some View {
        Section(MR.strings().coursedetail_basic_info.localized) {
            LabeledContent(MR.strings().coursedetail_course_number.localized, value: course.serialNumber)
            LabeledContent(MR.strings().coursedetail_credits.localized) {
                Text(course.credit, format: .number.precision(.fractionLength(0...1)))
            }
            LabeledContent(MR.strings().coursedetail_course_type.localized, value: course.courseType.localizedLabel)
            LabeledContent(
                MR.strings().coursedetail_instructors.localized,
                value: course.teachers
                    .filter { $0.nilIfBlank != nil }
                    .joined(separator: MR.strings().planning_list_separator.localized)
                    .nilIfBlank ?? MR.strings().coursedetail_not_provided.localized
            )
            LabeledContent(
                MR.strings().coursedetail_department.localized,
                value: [course.collegeName, course.departmentName]
                    .filter { $0.nilIfBlank != nil }
                    .joined(separator: "／")
            )
        }
        Section(MR.strings().coursedetail_enrollment_info.localized) {
            LabeledContent(MR.strings().coursedetail_schedule.localized, value: course.scheduleDescription)
            LabeledContent(
                MR.strings().coursedetail_enrolled_quota.localized,
                value: MR.strings().planning_course_enrollment.localized(course.admitCnt, course.limitCnt)
            )
            if course.waitCnt > 0 {
                LabeledContent(MR.strings().coursedetail_waitlist.localized, value: "\(course.waitCnt)")
            }
            LabeledContent(MR.strings().coursedetail_password.localized, value: course.passwordCard.localizedLabel())
        }
    }
}

private struct CourseDetailDescription: View {
    let detail: CourseDetail
    let translation: TextTranslation

    var body: some View {
        CourseDetailTextSection(
            title: MR.strings().coursedetail_objectives.localized,
            text: translation.text(detail.objectives)
        )
        CourseDetailTextSection(
            title: MR.strings().coursedetail_content.localized,
            text: translation.text(detail.content)
        )
        CourseDetailTextSection(title: MR.strings().coursedetail_books.localized, text: translation.text(detail.books))
        CourseDetailTextSection(
            title: MR.strings().coursedetail_teaching_method.localized,
            text: translation.text(detail.teachingMethod)
        )
        CourseDetailTextSection(
            title: MR.strings().coursedetail_grading_policy.localized,
            text: translation.text(detail.gradingPolicy)
        )
        CourseDetailDistributionSection(conditions: detail.distributionConditions, translation: translation)
        if [detail.objectives, detail.content, detail.books, detail.teachingMethod, detail.gradingPolicy]
            .allSatisfy({ $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty })
            && detail.distributionConditions.isEmpty {
            Section {
                Text(MR.strings().coursedetail_empty_syllabus.localized)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct CourseDetailDistributionSection: View {
    let conditions: [DistributionCondition]
    let translation: TextTranslation

    var body: some View {
        Section(MR.strings().coursedetail_distribution_conditions.localized) {
            if conditions.isEmpty {
                Text(MR.strings().coursedetail_none.localized)
                    .foregroundStyle(.secondary)
            }
            ForEach(Array(conditions.sorted { $0.priority < $1.priority }.enumerated()), id: \.offset) { entry in
                HStack(alignment: .top, spacing: 12) {
                    Text("\(entry.offset + 1).")
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                    Text(translation.text(entry.element.rule))
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}

private struct CourseDetailTextSection: View {
    let title: String
    let text: String

    var body: some View {
        if let text = text.nilIfBlank {
            Section {
                Text(text)
                    .textSelection(.enabled)
                    .fixedSize(horizontal: false, vertical: true)
            } header: {
                Text(title)
            }
        }
    }
}

private struct CourseDetailSelectionAction: View {
    let isSelected: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(role: isSelected ? .destructive : nil, action: onToggle) {
            Label(
                isSelected ? MR.strings().coursedetail_remove.localized : MR.strings().coursedetail_add.localized,
                systemImage: isSelected ? "minus.circle" : "plus.circle.fill"
            )
            .font(.headline)
            .frame(maxWidth: .infinity, minHeight: 36)
        }
        .buttonStyle(CourseSelectionButtonStyle(isSelected: isSelected))
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(.regularMaterial)
    }
}

private extension String {
    var nilIfBlank: String? {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self
    }
}

extension CourseSummary {
    var scheduleDescription: String {
        let times = classTimes.filter { $0.day != .unknown }
        let days = Dictionary(grouping: times, by: { $0.day.order })
        return days.keys.sorted().flatMap { order -> [String] in
            guard let entries = days[order], let day = entries.first?.day else { return [] }
            let periods = Dictionary(grouping: entries.map(\.period), by: { $0.order })
                .sorted { $0.key < $1.key }
                .compactMap { $0.value.first?.description_ }
            return periods.map { "\(day.code)-\($0)" }
        }
        .joined(separator: MR.strings().planning_list_separator.localized)
        .nilIfBlank ?? MR.strings().coursedetail_no_schedule.localized
    }
}
