import SwiftUI
import Shared

struct CourseCatalogComposeView: UIViewControllerRepresentable {
    let sharedHost: IosSharedHost
    let bridge: CourseSearchBridge
    let planBridge: CoursePlanBridge

    func makeUIViewController(context: Context) -> some UIViewController {
        sharedHost.courseCatalogScreenController(
            bridge: bridge,
            planBridge: planBridge
        )
    }

    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
    }
}
