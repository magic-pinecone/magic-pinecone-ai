import Shared
import SwiftUI

struct CourseSlotPickerComposeView: UIViewControllerRepresentable {
    let sharedHost: IosSharedHost
    let bridge: CourseSearchBridge

    func makeUIViewController(context: Context) -> some UIViewController {
        sharedHost.courseSlotPickerController(bridge: bridge)
    }

    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
    }
}
