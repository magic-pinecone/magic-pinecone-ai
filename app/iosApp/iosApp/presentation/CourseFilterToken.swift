import Shared

/// Identifiable wrapper so shared `CourseFilter`s can back native search tokens.
struct CourseFilterToken: Identifiable, Hashable {
    let filter: any CourseFilter
    let label: String

    var id: String { label }

    init(_ filter: any CourseFilter) {
        self.filter = filter
        self.label = CourseFilterLabelKt.label(filter)
    }

    var symbol: String {
        switch filter {
        case is CourseFilterTeacher: "person"
        case is CourseFilterDepartment: "building.2"
        case is CourseFilterCollege: "graduationcap"
        case is CourseFilterType: "book.closed"
        case is CourseFilterCredits: "number.circle"
        case is CourseFilterSeats: "person.2"
        default: "calendar"
        }
    }

    static func == (lhs: CourseFilterToken, rhs: CourseFilterToken) -> Bool {
        lhs.id == rhs.id
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

/// The current filters grouped by kind, for the filter sheet's controls.
struct ActiveCourseFilters {
    var types: Set<CourseType> = []
    var seats: Set<SeatAvailability> = []
    var credits: Set<Double> = []
    var teachers: Set<String> = []
    var departments: Set<String> = []
    var colleges: Set<String> = []
    var count = 0

    init(_ filters: [any CourseFilter]) {
        count = filters.count
        for filter in filters {
            switch filter {
            case let value as CourseFilterType: types.insert(value.type)
            case let value as CourseFilterSeats: seats.insert(value.availability)
            case let value as CourseFilterCredits: credits.insert(value.credits)
            case let value as CourseFilterTeacher: teachers.insert(value.name)
            case let value as CourseFilterDepartment: departments.insert(value.name)
            case let value as CourseFilterCollege: colleges.insert(value.name)
            default: break
            }
        }
    }
}
