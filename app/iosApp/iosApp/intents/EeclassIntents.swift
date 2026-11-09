import AppIntents
@preconcurrency import Shared
import SwiftUI

/// Rows a spoken ee-class answer's card shows.
private let snippetRowLimit = 5

#if SIRI_AI_PREVIEW
@AppIntent(.tool)
#endif
struct DueHomeworkIntent: AppIntent, ProgressReportingIntent {
    static let title: LocalizedStringResource = "近期作業"
    static let description = IntentDescription("查詢 ee-class 各課程尚未截止的作業，依截止時間由近到遠列出。")

    @Dependency private var sharedHost: IosSharedHost

    func perform() async throws -> some ProvidesDialog & ShowsSnippetView {
        progress.localizedDescription = "正在查看 ee-class 作業…"
        let answer = try await sharedHost.dueEeclassHomework()
        let reply = EeclassReply(unreadable: answer.status) ?? EeclassReply(dueHomework: answer)
        return .result(dialog: reply.dialog, view: reply.view)
    }
}

#if SIRI_AI_PREVIEW
@AppIntent(.tool)
#endif
struct NewestBulletinsIntent: AppIntent, ProgressReportingIntent {
    static let title: LocalizedStringResource = "最新公告"
    static let description = IntentDescription("查詢 ee-class 各課程的公告，由新到舊列出。")

    @Dependency private var sharedHost: IosSharedHost

    func perform() async throws -> some ProvidesDialog & ShowsSnippetView {
        progress.localizedDescription = "正在查看 ee-class 公告…"
        let answer = try await sharedHost.newestEeclassBulletins()
        let reply = EeclassReply(unreadable: answer.status) ?? EeclassReply(newestBulletins: answer)
        return .result(dialog: reply.dialog, view: reply.view)
    }
}

private struct EeclassReply {
    let dialog: IntentDialog
    let view: EeclassSnippetList

    init(full: String, supporting: String, view: EeclassSnippetList) {
        dialog = IntentDialog(
            full: LocalizedStringResource(stringLiteral: full),
            supporting: LocalizedStringResource(stringLiteral: supporting)
        )
        self.view = view
    }

    /// The reply when ee-class could not be read at all; nil once it was.
    init?(unreadable status: EeclassLoadStatus) {
        guard status != .loaded else { return nil }
        let needsPortal = status == .needsPortal
        let message = needsPortal
            ? "需要先登入 Portal。打開神奇松果，在 Portal 分頁登入後再問一次。"
            : "現在無法連上 ee-class，請稍後再試。"
        self.init(
            full: message,
            supporting: message,
            view: EeclassSnippetList(
                headline: SnippetHeadline(
                    title: needsPortal ? "需要登入 Portal" : "無法連上 ee-class",
                    detail: needsPortal ? "在神奇松果的 Portal 分頁登入。" : "請稍後再試。",
                    systemImage: needsPortal ? "person.crop.circle.badge.exclamationmark" : "wifi.exclamationmark"
                ),
                rows: [],
                timeImage: "clock"
            )
        )
    }

    init(dueHomework answer: EeclassAcrossCourses) {
        let rows = Array(answer.rows.prefix(snippetRowLimit))
        let count = rows.isEmpty ? "目前沒有尚未截止的作業。" : "有 \(answer.rows.count) 份作業尚未截止。"
        let soonest = rows.first.map { "最近的是\($0.course)的「\($0.title)」，\($0.whenLabel) 截止。" } ?? ""
        self.init(
            full: count + soonest + answer.unreadNote,
            supporting: count + answer.unreadNote,
            view: EeclassSnippetList(
                headline: SnippetHeadline(
                    title: "近期作業",
                    detail: rows.isEmpty ? "沒有尚未截止的作業" : "\(answer.rows.count) 份尚未截止",
                    systemImage: "doc.text"
                ),
                rows: rows,
                timeImage: "clock"
            )
        )
    }

    init(newestBulletins answer: EeclassAcrossCourses) {
        let rows = Array(answer.rows.prefix(snippetRowLimit))
        let newest = rows.first
        self.init(
            full: (newest.map { "最新的公告是\($0.course)的「\($0.title)」，\($0.whenLabel)發布。" } ?? "目前沒有公告。")
                + answer.unreadNote,
            supporting: (newest.map { "最新：\($0.course)「\($0.title)」。" } ?? "目前沒有公告。") + answer.unreadNote,
            view: EeclassSnippetList(
                headline: SnippetHeadline(
                    title: "最新公告",
                    detail: rows.isEmpty ? "沒有公告" : "最近 \(rows.count) 則",
                    systemImage: "megaphone"
                ),
                rows: rows,
                timeImage: "calendar"
            )
        )
    }
}

private extension EeclassAcrossCourses {
    /// Names the courses that did not load, so a short list is not mistaken for a complete one.
    var unreadNote: String {
        unreadCourses.isEmpty ? "" : "有 \(unreadCourses.count) 門課無法讀取：\(unreadCourses.joined(separator: "、"))。"
    }
}

/// The card under an ee-class answer: each row names its course and when it is due or was posted.
struct EeclassSnippetList: View {
    let headline: SnippetHeadline
    let rows: [EeclassCourseRowItem]
    let timeImage: String

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            headline
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                Divider()
                VStack(alignment: .leading, spacing: 4) {
                    Text(row.title)
                        .font(.headline)
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                    Label(row.course, systemImage: "book.closed")
                    Label(row.whenLabel, systemImage: timeImage)
                }
                .font(.subheadline)
                .foregroundStyle(.snippetSecondary)
                .labelStyle(SnippetDetailLabelStyle())
                .accessibilityElement(children: .combine)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
