#if SIRI_AI_PREVIEW
import AppIntents
@preconcurrency import Shared

@AppEntity(.generable)
struct CourseSearchRequest: TransientAppEntity {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "課程搜尋條件"

    @Property(title: "關鍵字", description: "課名或課號；可留空並只使用其他篩選條件。")
    var keyword: String?

    @Property(title: "課程類型", description: "只找必修或選修課程。")
    var courseType: CourseTypeOption?

    @Property(title: "名額", description: "篩選尚有名額或已額滿的課程。")
    var seats: SeatsOption?

    @Property(title: "學分", description: "可多選；符合其中一個所選學分數即可。")
    var credits: [Double]?

    @Property(title: "系所", description: "開課系所名稱；可多選，符合其中一個即可。")
    var departments: [String]?

    @Property(title: "學院", description: "開課學院名稱；可多選，符合其中一個即可。")
    var colleges: [String]?

    @Property(title: "老師", description: "授課老師姓名；可多選，符合其中一位即可。")
    var teachers: [String]?

    @Property(title: "星期", description: "篩選每週上課日。")
    var day: WeekdayOption?

    @Property(title: "時段", description: "上午、中午、下午或晚上；會與節次條件合併。")
    var timeOfDay: TimeOfDayOption?

    @Property(title: "節次", description: "可多選節次；會與時段條件合併。")
    var periods: [PeriodOption]?

    var displayRepresentation: DisplayRepresentation { "課程搜尋條件" }

    init() {}
}

@AppIntent(.tool)
struct FindCoursesToolIntent: AppIntent, ProgressReportingIntent {
    static let title: LocalizedStringResource = "搜尋課程目錄"
    static let description = IntentDescription("依課名、課號或篩選條件搜尋課程目錄，並直接在 Siri 回覆結果。")

    @Parameter(
        title: "搜尋條件",
        description: "可填入關鍵字、課程類型、名額、學分、系所、學院、老師、星期或上課時段。",
        requestValueDialog: "要找什麼課或篩選條件？"
    )
    var request: CourseSearchRequest

    func perform() async throws -> some ReturnsValue<[CourseEntity]> & ProvidesDialog & ShowsSnippetView {
        progress.localizedDescription = "正在搜尋課程…"
        var intent = FindCoursesIntent()
        intent.keyword = request.keyword
        intent.courseType = request.courseType
        intent.seats = request.seats
        intent.credits = request.credits
        intent.departments = request.departments
        intent.colleges = request.colleges
        intent.teachers = request.teachers
        intent.day = request.day
        intent.timeOfDay = request.timeOfDay
        intent.periods = request.periods
        return try await intent.execute(
            missingCriteriaError: $request.needsValueError(
                IntentDialog("請提供關鍵字或至少一項篩選條件。")
            )
        )
    }
}
#endif
