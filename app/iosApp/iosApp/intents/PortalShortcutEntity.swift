import AppIntents
import CoreSpotlight
@preconcurrency import Shared

/// A Portal service from the app's Portal shortcut catalog, identified by its destination URL.
struct PortalShortcutEntity: IndexedEntity {
    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "Portal 服務")
    static let defaultQuery = PortalShortcutEntityQuery()

    let id: String

    @Property(title: "名稱")
    var label: String

    @Property(title: "分類")
    var section: String

    let pageTitle: String
    let systemImage: String

    init(item: PortalShortcutItem, section: PortalShortcutSection) {
        id = item.id
        pageTitle = item.destination.title
        systemImage = item.icon.systemImage
        label = item.label
        self.section = section.title
    }

    var destination: PortalDestination { PortalDestination(title: pageTitle, url: id) }

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(
            title: "\(label)",
            subtitle: "Portal · \(section)",
            image: .init(systemName: systemImage)
        )
    }

    var attributeSet: CSSearchableItemAttributeSet {
        let attributes = defaultAttributeSet
        attributes.keywords = ["Portal", section, pageTitle]
        return attributes
    }

    static var all: [PortalShortcutEntity] {
        PortalShortcutCatalogKt.defaultPortalShortcutSections.flatMap { section in
            section.items.map { PortalShortcutEntity(item: $0, section: section) }
        }
    }
}

struct PortalShortcutEntityQuery: EnumerableEntityQuery, EntityStringQuery {
    func allEntities() async throws -> [PortalShortcutEntity] {
        PortalShortcutEntity.all
    }

    func entities(for identifiers: [PortalShortcutEntity.ID]) async throws -> [PortalShortcutEntity] {
        let wanted = Set(identifiers)
        return PortalShortcutEntity.all.filter { wanted.contains($0.id) }
    }

    func entities(matching string: String) async throws -> [PortalShortcutEntity] {
        let needle = string.trimmingCharacters(in: .whitespaces)
        return PortalShortcutEntity.all.filter {
            $0.label.localizedCaseInsensitiveContains(needle)
                || $0.section.localizedCaseInsensitiveContains(needle)
        }
    }

    func suggestedEntities() async throws -> [PortalShortcutEntity] {
        PortalShortcutEntity.all
    }
}
