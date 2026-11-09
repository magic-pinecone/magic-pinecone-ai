import AppIntents
import Foundation
@preconcurrency import Shared

enum WeekdayOption: String, AppEnum {
    case monday, tuesday, wednesday, thursday, friday, saturday, sunday

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "星期")
    static let caseDisplayRepresentations: [Self: DisplayRepresentation] = [
        .monday: DisplayRepresentation(title: "星期一", synonyms: ["週一", "禮拜一"]),
        .tuesday: DisplayRepresentation(title: "星期二", synonyms: ["週二", "禮拜二"]),
        .wednesday: DisplayRepresentation(title: "星期三", synonyms: ["週三", "禮拜三"]),
        .thursday: DisplayRepresentation(title: "星期四", synonyms: ["週四", "禮拜四"]),
        .friday: DisplayRepresentation(title: "星期五", synonyms: ["週五", "禮拜五"]),
        .saturday: DisplayRepresentation(title: "星期六", synonyms: ["週六", "禮拜六"]),
        .sunday: DisplayRepresentation(title: "星期日", synonyms: ["星期天", "週日", "禮拜天"])
    ]

    var courseDay: CourseDay {
        switch self {
        case .monday: .monday
        case .tuesday: .tuesday
        case .wednesday: .wednesday
        case .thursday: .thursday
        case .friday: .friday
        case .saturday: .saturday
        case .sunday: .sunday
        }
    }

    var label: String { "星期\(courseDay.description_)" }
}

/// The current weekday and minute of the day, in the terms the Saved Course Plan queries use.
struct ClassClock {
    let day: CourseDay
    let minuteOfDay: Int32

    init(date: Date = .now, calendar: Calendar = .campus) {
        // Calendar weekdays start at 1 for Sunday; course days start at 1 for Monday.
        let isoWeekday = (calendar.component(.weekday, from: date) + 5) % 7 + 1
        day = CourseDay.companion.fromIsoWeekday(isoWeekday: Int32(isoWeekday))
        let time = calendar.dateComponents([.hour, .minute], from: date)
        minuteOfDay = Int32((time.hour ?? 0) * 60 + (time.minute ?? 0))
    }
}

enum TimeOfDayOption: String, AppEnum {
    case morning, noon, afternoon, evening

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "時段")
    static let caseDisplayRepresentations: [Self: DisplayRepresentation] = [
        .morning: DisplayRepresentation(title: "上午", subtitle: "第 1–4 節", synonyms: ["早上"]),
        .noon: DisplayRepresentation(title: "中午", subtitle: "第 Z 節"),
        .afternoon: DisplayRepresentation(title: "下午", subtitle: "第 5–9 節"),
        .evening: DisplayRepresentation(title: "晚上", subtitle: "第 A–D 節", synonyms: ["夜間"])
    ]

    var periods: [CoursePeriod] {
        switch self {
        case .morning: [.one, .two, .three, .four]
        case .noon: [.noon]
        case .afternoon: [.five, .six, .seven, .eight, .nine]
        case .evening: [.a, .b, .c, .d]
        }
    }

    var label: String {
        switch self {
        case .morning: "上午"
        case .noon: "中午"
        case .afternoon: "下午"
        case .evening: "晚上"
        }
    }
}

enum CourseTypeOption: String, AppEnum {
    case required, elective

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "修別")
    static let caseDisplayRepresentations: [Self: DisplayRepresentation] = [
        .required: "必修",
        .elective: "選修"
    ]

    var courseType: CourseType {
        switch self {
        case .required: .required
        case .elective: .elective
        }
    }
}

enum SeatsOption: String, AppEnum {
    case open, full

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "名額")
    static let caseDisplayRepresentations: [Self: DisplayRepresentation] = [
        .open: DisplayRepresentation(title: "尚有名額", synonyms: ["有名額", "還有名額", "沒額滿"]),
        .full: DisplayRepresentation(title: "已額滿", synonyms: ["額滿"])
    ]

    var availability: SeatAvailability {
        switch self {
        case .open: .open
        case .full: .full
        }
    }
}

/// One row of the slot grid in the filter sheet.
enum PeriodOption: String, AppEnum {
    case one, two, three, four, noon, five, six, seven, eight, nine, periodA, periodB, periodC, periodD

    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "節次")
    static let caseDisplayRepresentations: [Self: DisplayRepresentation] = [
        .one: DisplayRepresentation(title: "第 1 節", subtitle: "08:00–08:50"),
        .two: DisplayRepresentation(title: "第 2 節", subtitle: "09:00–09:50"),
        .three: DisplayRepresentation(title: "第 3 節", subtitle: "10:00–10:50"),
        .four: DisplayRepresentation(title: "第 4 節", subtitle: "11:00–11:50"),
        .noon: DisplayRepresentation(title: "第 Z 節", subtitle: "12:00–12:50"),
        .five: DisplayRepresentation(title: "第 5 節", subtitle: "13:00–13:50"),
        .six: DisplayRepresentation(title: "第 6 節", subtitle: "14:00–14:50"),
        .seven: DisplayRepresentation(title: "第 7 節", subtitle: "15:00–15:50"),
        .eight: DisplayRepresentation(title: "第 8 節", subtitle: "16:00–16:50"),
        .nine: DisplayRepresentation(title: "第 9 節", subtitle: "17:00–17:50"),
        .periodA: DisplayRepresentation(title: "第 A 節", subtitle: "18:00–18:50"),
        .periodB: DisplayRepresentation(title: "第 B 節", subtitle: "19:00–19:50"),
        .periodC: DisplayRepresentation(title: "第 C 節", subtitle: "20:00–20:50"),
        .periodD: DisplayRepresentation(title: "第 D 節", subtitle: "21:00–21:50")
    ]

    var coursePeriod: CoursePeriod {
        switch self {
        case .one: .one
        case .two: .two
        case .three: .three
        case .four: .four
        case .noon: .noon
        case .five: .five
        case .six: .six
        case .seven: .seven
        case .eight: .eight
        case .nine: .nine
        case .periodA: .a
        case .periodB: .b
        case .periodC: .c
        case .periodD: .d
        }
    }
}

// The filter sheet's catalog choices, so Siri and Shortcuts offer the same names as the app.

struct TeacherOptions: DynamicOptionsProvider {
    @Dependency private var sharedHost: IosSharedHost

    func results() async throws -> [String] {
        try await sharedHost.courseFilterOptions(semester: CourseIdentity.currentSemester).teachers
    }
}

struct DepartmentOptions: DynamicOptionsProvider {
    @Dependency private var sharedHost: IosSharedHost

    func results() async throws -> [String] {
        try await sharedHost.courseFilterOptions(semester: CourseIdentity.currentSemester).departments
    }
}

struct CollegeOptions: DynamicOptionsProvider {
    @Dependency private var sharedHost: IosSharedHost

    func results() async throws -> [String] {
        try await sharedHost.courseFilterOptions(semester: CourseIdentity.currentSemester).colleges
    }
}

struct CreditOptions: DynamicOptionsProvider {
    @Dependency private var sharedHost: IosSharedHost

    func results() async throws -> [Double] {
        try await sharedHost.courseFilterOptions(semester: CourseIdentity.currentSemester).credits.map(\.doubleValue)
    }
}

extension Calendar {
    /// Reads weekdays and times on campus, so a phone abroad still matches the period table.
    static let campus: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .campus
        return calendar
    }()
}
