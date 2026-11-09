import AppIntents
import CoreSpotlight
@preconcurrency import Shared

/// Puts the Course Catalog and Portal services in Spotlight, where Siri also finds them.
enum SpotlightIndexer {
    private static let index = CSSearchableIndex(name: "MagicPinecone_Entities")

    /// Replaces each entity type's entries, so rerunning converges on the current catalog.
    static func indexAll(sharedHost: IosSharedHost) async {
        await replace(PortalShortcutEntity.all)
        let semester = CourseIdentity.currentSemester
        // Awaiting the catalog also loads it when it is not stored yet.
        guard let courses = try? await sharedHost.findCourses(
            semester: semester,
            keyword: "",
            filters: [],
            limit: Int32.max
        ), !courses.isEmpty else { return }
        await replace(courses.map { CourseEntity(semester: semester, course: $0) })
    }

    private static func replace<Entity: IndexedEntity>(_ entities: [Entity]) async {
        do {
            try await index.deleteAppEntities(ofType: Entity.self)
            try await index.indexAppEntities(entities, priority: 0)
        } catch {
            // Spotlight is a convenience; the app works the same without it.
        }
    }
}
