import AppIntentsTesting
import XCTest

final class SiriIntegrationTests: XCTestCase {
    private var app: XCUIApplication!
    private var definitions: IntentDefinitions!

    @MainActor
    override func setUpWithError() throws {
        continueAfterFailure = false
        let bundleIdentifier = try XCTUnwrap(ProcessInfo.processInfo.environment["MPC_APP_BUNDLE_IDENTIFIER"])
        app = XCUIApplication(bundleIdentifier: bundleIdentifier)
        app.launch()
        definitions = IntentDefinitions(bundleIdentifier: bundleIdentifier)
    }

    @MainActor
    func testStructuredCourseSearchAndEntityResolution() async throws {
        let finder = definitions.intents["FindCoursesIntent"]
        let initial = try await finder.makeIntent(keyword: "微積分").run()
        let candidates: [AnyAppEntity] = try initial.value
        let course = try XCTUnwrap(candidates.first, "The current catalog must contain 微積分 for this integration test.")
        let title: String = try course.title
        let credits: Double = try course.credits

        let filtered = try await finder.makeIntent(keyword: title, credits: [credits]).run()
        let courses: [AnyAppEntity] = try filtered.value
        XCTAssertFalse(courses.isEmpty, "A known course must survive its own title and credit filters.")
        for result in courses {
            let actualCredits: Double = try result.credits
            XCTAssertEqual(actualCredits, credits)
        }

        let resolved = try await definitions.entities["CourseEntity"]
            .entities(identifiers: [course.identifier.instanceIdentifier])
        XCTAssertEqual(resolved.count, 1)
        let resolvedTitle: String = try XCTUnwrap(resolved.first).title
        XCTAssertEqual(resolvedTitle, title)

        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "神奇松果 after structured intent execution"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    @MainActor
    func testPortalEntityLookup() async throws {
        let services = try await definitions.entities["PortalShortcutEntity"].allEntities()
        XCTAssertFalse(services.isEmpty)
    }

#if SIRI_AI_PREVIEW
    @MainActor
    func testGenerableCourseQueryReturnsMatchingCredits() async throws {
        let initial = try await definitions.intents["FindCoursesIntent"].makeIntent(keyword: "微積分").run()
        let candidates: [AnyAppEntity] = try initial.value
        let course = try XCTUnwrap(candidates.first)
        let title: String = try course.title
        let credits: Double = try course.credits
        let request = definitions.transientEntities["CourseSearchRequest"]
            .makeEntity(keyword: title, credits: [credits])
        let result = try await definitions.intents["FindCoursesToolIntent"].makeIntent(request: request).run()
        let courses: [AnyAppEntity] = try result.value
        XCTAssertFalse(courses.isEmpty)
        for course in courses {
            let actualCredits: Double = try course.credits
            XCTAssertEqual(actualCredits, credits)
        }
    }
#endif
}
