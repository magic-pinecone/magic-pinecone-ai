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

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    CourseDetailHeader(
                        title: selection.course.title,
                        classNumber: selection.course.classNo,
                        isSelected: isSelected
                    )
                }

                CourseDetailFacts(course: selection.course)

                if isLoading {
                    Section {
                        HStack(spacing: 12) {
                            ProgressView()
                            Text("正在載入課程說明…")
                                .foregroundStyle(.secondary)
                        }
                    }
                } else if loadFailed {
                    Section {
                        Label("無法載入完整課程資訊", systemImage: "exclamationmark.circle")
                        Text("仍可查看基本資訊與加入課表，或稍後重試。")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Button("重新載入", systemImage: "arrow.clockwise") {
                            retryID += 1
                        }
                    }
                } else if let detail {
                    CourseDetailDescription(detail: detail)
                }

                if let url = URL(string: selection.course.detailUrl),
                   ["https", "http"].contains(url.scheme?.lowercased() ?? "") {
                    Section {
                        // App Transport Security blocks plain http in the in-app browser.
                        if url.scheme?.lowercased() == "https" {
                            NavigationLink {
                                WebPageView(sharedHost: sharedHost, title: "官方課程頁面", url: url.absoluteString)
                            } label: {
                                Label("查看官方課程頁面", systemImage: "globe")
                            }
                        } else {
                            Link(destination: url) {
                                Label("查看官方課程頁面", systemImage: "arrow.up.right.square")
                            }
                        }
                    }
                }
            }
            .courseEntityContext(
                CourseIdentity(semester: selection.semester, serialNumber: selection.course.serialNumber)
            )
            .navigationTitle("課程資訊")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("關閉", systemImage: "xmark") { dismiss() }
                        .labelStyle(.iconOnly)
                        .accessibilityLabel("關閉課程資訊")
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
                Label("已加入課表", systemImage: "checkmark.circle.fill")
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
        Section("基本資訊") {
            LabeledContent("課號", value: course.serialNumber)
            LabeledContent("學分") {
                Text(course.credit, format: .number.precision(.fractionLength(0...1)))
            }
            LabeledContent("課程類別", value: course.courseType.description_)
            LabeledContent(
                "授課教師",
                value: course.teachers
                    .filter { $0.nilIfBlank != nil }
                    .joined(separator: "、")
                    .nilIfBlank ?? "未提供"
            )
            LabeledContent(
                "開課單位",
                value: [course.collegeName, course.departmentName]
                    .filter { $0.nilIfBlank != nil }
                    .joined(separator: "／")
            )
        }
        Section("選課資訊") {
            LabeledContent("上課時間", value: course.scheduleDescription)
            LabeledContent("已選／名額", value: "\(course.admitCnt)／\(course.limitCnt)")
            if course.waitCnt > 0 {
                LabeledContent("候補人數", value: "\(course.waitCnt)")
            }
            LabeledContent("加選密碼卡", value: course.passwordCard.description_)
        }
    }
}

private struct CourseDetailDescription: View {
    let detail: CourseDetail

    var body: some View {
        CourseDetailTextSection(title: "課程目標", text: detail.objectives)
        CourseDetailTextSection(title: "課程內容", text: detail.content)
        CourseDetailTextSection(title: "教科書", text: detail.books)
        CourseDetailTextSection(title: "教學方式", text: detail.teachingMethod)
        CourseDetailTextSection(title: "評量方式", text: detail.gradingPolicy)
        CourseDetailDistributionSection(conditions: detail.distributionConditions)
        if [detail.objectives, detail.content, detail.books, detail.teachingMethod, detail.gradingPolicy]
            .allSatisfy({ $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty })
            && detail.distributionConditions.isEmpty {
            Section {
                Text("尚未提供課程說明")
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct CourseDetailDistributionSection: View {
    let conditions: [DistributionCondition]

    var body: some View {
        Section("分發條件") {
            if conditions.isEmpty {
                Text("無")
                    .foregroundStyle(.secondary)
            }
            ForEach(Array(conditions.sorted { $0.priority < $1.priority }.enumerated()), id: \.offset) { entry in
                HStack(alignment: .top, spacing: 12) {
                    Text("\(entry.offset + 1).")
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                    Text(entry.element.rule)
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
                isSelected ? "從課表移除" : "加入課表",
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
        }.joined(separator: "、").nilIfBlank ?? "未提供上課時間"
    }
}
