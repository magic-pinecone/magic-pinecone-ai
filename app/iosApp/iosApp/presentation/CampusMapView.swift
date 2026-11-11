import Shared
import SwiftUI

struct CampusMapView: View {
    let sharedHost: IosSharedHost
    let onOpenSettings: () -> Void

    var body: some View {
        CampusMapComposeView(sharedHost: sharedHost)
            .ignoresSafeArea(.container, edges: .bottom)
            .navigationTitle(MR.strings().shell_tab_map.localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(MR.strings().shell_settings.localized, systemImage: "gear", action: onOpenSettings)
                        .labelStyle(.iconOnly)
                }
            }
    }
}
