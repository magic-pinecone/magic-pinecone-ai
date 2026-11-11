import SwiftUI
import Shared

struct CampusMapComposeView: UIViewControllerRepresentable {
    let sharedHost: IosSharedHost

    func makeUIViewController(context: Context) -> some UIViewController {
        sharedHost.campusMapScreenController()
    }

    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
    }
}
