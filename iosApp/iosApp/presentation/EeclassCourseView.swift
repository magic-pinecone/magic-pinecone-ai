import Shared
import SwiftUI

/// How many items each section of the course overview shows before "see all".
private let overviewLimit = 5

/// One ee-class course's announcements, materials, and homework, up to `overviewLimit` each. An announcement opens in
/// a sheet; other rows open their page in the in-app browser.
struct EeclassCourseView: View {
    let sharedHost: IosSharedHost
    let course: EeclassCourseLink

    @State private var page: EeclassCoursePage?
    @State private var reloadID = 0
    @State private var openBulletin: EeclassBulletinSelection?

    var body: some View {
        content
            .navigationTitle(course.name)
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: EeclassPageLink.self) { link in
                WebPageView(sharedHost: sharedHost, title: link.title, url: sharedHost.eeclassPageUrl(path: link.path))
            }
            .navigationDestination(for: EeclassCategory.self) { category in
                if let page {
                    EeclassCategoryView(sharedHost: sharedHost, course: course, category: category, page: page)
                }
            }
            .sheet(item: $openBulletin) { selection in
                EeclassBulletinSheet(sharedHost: sharedHost, selection: selection)
            }
            .task(id: reloadID) { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if let page {
            switch page.status {
            case .loaded:
                List {
                    Section(EeclassCategory.bulletins.title) {
                        EeclassCategoryRows(category: .bulletins, page: page, limit: overviewLimit) {
                            openBulletin = EeclassBulletinSelection(course: course.number, bulletin: $0)
                        }
                        seeAll(.bulletins, count: page.bulletins.count)
                    }
                    Section(EeclassCategory.materials.title) {
                        EeclassCategoryRows(category: .materials, page: page, limit: overviewLimit)
                        seeAll(.materials, count: page.materials.count)
                    }
                    Section(EeclassCategory.homework.title) {
                        EeclassCategoryRows(category: .homework, page: page, limit: overviewLimit)
                        seeAll(.homework, count: page.homework.count)
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
                    title: "無法載入課程內容",
                    systemImage: "wifi.exclamationmark",
                    description: "請稍後再試。"
                ) { reloadID += 1 }
            }
        } else {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    @ViewBuilder
    private func seeAll(_ category: EeclassCategory, count: Int) -> some View {
        if count > overviewLimit {
            NavigationLink("查看全部 \(count) 項", value: category)
                .foregroundStyle(.tint)
        }
    }

    private func load() async {
        guard let result = try? await sharedHost.loadEeclassCourse(number: course.number) else { return }
        page = result
    }
}

/// Every item ee-class lists in one category of a course.
private struct EeclassCategoryView: View {
    let sharedHost: IosSharedHost
    let course: EeclassCourseLink
    let category: EeclassCategory
    let page: EeclassCoursePage

    @State private var openBulletin: EeclassBulletinSelection?

    var body: some View {
        List {
            EeclassCategoryRows(category: category, page: page, limit: nil) {
                openBulletin = EeclassBulletinSelection(course: course.number, bulletin: $0)
            }
        }
        .navigationTitle(category.title)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $openBulletin) { selection in
            EeclassBulletinSheet(sharedHost: sharedHost, selection: selection)
        }
    }
}

enum EeclassCategory: Hashable {
    case bulletins, materials, homework

    var title: String {
        switch self {
        case .bulletins: "公告"
        case .materials: "教材"
        case .homework: "作業"
        }
    }

    var emptyText: String {
        switch self {
        case .bulletins: "沒有公告"
        case .materials: "沒有教材"
        case .homework: "沒有作業"
        }
    }
}

/// The rows of one category, the first `limit` of them when set. Announcements call `onOpenBulletin`; other rows
/// push their page.
private struct EeclassCategoryRows: View {
    let category: EeclassCategory
    let page: EeclassCoursePage
    let limit: Int?
    var onOpenBulletin: (EeclassBulletinRow) -> Void = { _ in }

    var body: some View {
        switch category {
        case .bulletins:
            if page.bulletins.isEmpty { empty }
            ForEach(limited(page.bulletins), id: \.number) { bulletin in
                Button { onOpenBulletin(bulletin) } label: {
                    EeclassRowLabel(title: bulletin.title, detail: bulletin.detail)
                }
                .foregroundStyle(.primary)
            }
        case .materials:
            links(page.materials)
        case .homework:
            links(page.homework)
        }
    }

    @ViewBuilder
    private func links(_ rows: [EeclassItemRow]) -> some View {
        if rows.isEmpty { empty }
        ForEach(Array(limited(rows).enumerated()), id: \.offset) { _, row in
            NavigationLink(value: EeclassPageLink(title: row.title, path: row.path)) {
                EeclassRowLabel(title: row.title, detail: row.detail)
            }
        }
    }

    private var empty: some View {
        Text(category.emptyText)
            .foregroundStyle(.secondary)
    }

    private func limited<Row>(_ rows: [Row]) -> [Row] {
        limit.map { Array(rows.prefix($0)) } ?? rows
    }
}

/// Opens an ee-class course screen.
struct EeclassCourseLink: Hashable {
    let number: String
    let name: String
}

/// Opens an ee-class page. The URL is resolved on open, because it depends on the session at that moment.
struct EeclassPageLink: Hashable {
    let title: String
    let path: String
}

private struct EeclassRowLabel: View {
    let title: String
    let detail: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
            if !detail.isEmpty {
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

/// Why ee-class content could not be shown, with a way to try again.
struct EeclassUnavailableView: View {
    let title: String
    let systemImage: String
    let description: String
    let onRetry: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label(title, systemImage: systemImage)
        } description: {
            Text(description)
        } actions: {
            Button("重新整理", action: onRetry)
        }
    }
}
