import EventKit
import Foundation
@preconcurrency import Shared

enum ClassCalendarError: Error {
    case accessDenied
    case termUnknown

    var message: String {
        switch self {
        case .accessDenied: MR.strings().shell_calendar_full_access_denied.localized
        case .termUnknown: MR.strings().planning_calendar_result_no_term.localized
        }
    }
}

/// Mirrors the Saved Course Plan into one calendar the app owns, so exporting again replaces instead of duplicating.
enum ClassCalendar {
    static var title: String { MR.strings().planning_calendar_title.localized }
    private static let identifierKey = "classCalendarIdentifier"

    /// Replaces this term's events with the Saved Course Plan's classes and returns how many were added.
    static func exportSavedPlan(sharedHost: IosSharedHost, semester: String) async throws -> Int {
        guard let termCalendar = try await sharedHost.savedTermCalendar(semester: semester) else {
            throw ClassCalendarError.termUnknown
        }
        let store = EKEventStore()
        guard try await store.requestFullAccessToEvents() else { throw ClassCalendarError.accessDenied }
        let calendar = try ownedCalendar(in: store)

        let start = date(termCalendar.firstClassDay, minute: 0)
        let end = date(termCalendar.lastClassDay, minute: 24 * 60)
        let stale = store.events(matching: store.predicateForEvents(withStart: start, end: end, calendars: [calendar]))
        for event in stale {
            try store.remove(event, span: .thisEvent, commit: false)
        }
        for item in termCalendar.classes {
            let event = EKEvent(eventStore: store)
            event.calendar = calendar
            event.title = item.course.title
            event.timeZone = .campus
            event.startDate = date(item.date, minute: item.startMinute)
            event.endDate = date(item.date, minute: item.endMinute)
            let teachers = item.course.teachers.joined(separator: MR.strings().planning_list_separator.localized)
            let courseNumber = MR.strings().planning_calendar_course_number.localized(item.course.serialNumber)
            event.notes = [item.periodLabel, teachers, courseNumber]
                .filter { !$0.isEmpty }
                .joined(separator: "\n")
            try store.save(event, span: .thisEvent, commit: false)
        }
        try store.commit()
        return termCalendar.classes.count
    }

    static func resultMessage(classCount: Int) -> String {
        classCount == 0
            ? MR.strings().planning_calendar_result_cleared.localized(title)
            : MR.plurals().planning_calendar_result_added.localized(classCount, title)
    }

    private static func ownedCalendar(in store: EKEventStore) throws -> EKCalendar {
        if let identifier = UserDefaults.standard.string(forKey: identifierKey),
           let calendar = store.calendar(withIdentifier: identifier) {
            return calendar
        }
        let calendar = EKCalendar(for: .event, eventStore: store)
        calendar.title = title
        calendar.source = store.defaultCalendarForNewEvents?.source
            ?? store.sources.first { $0.sourceType == .local }
        try store.saveCalendar(calendar, commit: true)
        UserDefaults.standard.set(calendar.calendarIdentifier, forKey: identifierKey)
        return calendar
    }

    private static func date(_ day: CalendarDay, minute: Int32) -> Date {
        var components = DateComponents()
        components.calendar = Calendar(identifier: .gregorian)
        components.timeZone = .campus
        components.year = Int(day.year)
        components.month = Int(day.month)
        components.day = Int(day.day)
        components.minute = Int(minute)
        return components.date!
    }
}

extension TimeZone {
    /// Classes meet on campus time wherever the phone is.
    static let campus = TimeZone(identifier: "Asia/Taipei")!
}
