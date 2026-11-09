import Shared
import SwiftUI
import UIKit

/// Which announcement the course screen shows in a sheet.
struct EeclassBulletinSelection: Identifiable {
    let course: String
    let bulletin: EeclassBulletinRow

    var id: String { bulletin.number }
}

/// A link in an announcement body that opens in the in-app browser.
private struct EeclassBodyLink: Hashable {
    let url: String
}

/// One announcement: its title, ee-class's summary line, the body as rich text, and its attachments.
struct EeclassBulletinSheet: View {
    let sharedHost: IosSharedHost
    let selection: EeclassBulletinSelection

    @State private var page: EeclassBulletinPage?
    @State private var bodyText: AttributedString?
    @State private var reloadID = 0
    @State private var path = NavigationPath()
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack(path: $path) {
            content
                .navigationTitle("公告")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("關閉", systemImage: "xmark") { dismiss() }
                            .labelStyle(.iconOnly)
                    }
                }
                .navigationDestination(for: EeclassPageLink.self) { link in
                    WebPageView(
                        sharedHost: sharedHost,
                        title: link.title,
                        url: sharedHost.eeclassPageUrl(path: link.path)
                    )
                }
                .navigationDestination(for: EeclassBodyLink.self) { link in
                    WebPageView(sharedHost: sharedHost, title: selection.bulletin.title, url: link.url)
                }
        }
        .task(id: reloadID) { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if let page {
            switch page.status {
            case .loaded:
                List {
                    Section {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(selection.bulletin.title)
                                .font(.title3.weight(.semibold))
                                .accessibilityAddTraits(.isHeader)
                            if !page.info.isEmpty {
                                Text(page.info)
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        if let bodyText {
                            Text(bodyText)
                                .textSelection(.enabled)
                                .environment(\.openURL, OpenURLAction { url in
                                    // Links to ee-class need its session, so they stay in the in-app browser.
                                    guard let entry = sharedHost.campusLinkUrl(url: url.absoluteString) else {
                                        return .systemAction
                                    }
                                    path.append(EeclassBodyLink(url: entry))
                                    return .handled
                                })
                        }
                    }
                    if !page.attachments.isEmpty {
                        Section("附件") {
                            ForEach(Array(page.attachments.enumerated()), id: \.offset) { _, attachment in
                                NavigationLink(value: EeclassPageLink(title: attachment.title, path: attachment.path)) {
                                    Label(attachment.title, systemImage: "paperclip")
                                }
                            }
                        }
                    }
                }
            case .needsPortal:
                EeclassUnavailableView(
                    title: "需要登入 Portal",
                    systemImage: "person.crop.circle.badge.exclamationmark",
                    description: "在 Portal 分頁登入後再重新整理。"
                ) { reloadID += 1 }
            case .unavailable:
                EeclassUnavailableView(
                    title: "無法載入公告",
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
        guard let result = try? await sharedHost.loadEeclassBulletin(
            course: selection.course,
            bulletin: selection.bulletin.number
        ) else { return }
        bodyText = result.status == .loaded ? EeclassRichText.attributed(html: result.bodyHtml) : nil
        page = result
    }
}

/// Turns a teacher-written HTML fragment into native rich text: system body font, the app's text colors, working links.
enum EeclassRichText {
    @MainActor
    static func attributed(html: String) -> AttributedString {
        let styled = "<style>body { font: -apple-system-body; }</style>" + html
        guard
            let data = styled.data(using: .utf8),
            let imported = try? NSMutableAttributedString(
                data: data,
                options: [
                    .documentType: NSAttributedString.DocumentType.html,
                    .characterEncoding: String.Encoding.utf8.rawValue
                ],
                documentAttributes: nil
            )
        else {
            return AttributedString(html)
        }
        // The HTML's fixed colors would ignore dark mode; drop them so Text uses the primary color.
        imported.removeAttribute(.foregroundColor, range: NSRange(location: 0, length: imported.length))
        let trimmed = imported.attributedSubstring(from: trailingNewlinesTrimmed(imported.string))
        var result = (try? AttributedString(trimmed, including: \.uiKit)) ?? AttributedString(trimmed.string)
        // SwiftUI Text reads its own font attribute, not UIKit's, so carry bold and italic across.
        for run in result.runs {
            if let font = run.uiKit.font {
                result[run.range].font = Font(font as CTFont)
            }
        }
        return result
    }

    private static func trailingNewlinesTrimmed(_ string: String) -> NSRange {
        let utf16 = Array(string.utf16)
        var end = utf16.count
        while end > 0, utf16[end - 1] == 0x0A { end -= 1 }
        return NSRange(location: 0, length: end)
    }
}
