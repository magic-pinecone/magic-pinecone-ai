import AppIntents
import SwiftUI

extension View {
    /// Tells Siri which course this view shows, so a request like "這堂課什麼時候上？" can refer to it.
    @ViewBuilder
    func courseEntityContext(_ identity: CourseIdentity) -> some View {
        if #available(iOS 26.0, *) {
            appEntityIdentifier(EntityIdentifier(for: CourseEntity.self, identifier: identity.entityID))
        } else {
            self
        }
    }
}
