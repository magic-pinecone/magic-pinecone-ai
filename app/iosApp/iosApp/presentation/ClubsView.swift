import Shared
import SwiftUI

struct ClubsView: View {
    let sharedHost: IosSharedHost

    var body: some View {
        ClubsComposeView(sharedHost: sharedHost)
            .ignoresSafeArea(.container, edges: .bottom)
            .navigationTitle(MR.strings().clubs_title.localized)
            .navigationBarTitleDisplayMode(.inline)
    }
}

private struct ClubsComposeView: UIViewControllerRepresentable {
    let sharedHost: IosSharedHost

    func makeUIViewController(context: Context) -> some UIViewController {
        sharedHost.clubsScreenController()
    }

    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
    }
}
