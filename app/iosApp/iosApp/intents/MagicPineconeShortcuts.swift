import AppIntents

// Phrases are translated in AppShortcuts.xcstrings; Siri matches them in the device language.
struct MagicPineconeShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: NextClassIntent(),
            phrases: [
                "What's my next class in \(.applicationName)",
                "Next class in \(.applicationName)",
                "Check my next class with \(.applicationName)"
            ],
            shortTitle: "下一堂課",
            systemImageName: "clock"
        )
        AppShortcut(
            intent: ClassesOnDayIntent(),
            phrases: [
                "What classes do I have today in \(.applicationName)",
                "What classes do I have on \(\.$day) in \(.applicationName)",
                "Show today's classes in \(.applicationName)"
            ],
            shortTitle: "今天的課",
            systemImageName: "calendar"
        )
        AppShortcut(
            intent: AddClassesToCalendarIntent(),
            phrases: [
                "Add my classes to Calendar with \(.applicationName)",
                "Sync my timetable to Calendar with \(.applicationName)"
            ],
            shortTitle: "加入行事曆",
            systemImageName: "calendar.badge.plus"
        )
#if SIRI_AI_PREVIEW
        AppShortcut(
            intent: FindCoursesToolIntent(),
            phrases: ["Search the course catalog with \(.applicationName)"],
            shortTitle: "找課",
            systemImageName: "magnifyingglass"
        )
#else
        AppShortcut(
            intent: FindCoursesIntent(),
            phrases: [
                "Find courses with \(.applicationName)",
                "Find \(\.$timeOfDay) courses with \(.applicationName)",
                "Find courses on \(\.$day) with \(.applicationName)",
                "Find \(\.$courseType) courses with \(.applicationName)",
                "Find courses that are \(\.$seats) with \(.applicationName)"
            ],
            shortTitle: "找課",
            systemImageName: "magnifyingglass"
        )
#endif
        AppShortcut(
            intent: DueHomeworkIntent(),
            phrases: [
                "What homework is due in \(.applicationName)",
                "Show my homework in \(.applicationName)",
                "Check ee-class homework with \(.applicationName)"
            ],
            shortTitle: "近期作業",
            systemImageName: "doc.text"
        )
        AppShortcut(
            intent: NewestBulletinsIntent(),
            phrases: [
                "Show the latest announcements in \(.applicationName)",
                "Any new announcements in \(.applicationName)",
                "Check ee-class announcements with \(.applicationName)"
            ],
            shortTitle: "最新公告",
            systemImageName: "megaphone"
        )
        AppShortcut(
            intent: CourseDetailsIntent(),
            phrases: [
                "Course details in \(.applicationName)",
                "Tell me about \(\.$course) in \(.applicationName)"
            ],
            shortTitle: "課程資訊",
            systemImageName: "book.closed"
        )
    }
}
