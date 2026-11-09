import Shared
import SwiftUI

struct CoursePlanningTimetableView: View {
    let sharedHost: IosSharedHost
    let planBridge: CoursePlanBridge

    var body: some View {
        CoursePlanningTimetableComposeView(
            sharedHost: sharedHost,
            planBridge: planBridge
        )
        .ignoresSafeArea(.container, edges: .bottom)
    }
}
