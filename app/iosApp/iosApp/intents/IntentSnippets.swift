@preconcurrency import Shared
import SwiftUI

struct CourseSnippetRow: Identifiable {
    let id: String
    let leading: String?
    let title: String
    let people: String
    let time: String
    let badges: [String]

    init(course: CourseSummary) {
        id = course.serialNumber
        leading = nil
        title = course.title
        people = Self.people(course)
        time = course.meetingsDescription
        badges = [
            CourseFilterLabelKt.label(CourseFilterCredits(credits: course.credit)),
            course.courseType == .unknown ? nil : course.courseType.description_,
            course.seatsBadge
        ].compactMap { $0 }
    }

    init(meeting: ClassMeeting) {
        id = "\(meeting.course.serialNumber)-\(meeting.startMinute)"
        leading = clockLabel(meeting.startMinute)
        title = meeting.course.title
        people = Self.people(meeting.course)
        time = "\(meeting.periodLabel()) \(meeting.timeRangeLabel())"
        badges = []
    }

    private static func people(_ course: CourseSummary) -> String {
        (course.teachers.filter { !$0.isEmpty }.prefix(2) + [course.classNo])
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
    }
}

private extension CourseSummary {
    var seatsBadge: String {
        let remaining = Int(limitCnt) - Int(admitCnt)
        if limitCnt == 0 { return "不限名額" }
        return remaining > 0 ? "尚餘 \(remaining) 名額" : "已額滿"
    }
}

/// Siri draws snippets on its own material, where a concrete label color stays legible in both appearances.
extension ShapeStyle where Self == Color {
    static var snippetSecondary: Color { Color(uiColor: .secondaryLabel) }
}

/// What a card answers, so it still reads on its own when Siri shows the card without the dialog.
struct SnippetHeadline: View {
    let title: String
    let detail: String
    let systemImage: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label(title, systemImage: systemImage)
                .font(.subheadline.weight(.medium))
                .lineLimit(2)
            Text(detail)
                .font(.footnote)
                .foregroundStyle(.snippetSecondary)
        }
    }
}

/// The card Siri and Shortcuts show under a schedule or calendar intent's spoken answer.
struct CourseSnippetList: View {
    let headline: SnippetHeadline
    let rows: [CourseSnippetRow]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            headline
            if !rows.isEmpty {
                Divider()
                CourseSnippetRows(rows: rows)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct CourseSnippetDetails {
    let title: String
    let subtitle: String
    let facts: [(label: String, value: String)]
    let meetings: [String]
    let sections: [(title: String, text: String)]
    let distributionRules: [String]?

    init(course: CourseSummary, detail: CourseDetail?) {
        title = course.title
        subtitle = ([course.classNo] + course.teachers.filter { !$0.isEmpty })
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
        var facts = [
            (label: "學分", value: course.credit.formatted()),
            (label: "修別", value: course.courseType.description_),
            (label: "開課", value: [course.collegeName, course.departmentName]
                .filter { !$0.isEmpty }
                .joined(separator: "／")),
            (label: "名額", value: "\(course.admitCnt)／\(course.limitCnt) 人"),
            (label: "密碼卡", value: course.passwordCard.description_)
        ]
        if course.waitCnt > 0 {
            facts.insert((label: "候補", value: "\(course.waitCnt) 人"), at: 4)
        }
        self.facts = facts
        meetings = course.meetings()
            .map { "星期\($0.day.description_) \($0.periodLabel()) \($0.timeRangeLabel())" }
        sections = detail.map { detail in
            [
                (title: "課程目標", text: detail.objectives),
                (title: "課程內容", text: detail.content),
                (title: "教科書", text: detail.books),
                (title: "教學方式", text: detail.teachingMethod),
                (title: "評量方式", text: detail.gradingPolicy)
            ]
            .map { section in
                // Catalog text often carries blank lines, which waste the card's few lines.
                let lines = section.text
                    .split(whereSeparator: \.isNewline)
                    .map { $0.trimmingCharacters(in: .whitespaces) }
                    .filter { !$0.isEmpty }
                return (title: section.title, text: lines.joined(separator: "\n"))
            }
            .filter { !$0.text.isEmpty }
        } ?? []
        distributionRules = detail?.rankedDistributionRules
    }
}

/// The card Siri and Shortcuts show for one course's details.
struct CourseDetailSnippet: View {
    let details: CourseSnippetDetails?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let details {
                VStack(alignment: .leading, spacing: 2) {
                    Text(details.title)
                        .font(.headline)
                    if !details.subtitle.isEmpty {
                        Text(details.subtitle)
                            .font(.subheadline)
                            .foregroundStyle(.snippetSecondary)
                    }
                }
                Grid(alignment: .leading, horizontalSpacing: 16, verticalSpacing: 6) {
                    ForEach(details.facts, id: \.label) { fact in
                        GridRow {
                            Text(fact.label)
                                .foregroundStyle(.snippetSecondary)
                            Text(fact.value)
                        }
                    }
                }
                .font(.subheadline)
                Divider()
                if details.meetings.isEmpty {
                    Label("未提供上課時間", systemImage: "calendar")
                        .font(.subheadline)
                        .foregroundStyle(.snippetSecondary)
                } else {
                    ForEach(details.meetings, id: \.self) { meeting in
                        Label(meeting, systemImage: "calendar")
                            .font(.subheadline)
                    }
                }
                CourseDetailSnippetDescription(details: details)
            } else {
                Label("找不到這門課", systemImage: "book.closed")
                    .foregroundStyle(.snippetSecondary)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The card for a Course Catalog search: what Siri searched for, then the best matches.
struct CourseSearchSnippet: View {
    let criteria: [String]
    let total: Int
    let rows: [CourseSnippetRow]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            SnippetHeadline(
                title: criteria.joined(separator: " · "),
                detail: total == 0 ? "沒有符合的課程" : "共 \(total) 門課程",
                systemImage: "magnifyingglass"
            )
            if !rows.isEmpty {
                Divider()
                CourseSnippetRows(rows: rows)
            }
            if total > rows.count {
                Text("還有 \(total - rows.count) 門，可在選課中查看。")
                    .font(.footnote)
                    .foregroundStyle(.snippetSecondary)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct CourseDetailSnippetDescription: View {
    let details: CourseSnippetDetails

    var body: some View {
        if let rules = details.distributionRules {
            ForEach(details.sections, id: \.title) { section in
                Divider()
                VStack(alignment: .leading, spacing: 4) {
                    Text(section.title)
                        .font(.subheadline.weight(.semibold))
                    // A Siri card cannot scroll; the full text stays on the returned course.
                    Text(section.text)
                        .font(.footnote)
                        .foregroundStyle(.snippetSecondary)
                        .lineLimit(4)
                }
            }
            Divider()
            VStack(alignment: .leading, spacing: 4) {
                Text("分發條件")
                    .font(.subheadline.weight(.semibold))
                if rules.isEmpty {
                    Text("無")
                        .font(.footnote)
                        .foregroundStyle(.snippetSecondary)
                }
                ForEach(Array(rules.enumerated()), id: \.offset) { index, rule in
                    Text("\(index + 1). \(rule)")
                        .font(.footnote)
                        .foregroundStyle(.snippetSecondary)
                }
            }
        } else {
            Divider()
            Label("目前無法載入課程說明與分發條件", systemImage: "exclamationmark.circle")
                .font(.footnote)
                .foregroundStyle(.snippetSecondary)
        }
    }
}

private struct CourseSnippetRows: View {
    let rows: [CourseSnippetRow]

    var body: some View {
        ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
            if index > 0 {
                Divider()
            }
            VStack(alignment: .leading, spacing: 4) {
                if let leading = row.leading {
                    Text(leading)
                        .font(.subheadline.monospacedDigit().weight(.semibold))
                        .foregroundStyle(.tint)
                }
                Text(row.title)
                    .font(.headline)
                    .foregroundStyle(.primary)
                if !row.people.isEmpty {
                    Label(row.people, systemImage: "person")
                }
                Label(row.time, systemImage: "clock")
                    .lineLimit(2)
                if !row.badges.isEmpty {
                    Text(row.badges.joined(separator: " · "))
                        .font(.caption.weight(.medium))
                        .padding(.top, 2)
                }
            }
            .font(.subheadline)
            .foregroundStyle(.snippetSecondary)
            .labelStyle(SnippetDetailLabelStyle())
            .accessibilityElement(children: .combine)
        }
    }
}

struct SnippetDetailLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            configuration.icon
                .font(.caption)
                .frame(width: 16)
            configuration.title
        }
    }
}
