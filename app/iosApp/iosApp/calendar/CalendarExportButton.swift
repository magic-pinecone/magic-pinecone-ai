@preconcurrency import Shared
import SwiftUI

struct CalendarExportButton: View {
    let sharedHost: IosSharedHost

    @State private var isExporting = false
    @State private var message: String?

    var body: some View {
        Button("加入行事曆", systemImage: "calendar.badge.plus") {
            Task { await export() }
        }
        .disabled(isExporting)
        .accessibilityHint("把已儲存的課表加入「\(ClassCalendar.title)」行事曆")
        .alert(
            message ?? "",
            isPresented: Binding(
                get: { message != nil },
                set: { if !$0 { message = nil } }
            )
        ) {
            Button("好") {}
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
            message = String(localized: error.localizedStringResource)
        } catch {
            message = "無法加入行事曆，請稍後再試。"
        }
    }
}
