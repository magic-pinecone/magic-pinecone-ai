import AppIntents
@preconcurrency import Shared

#if SIRI_AI_PREVIEW
@AppIntent(.tool)
#endif
struct NextClassIntent: AppIntent, ProgressReportingIntent {
    static let title: LocalizedStringResource = "下一堂課"
    static let description = IntentDescription("依目前時間查詢已儲存的本學期課表，回覆下一堂課；若正在上課，回覆目前課程和下課時間。")

    @Dependency private var sharedHost: IosSharedHost

    func perform() async throws -> some ReturnsValue<CourseEntity?> & ProvidesDialog & ShowsSnippetView {
        progress.localizedDescription = "正在查看課表…"
        let clock = ClassClock()
        let semester = CourseIdentity.currentSemester
        guard let next = try await sharedHost.nextSavedMeeting(
            semester: semester,
            day: clock.day,
            minuteOfDay: clock.minuteOfDay
        ) else {
            return .result(
                value: nil,
                dialog: IntentDialog(
                    full: "已儲存的課表還沒有課程。可以在神奇松果的選課中把課程加入課表。",
                    supporting: "已儲存的課表還沒有課程。"
                ),
                view: CourseSnippetList(
                    headline: SnippetHeadline(
                        title: "課表還沒有課程",
                        detail: "在選課中加入課程後，就能查詢上課時間。",
                        systemImage: "calendar"
                    ),
                    rows: []
                )
            )
        }
        let meeting = next.meeting
        let title = meeting.course.title
        let end = clockLabel(meeting.endMinute)
        let full = next.inProgress
            ? "現在正在上\(title)，\(end) 下課。"
            : "下一堂是\(title)，\(next.whenLabel()) 開始，\(meeting.periodLabel())。"
        // The card already shows the time and periods, so the onscreen line stays short.
        let supporting = next.inProgress ? "\(title)上課中，\(end) 下課。" : "下一堂：\(title)，\(next.whenLabel())。"
        return .result(
            value: CourseEntity(semester: semester, course: meeting.course),
            dialog: IntentDialog(
                full: LocalizedStringResource(stringLiteral: full),
                supporting: LocalizedStringResource(stringLiteral: supporting)
            ),
            view: CourseSnippetList(
                headline: SnippetHeadline(
                    title: next.inProgress ? "正在上課" : "下一堂課",
                    detail: next.inProgress ? "\(end) 下課" : next.whenLabel(),
                    systemImage: "clock"
                ),
                rows: [CourseSnippetRow(meeting: meeting)]
            )
        )
    }
}

#if SIRI_AI_PREVIEW
@AppIntent(.tool)
#endif
struct ClassesOnDayIntent: AppIntent, ProgressReportingIntent {
    static let title: LocalizedStringResource = "某天的課"
    static let description = IntentDescription("列出已儲存的本學期課表在指定星期的課程；未指定星期時列出今天。")

    @Parameter(title: "星期", description: "不指定時為今天。")
    var day: WeekdayOption?

    static var parameterSummary: some ParameterSummary {
        Summary("\(\.$day)有什麼課")
    }

    @Dependency private var sharedHost: IosSharedHost

    func perform() async throws -> some ReturnsValue<[CourseEntity]> & ProvidesDialog & ShowsSnippetView {
        progress.localizedDescription = "正在查看課表…"
        let semester = CourseIdentity.currentSemester
        let today = ClassClock().day
        let courseDay = day?.courseDay ?? today
        let dayName = courseDay == today ? "今天" : "星期\(courseDay.description_)"
        let meetings = try await sharedHost.savedMeetingsOn(semester: semester, day: courseDay)
        let count = meetings.isEmpty ? "\(dayName)沒有課。" : "\(dayName)有 \(meetings.count) 堂課。"
        let full = meetings.isEmpty
            ? count
            : "\(dayName)有 \(meetings.count) 堂課："
                + meetings.map { "\(clockLabel($0.startMinute)) \($0.course.title)" }.joined(separator: "、")
                + "。"
        return .result(
            value: meetings.map { CourseEntity(semester: semester, course: $0.course) },
            dialog: IntentDialog(
                full: LocalizedStringResource(stringLiteral: full),
                supporting: LocalizedStringResource(stringLiteral: count)
            ),
            view: CourseSnippetList(
                headline: SnippetHeadline(
                    title: courseDay == today ? "今天（星期\(courseDay.description_)）" : dayName,
                    detail: meetings.isEmpty ? "沒有課" : "\(meetings.count) 堂課",
                    systemImage: "calendar"
                ),
                rows: meetings.map(CourseSnippetRow.init(meeting:))
            )
        )
    }
}

/// "13:00" for a minute of the day.
func clockLabel(_ minuteOfDay: Int32) -> String {
    String(format: "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)
}
