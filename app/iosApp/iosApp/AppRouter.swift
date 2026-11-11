import Observation

enum AppTab: Hashable {
    case home
    case coursePlanning
    case portal
}

/// The semester and serial number that identify one course in the Course Catalog.
struct CourseIdentity: Hashable, Sendable {
    /// The semester whose Course Catalog the app shows and intents search.
    static let currentSemester = "115-1"

    let semester: String
    let serialNumber: String

    init(semester: String, serialNumber: String) {
        self.semester = semester
        self.serialNumber = serialNumber
    }

    init?(entityID: String) {
        let parts = entityID.split(separator: ":", maxSplits: 1).map(String.init)
        guard parts.count == 2, !parts[0].isEmpty, !parts[1].isEmpty else { return nil }
        self.init(semester: parts[0], serialNumber: parts[1])
    }

    var entityID: String { "\(semester):\(serialNumber)" }
}

struct PortalDestination: Hashable, Sendable {
    let title: String
    let url: String
}

enum AppRoute: Hashable, Sendable {
    case course(CourseIdentity)
    case catalogSearch(String)
    case portal(PortalDestination)

    var tab: AppTab {
        switch self {
        case .course, .catalogSearch: .coursePlanning
        case .portal: .portal
        }
    }

    var course: CourseIdentity? {
        if case let .course(identity) = self { identity } else { nil }
    }

    var catalogSearch: String? {
        if case let .catalogSearch(query) = self { query } else { nil }
    }

    var portal: PortalDestination? {
        if case let .portal(destination) = self { destination } else { nil }
    }
}

/// Navigation requested from outside the view tree, such as App Intents and Spotlight.
@MainActor
@Observable
final class AppRouter {
    var selectedTab: AppTab = .home
    private(set) var pendingRoute: AppRoute?

    func open(_ route: AppRoute) {
        selectedTab = route.tab
        pendingRoute = route
    }

    /// Hands the pending route to the view that `match` accepts, so it is handled once.
    func take<Value>(_ match: (AppRoute) -> Value?) -> Value? {
        guard let route = pendingRoute, let value = match(route) else { return nil }
        pendingRoute = nil
        return value
    }
}
