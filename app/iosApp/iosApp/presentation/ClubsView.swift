import Shared
import SwiftUI

struct ClubsView: View {
    let sharedHost: IosSharedHost

    var body: some View {
        ClubsComposeView(sharedHost: sharedHost)
            .ignoresSafeArea(.container, edges: .bottom)
            .navigationTitle("社團")
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
