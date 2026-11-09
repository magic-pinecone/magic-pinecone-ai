import AppIntents
import CoreSpotlight
@preconcurrency import Shared

/// A course in the Course Catalog, identified by its Course Identity.
struct CourseEntity: IndexedEntity {
    static let typeDisplayRepresentation = TypeDisplayRepresentation(
        name: "課程",
        numericFormat: "\(placeholder: .int) 門課程"
    )
    static let defaultQuery = CourseEntityQuery()

    let id: String

#if SIRI_AI_PREVIEW
    // Property descriptions need the iOS 27.3 SDK.
    @Property(title: "課程名稱", description: "課程的名稱")
    var title: String

    @Property(title: "班次", description: "課程代號，包含課程種類與班別")
    var classNo: String
#else
    @Property(title: "課程名稱")
    var title: String

    @Property(title: "班次")
    var classNo: String
#endif

    @Property(title: "授課教師")
    var teachers: [String]

    @Property(title: "開課系所")
    var department: String

    @Property(title: "學院")
    var college: String

    @Property(title: "學分")
    var credits: Double

    @Property(title: "修別")
    var courseType: String

    @Property(title: "上課時間")
    var schedule: String

    @Property(title: "名額")
    var seats: String

    // Filled only when the course details were loaded, as CourseDetailsIntent does.
    @Property(title: "課程目標")
    var objectives: String

    @Property(title: "課程內容")
    var content: String

    @Property(title: "教科書")
    var books: String

    @Property(title: "教學方式")
    var teachingMethod: String

    @Property(title: "評量方式")
    var gradingPolicy: String

    @Property(title: "分發條件")
    var distributionConditions: [String]

    /// "CH2009-* · 必修 · 王小明" over "3 學分 · 1-2、4-5", the chip's subtitle.
    let compactSummary: String

    init(semester: String, course: CourseSummary) {
        id = CourseIdentity(semester: semester, serialNumber: course.serialNumber).entityID
        // Plain stored properties come first; the @Property setters need self fully initialized.
        let identityLine = [
            course.classNo,
            course.courseType == .unknown ? "" : course.courseType.description_,
            course.teachers.filter { !$0.isEmpty }.joined(separator: "、")
        ]
        let timeLine = [
            "\(course.credit.formatted(.number.precision(.fractionLength(0...2)))) 學分",
            course.scheduleDescription
        ]
        compactSummary = [identityLine, timeLine]
            .map { $0.filter { !$0.isEmpty }.joined(separator: " · ") }
            .joined(separator: "\n")
        title = course.title
        classNo = course.classNo
        teachers = course.teachers.filter { !$0.isEmpty }
        department = course.departmentName
        college = course.collegeName
        credits = course.credit
        courseType = course.courseType.description_
        schedule = course.meetingsDescription
        seats = course.seatsDescription
        objectives = ""
        content = ""
        books = ""
        teachingMethod = ""
        gradingPolicy = ""
        distributionConditions = []
    }

    init(semester: String, course: CourseSummary, detail: CourseDetail) {
        self.init(semester: semester, course: course)
        objectives = detail.objectives
        content = detail.content
        books = detail.books
        teachingMethod = detail.teachingMethod
        gradingPolicy = detail.gradingPolicy
        distributionConditions = detail.rankedDistributionRules
    }

    var identity: CourseIdentity? { CourseIdentity(entityID: id) }

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(
            title: "\(title)",
            subtitle: "\(compactSummary)",
            image: CourseCodeBadge.image(classNo: classNo) ?? .init(systemName: "book.closed")
        )
    }

    var attributeSet: CSSearchableItemAttributeSet {
        let attributes = defaultAttributeSet
        attributes.contentDescription = "\(compactSummary)\n\(schedule)"
        attributes.keywords = teachers + [department, college, classNo, identity?.serialNumber ?? ""]
            .filter { !$0.isEmpty }
        return attributes
    }
}

extension CourseSummary {
    /// "星期一 第 1–2 節 08:00–09:50；星期三 第 3 節 10:00–10:50"
    var meetingsDescription: String {
        let meetings = meetings()
        guard !meetings.isEmpty else { return "未提供上課時間" }
        return meetings
            .map { "星期\($0.day.description_) \($0.periodLabel()) \($0.timeRangeLabel())" }
            .joined(separator: "；")
    }

    var seatsDescription: String {
        // A limitCnt of 0 means the catalog publishes no cap, as in the open-seats filter.
        if limitCnt == 0 { return "已選 \(admitCnt) 人，不限名額" }
        let remaining = Int(limitCnt) - Int(admitCnt)
        return remaining > 0
            ? "已選 \(admitCnt)／\(limitCnt) 人，尚餘 \(remaining) 個名額"
            : "已選 \(admitCnt)／\(limitCnt) 人，已額滿"
    }
}

extension CourseDetail {
    /// Distribution rules in the order they are applied.
    var rankedDistributionRules: [String] {
        distributionConditions.sorted { $0.priority < $1.priority }.map(\.rule)
    }
}

struct CourseEntityQuery: EntityStringQuery {
    @Dependency private var sharedHost: IosSharedHost

    func entities(for identifiers: [CourseEntity.ID]) async throws -> [CourseEntity] {
        var entities: [CourseEntity] = []
        for identity in identifiers.compactMap(CourseIdentity.init(entityID:)) {
            if let course = try await sharedHost.course(
                semester: identity.semester,
                serialNumber: identity.serialNumber
            ) {
                entities.append(CourseEntity(semester: identity.semester, course: course))
            }
        }
        return entities
    }

    func entities(matching string: String) async throws -> [CourseEntity] {
        let semester = CourseIdentity.currentSemester
        return try await sharedHost
            .findCourses(semester: semester, keyword: string, filters: [], limit: 10)
            .map { CourseEntity(semester: semester, course: $0) }
    }

    func suggestedEntities() async throws -> [CourseEntity] {
        try await sharedHost.savedPlanCourses()
            .map { CourseEntity(semester: CourseIdentity.currentSemester, course: $0) }
    }
}

extension IosSharedHost {
    /// Courses of the Saved Course Plan in weekly order, each once.
    func savedPlanCourses() async throws -> [CourseSummary] {
        var seen = Set<String>()
        var courses: [CourseSummary] = []
        for day in CourseDay.allCases where day != .unknown {
            for meeting in try await savedMeetingsOn(semester: CourseIdentity.currentSemester, day: day)
            where seen.insert(meeting.course.serialNumber).inserted {
                courses.append(meeting.course)
            }
        }
        return courses
    }
}
