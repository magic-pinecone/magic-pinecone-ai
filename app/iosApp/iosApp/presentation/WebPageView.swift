import Shared
import SwiftUI

/// The in-app browser. The back button and swipe leave the page; a separate button goes back within it.
/// Sharing and opening externally use the URL it was opened with,
/// because later pages can carry one-time sign-in URLs.
struct WebPageView: View {
    let title: String
    let url: String

    @State private var page: PortalWebPageBridge
    @State private var canGoBack = false

    init(sharedHost: IosSharedHost, title: String, url: String) {
        self.title = title
        self.url = url
        _page = State(initialValue: sharedHost.portalWebPage(url: url))
    }

    var body: some View {
        WebPageComposeView(page: page)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if canGoBack {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("上一頁", systemImage: "arrow.uturn.backward") { page.goBack() }
                    }
                }
                if let pageURL = URL(string: url) {
                    ToolbarItem(placement: .topBarTrailing) {
                        Menu {
                            ShareLink(item: pageURL)
                            Link(destination: pageURL) {
                                Label("在瀏覽器中開啟", systemImage: "safari")
                            }
                        } label: {
                            Label("更多選項", systemImage: "ellipsis")
                        }
                    }
                }
            }
            .task {
                for await value in page.canGoBack {
                    canGoBack = value.boolValue
                }
            }
    }
}

private struct WebPageComposeView: UIViewControllerRepresentable {
    let page: PortalWebPageBridge

    func makeUIViewController(context: Context) -> some UIViewController {
        page.controller
    }

    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
    }
}
