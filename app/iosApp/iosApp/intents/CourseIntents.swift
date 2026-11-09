import AppIntents
@preconcurrency import Shared

#if !SIRI_AI_PREVIEW
@available(iOS 27.0, *)
@AppIntent(schema: .system.searchInApp)
struct SearchCoursesInAppIntent: ShowInAppSearchResultsIntent {
    static let title: LocalizedStringResource = "在選課中搜尋"
    static let description = IntentDescription("開啟神奇松果的選課畫面，並將搜尋內容帶入關鍵字欄位。")
    static let searchScopes: [StringSearchScope] = [.general]

    @Parameter(title: "搜尋內容", requestValueDialog: "要搜尋什麼課程？")
    var criteria: StringSearchCriteria

    @Dependency private var router: AppRouter

    @MainActor
    func perform() async throws -> some IntentResult {
        router.open(.catalogSearch(criteria.term))
        return .result()
    }
}
#endif

/// The Course Catalog search with the same keyword and filters as the search bar and filter sheet, answered in Siri.
struct FindCoursesIntent: AppIntent, ProgressReportingIntent {
    static let title: LocalizedStringResource = "找課程"
    static let description = IntentDescription(
        "依關鍵字與篩選條件搜尋本學期課程，直接在 Siri 回覆結果。可只指定篩選條件，不必提供關鍵字。上課時段篩選只保留所有上課時間都落在所選範圍內的課程。"
    )
#if SIRI_AI_PREVIEW
    static let isDiscoverable = false
#endif

    @Parameter(
        title: "關鍵字",
        description: "課名、課號或其他搜尋文字。學分、星期、時段、老師、系所等條件使用對應參數；未指定關鍵字時留空。",
        requestValueDialog: "要找什麼課？"
    )
    var keyword: String?

    @Parameter(title: "課程類型", description: "只找所選修別的課程：必修或選修。")
    var courseType: CourseTypeOption?

    @Parameter(
        title: "名額",
        description: "尚有名額或已額滿；依目錄資料判斷，可能與即時選課狀態不同。"
    )
    var seats: SeatsOption?

    @Parameter(title: "學分", description: "可多選；課程符合其中一個所選學分數即可。", optionsProvider: CreditOptions())
    var credits: [Double]?

    @Parameter(
        title: "系所",
        description: "開課系所名稱；可多選，符合其中一個即可。",
        optionsProvider: DepartmentOptions()
    )
    var departments: [String]?

    @Parameter(
        title: "學院",
        description: "開課學院名稱；可多選，符合其中一個即可。",
        optionsProvider: CollegeOptions()
    )
    var colleges: [String]?

    @Parameter(
        title: "老師",
        description: "授課老師姓名；可多選，符合其中一位即可。",
        optionsProvider: TeacherOptions()
    )
    var teachers: [String]?

    @Parameter(
        title: "星期",
        description: "篩選每週上課日；未指定星期但選了節次時，任何星期都符合。"
    )
    var day: WeekdayOption?

    @Parameter(
        title: "時段",
        description: "上午、中午、下午或晚上會轉換為課表節次，並與節次條件合併。"
    )
    var timeOfDay: TimeOfDayOption?

    @Parameter(
        title: "節次",
        description: "可多選節次；與時段條件合併。未指定星期時，包含所有星期。"
    )
    var periods: [PeriodOption]?

    static var parameterSummary: some ParameterSummary {
        Summary("找\(\.$keyword)課程") {
            \.$courseType
            \.$seats
            \.$credits
            \.$departments
            \.$colleges
            \.$teachers
            \.$day
            \.$timeOfDay
            \.$periods
        }
    }

    @Dependency private var sharedHost: IosSharedHost

    func perform() async throws -> some ReturnsValue<[CourseEntity]> & ProvidesDialog & ShowsSnippetView {
        progress.localizedDescription = "正在搜尋課程…"
        return try await execute(missingCriteriaError: $keyword.needsValueError())
    }

    /// Runs the search without touching `progress`, which exists only on an intent the system performs.
    func execute(
        missingCriteriaError: AppIntentError
    ) async throws -> some ReturnsValue<[CourseEntity]> & ProvidesDialog & ShowsSnippetView {
        let semester = CourseIdentity.currentSemester
        let query = try await courseQuery(semester: semester)
        let searchable = !query.keyword.isEmpty || !query.filters.isEmpty
        if !searchable, query.unmatched.isEmpty {
            throw missingCriteriaError
        }
        // Searching with only unknown names left out would list the whole catalog.
        let courses = searchable
            ? try await sharedHost.findCourses(
                semester: semester,
                keyword: query.keyword,
                filters: query.filters,
                limit: Int32.max
            )
            : []
        let entities = courses.prefix(20).map { CourseEntity(semester: semester, course: $0) }
        return .result(
            value: Array(entities),
            dialog: IntentDialog(
                full: LocalizedStringResource(stringLiteral: query.unmatchedNote + Self.spokenSummary(of: courses)),
                supporting: LocalizedStringResource(stringLiteral: query.unmatchedNote + Self.countSummary(of: courses))
            ),
            view: CourseSearchSnippet(
                criteria: query.criteria,
                total: courses.count,
                rows: courses.prefix(5).map(CourseSnippetRow.init(course:))
            )
        )
    }

    /// Builds the filters the sheet would hold for these parameters.
    private func courseQuery(semester: String) async throws -> IntentCourseQuery {
        var query = IntentCourseQuery(keyword: keyword?.trimmingCharacters(in: .whitespaces) ?? "")
        if let courseType {
            query.add(CourseFilterType(type: courseType.courseType))
        }
        if let seats {
            query.add(CourseFilterSeats(availability: seats.availability))
        }
        for value in credits ?? [] {
            query.add(CourseFilterCredits(credits: value))
        }
        let names = [departments, colleges, teachers].contains { !($0 ?? []).isEmpty }
            ? try await sharedHost.courseFilterOptions(semester: semester)
            : nil
        if let names {
            query.addNames(departments, choices: names.departments, make: CourseFilterDepartment.init(name:))
            query.addNames(colleges, choices: names.colleges, make: CourseFilterCollege.init(name:))
            query.addNames(teachers, choices: names.teachers, make: CourseFilterTeacher.init(name:))
        }
        if let schedule {
            query.add(schedule.filter, label: schedule.label)
        }
        return query
    }

    /// The slot grid selection: the chosen day (or every day) times the chosen periods (or every period).
    private var schedule: (filter: CourseFilterSchedule, label: String)? {
        let chosenPeriods = (periods ?? []).map(\.coursePeriod) + (timeOfDay?.periods ?? [])
        guard day != nil || !chosenPeriods.isEmpty else { return nil }
        let days = day.map { [$0] } ?? WeekdayOption.allCases
        let slotPeriods = chosenPeriods.isEmpty ? CoursePeriod.allCases : chosenPeriods
        let slots = days.flatMap { day in
            slotPeriods.map { CourseTime(day: day.courseDay, period: $0) }
        }
        let periodNumbers = (periods ?? []).map(\.coursePeriod.description_)
        let periodLabel = periodNumbers.isEmpty ? nil : "第 \(periodNumbers.joined(separator: "、")) 節"
        let label = [day?.label, timeOfDay?.label, periodLabel].compactMap { $0 }.joined(separator: " ")
        // Like the slot grid, a course matches only when every meeting falls within the chosen slots.
        return (CourseFilterSchedule(slots: Set(slots), match: .fitsWithin), label)
    }

    private static func countSummary(of courses: [CourseSummary]) -> String {
        courses.isEmpty ? "找不到符合的課程。" : "找到 \(courses.count) 門課。"
    }

    private static func spokenSummary(of courses: [CourseSummary]) -> String {
        guard !courses.isEmpty else { return countSummary(of: courses) }
        let top = courses.prefix(3).map(\.spokenName).joined(separator: "、")
        if courses.count <= 3 {
            return "找到 \(courses.count) 門課：\(top)。"
        }
        return "找到 \(courses.count) 門課，前 3 門是：\(top)。"
    }
}

struct CourseDetailsIntent: AppIntent, ProgressReportingIntent {
    static let title: LocalizedStringResource = "課程資訊"
    static let description = IntentDescription("查看一門課的學分、上課時間、名額、課程說明與分發條件。")

    @Parameter(title: "課程", requestValueDialog: "要查哪一門課？")
    var course: CourseEntity

    static var parameterSummary: some ParameterSummary {
        Summary("查看\(\.$course)的課程資訊")
    }

    @Dependency private var sharedHost: IosSharedHost

    func perform() async throws -> some ReturnsValue<CourseEntity> & ProvidesDialog & ShowsSnippetView {
        progress.localizedDescription = "正在查詢課程資訊…"
        // Read the catalog again so seats are current, not what the entity held when it was resolved.
        guard let identity = course.identity,
              let summary = try await sharedHost.course(
                  semester: identity.semester,
                  serialNumber: identity.serialNumber
              ) else {
            return .result(
                value: course,
                dialog: "找不到這門課的資訊。",
                view: CourseDetailSnippet(details: nil)
            )
        }
        // The description is a separate request; answer with the basics when it fails.
        let detail = try? await sharedHost.loadCourseDetail(
            semester: identity.semester,
            serialNumber: identity.serialNumber
        )
        let entity = detail.map { CourseEntity(semester: identity.semester, course: summary, detail: $0) }
            ?? CourseEntity(semester: identity.semester, course: summary)
        return .result(
            value: entity,
            dialog: IntentDialog(stringLiteral: summary.spokenDetails),
            view: CourseDetailSnippet(details: CourseSnippetDetails(course: summary, detail: detail))
        )
    }
}

/// A Course Query assembled from intent parameters, with a label for each criterion the snippet shows.
private struct IntentCourseQuery {
    let keyword: String
    var filters: [CourseFilter] = []
    var criteria: [String] = []
    /// Names Siri asked for that the catalog does not have; they are left out of the search.
    var unmatched: [String] = []

    init(keyword: String) {
        self.keyword = keyword
        if !keyword.isEmpty { criteria.append("「\(keyword)」") }
    }

    mutating func add(_ filter: CourseFilter, label: String? = nil) {
        filters.append(filter)
        criteria.append(label ?? CourseFilterLabelKt.label(filter))
    }

    /// Turns spoken names into catalog-name filters, the way the sheet does, so several names match any one of them.
    mutating func addNames(_ names: [String]?, choices: [String], make: (String) -> CourseFilter) {
        for name in names ?? [] {
            let trimmed = name.trimmingCharacters(in: .whitespaces)
            guard !trimmed.isEmpty else { continue }
            let matches = CatalogNamesKt.matchCatalogNames(name: trimmed, choices: choices)
            guard !matches.isEmpty else {
                unmatched.append(trimmed)
                criteria.append("找不到「\(trimmed)」")
                continue
            }
            filters += matches.map(make)
            criteria.append(CourseFilterLabelKt.label(make(matches.count == 1 ? matches[0] : trimmed)))
        }
    }

    /// Tells the user which names were left out, so an empty or broad result is not mistaken for theirs.
    var unmatchedNote: String {
        unmatched.isEmpty ? "" : "目錄中找不到「\(unmatched.joined(separator: "」、「"))」，已略過。"
    }
}

extension CourseSummary {
    /// "王小明老師的微積分", which tells sections of the same course apart when read aloud.
    var spokenName: String {
        guard let teacher = teachers.first(where: { !$0.isEmpty }) else { return title }
        return "\(teacher)老師的\(title)"
    }

    /// "王小明老師的微積分，3 學分，必修。上課時間：…。已選 50／60 人，尚餘 10 個名額。"
    var spokenDetails: String {
        "\(spokenName)，\(credit.formatted()) 學分，\(courseType.description_)。"
            + "上課時間：\(meetingsDescription)。\(seatsDescription)。"
    }
}
