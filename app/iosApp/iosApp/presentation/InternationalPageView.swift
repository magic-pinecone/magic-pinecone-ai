import Shared
import SwiftUI

struct InternationalPageView: View {
    let sharedHost: IosSharedHost
    let page: InternationalPage

    var body: some View {
        InternationalComposeView(sharedHost: sharedHost, page: page)
            .ignoresSafeArea(.container, edges: .bottom)
            .navigationTitle(page.title.localized)
            .navigationBarTitleDisplayMode(.inline)
    }
}

private struct InternationalComposeView: UIViewControllerRepresentable {
    let sharedHost: IosSharedHost
    let page: InternationalPage

    func makeUIViewController(context: Context) -> some UIViewController {
        sharedHost.internationalScreenController(page: page)
    }

    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
    }
}

extension InternationalPage {
    var systemImage: String {
        switch self {
        case .graduationLanguage: "character.book.closed"
        case .govGuides: "building.2"
        case .serviceLearning: "hands.sparkles"
        case .credits: "graduationcap"
        case .ask: "questionmark.bubble"
        }
    }
}
