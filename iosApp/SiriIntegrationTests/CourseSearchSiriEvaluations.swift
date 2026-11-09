#if SIRI_AI_PREVIEW
import AppIntentsTesting
import Evaluations
import Foundation
import SiriEvaluations
import Testing
import XCUIAutomation

private struct ExpectedCourseQuery {
    var keyword: String?
    var credits: [Double] = []
    var day: String?
    var timeOfDay: String?
    var seats: String?
}

private struct CourseSearchEvaluation: Evaluation {
    let name: String
    let dataset: SiriScenarioLoader
    let expected: ExpectedCourseQuery

    @MainActor
    func subject(from sample: SiriScenario) async throws -> SiriScenarioSubject {
        let app = XCUIApplication(bundleIdentifier: SiriCourseSearchTests.bundleIdentifier)
        app.launch()
        let subject = try await SiriService.run(scenario: sample)
        let calls = subject.siriTranscript.turns.last?.intentInvocations.map(\.value) ?? []
        let completed = calls.last {
            $0.intent.identifier == SiriCourseSearchTests.finder.identifier && $0.outcome == .succeeded
        }
        #expect(completed != nil, "Siri must complete a structured course search in the final turn.")
        if let completed {
            let request: AnyTransientAppEntity = try completed.intent.request
            let keyword: String? = try request.keyword
            let credits: [Double]? = try request.credits
            let day: AnyAppEnum? = try request.day
            let timeOfDay: AnyAppEnum? = try request.timeOfDay
            let seats: AnyAppEnum? = try request.seats
            #expect((keyword ?? "") == (expected.keyword ?? ""))
            #expect((credits ?? []) == expected.credits)
            #expect(day?.rawValue == expected.day)
            #expect(timeOfDay?.rawValue == expected.timeOfDay)
            #expect(seats?.rawValue == expected.seats)
        }
        return subject
    }

    var evaluators: Evaluators {
        SiriExpectationEvaluator()
    }

    func aggregateMetrics(using aggregator: inout MetricsAggregator) {
        Self.computeSiriMetrics(into: &aggregator)
    }
}

@Suite(.serialized)
struct SiriCourseSearchTests {
    static let bundleIdentifier: String = {
        guard let value = ProcessInfo.processInfo.environment["MPC_APP_BUNDLE_IDENTIFIER"], !value.isEmpty else {
            preconditionFailure("Run the Siri Integration scheme with its SiriIntegration test plan.")
        }
        return value
    }()

    private static let definitions = IntentDefinitions(bundleIdentifier: bundleIdentifier)
    fileprivate static let finder = definitions.intents["FindCoursesToolIntent"]
    private static let openSearch = definitions.intents["SearchCoursesInAppIntent"]
    private static let courseSearchSkill = definitions.skills["find-courses"]

    private static let keywordScenario = SiriScenario {
        UserTurn("用神奇松果找微積分課程，直接告訴我結果") {
            Calls { finder }
            Calls(.disallowed) { openSearch }
            Loads(.preferred) { courseSearchSkill }
        }
    }

    private static let followupScenario = SiriScenario {
        UserTurn("用神奇松果找微積分課程")
        UserTurn("只要三學分的，直接告訴我結果") {
            Calls { finder }
        }
    } expectation: {
        Calls(.disallowed) { openSearch }
        Loads(.preferred) { courseSearchSkill }
    }

    private static let filterScenarios: [SiriScenario] = [
        "用神奇松果找週三下午三學分而且還有名額的課，直接告訴我結果",
        "幫我在神奇松果看看禮拜三下午有哪些三學分的課還沒額滿"
    ].map { request in
        SiriScenario {
            UserTurn(request) {
                Calls { finder }
                Calls(.disallowed) { openSearch }
                Loads(.preferred) { courseSearchSkill }
            }
        }
    }

    @Test
    func scenariosCanBeSentToSiri() throws {
        for scenario in [Self.keywordScenario, Self.followupScenario] + Self.filterScenarios {
            _ = try JSONEncoder().encode(scenario)
        }
    }

    @Test(.evaluates(CourseSearchEvaluation(
        name: "神奇松果 keyword search",
        dataset: SiriScenarioLoader { keywordScenario },
        expected: ExpectedCourseQuery(keyword: "微積分")
    )))
    func findsCourses() async throws {
        let result = EvaluationContext.current.result
        #expect(result.aggregateValue(.mean(of: .intentsAllPass)) == 1)
    }

    @Test(.evaluates(CourseSearchEvaluation(
        name: "神奇松果 follow-up filters",
        dataset: SiriScenarioLoader { followupScenario },
        expected: ExpectedCourseQuery(keyword: "微積分", credits: [3])
    )))
    func refinesCourseSearch() async throws {
        let result = EvaluationContext.current.result
        #expect(result.aggregateValue(.mean(of: .intentsAllPass)) == 1)
    }

    @Test(.evaluates(CourseSearchEvaluation(
        name: "神奇松果 structured filters",
        dataset: SiriScenarioLoader { filterScenarios },
        expected: ExpectedCourseQuery(credits: [3], day: "wednesday", timeOfDay: "afternoon", seats: "open")
    )))
    func usesStructuredFiltersWithoutOpeningTextSearch() async throws {
        let result = EvaluationContext.current.result
        #expect(result.aggregateValue(.mean(of: .intentsAllPass)) == 1)
    }
}
#endif
