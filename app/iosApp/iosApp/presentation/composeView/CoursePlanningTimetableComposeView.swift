import Shared
import SwiftUI

struct CoursePlanningTimetableComposeView: UIViewControllerRepresentable {
    let sharedHost: IosSharedHost
    let planBridge: CoursePlanBridge

    func makeUIViewController(context: Context) -> some UIViewController {
        sharedHost.coursePlanningTimetableScreenController(
            planBridge: planBridge
        )
    }

    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
    }
}
