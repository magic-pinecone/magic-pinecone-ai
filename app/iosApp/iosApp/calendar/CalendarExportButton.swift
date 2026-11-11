@preconcurrency import Shared
import SwiftUI

struct CalendarExportButton: View {
    let sharedHost: IosSharedHost

    @State private var isExporting = false
    @State private var message: String?

    var body: some View {
        Button(MR.strings().planning_calendar_add.localized, systemImage: "calendar.badge.plus") {
            Task { await export() }
        }
        .disabled(isExporting)
        .accessibilityHint(MR.strings().planning_calendar_add_hint.localized(ClassCalendar.title))
        .alert(
            message ?? "",
            isPresented: Binding(
                get: { message != nil },
                set: { if !$0 { message = nil } }
            )
        ) {
            Button(MR.strings().common_ok.localized) {}
        }
    }

    private func export() async {
        isExporting = true
        defer { isExporting = false }
        do {
            let count = try await ClassCalendar.exportSavedPlan(
                sharedHost: sharedHost,
                semester: CourseIdentity.currentSemester
            )
            message = ClassCalendar.resultMessage(classCount: count)
        } catch let error as ClassCalendarError {
            message = error.message
        } catch {
            message = MR.strings().shell_calendar_export_failed.localized
        }
    }
}
