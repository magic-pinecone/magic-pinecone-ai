import Shared
import SwiftUI
import UIKit

private func composePreview(
    _ scenario: ComposePreviewScenario,
    isDarkTheme: Bool = false
) -> UIViewController {
    IosComposePreviewHost.shared.createController(
        scenario: scenario,
        isDarkTheme: isDarkTheme
    )
}

#Preview("Timetable · populated") {
    composePreview(.timetablePopulated)
}

#Preview("Timetable · empty") {
    composePreview(.timetableEmpty)
}

#Preview("Timetable · loading") {
    composePreview(.timetableLoading)
}

#Preview("Timetable · failure") {
    composePreview(.timetableFailure)
}

#Preview("Course details") {
    composePreview(.courseDetails)
}

#Preview("Portal") {
    composePreview(.portal)
}

#Preview("Settings") {
    composePreview(.settings)
}

#Preview("Course slot picker") {
    composePreview(.slotPicker)
}

#Preview("Course slot picker · dark") {
    composePreview(.slotPicker, isDarkTheme: true)
}
