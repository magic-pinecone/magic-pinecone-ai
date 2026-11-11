import Shared
import SwiftUI

struct CampusMapView: View {
    let sharedHost: IosSharedHost

    var body: some View {
        CampusMapComposeView(sharedHost: sharedHost)
            .ignoresSafeArea(.container, edges: .bottom)
            .navigationTitle(MR.strings().shell_tab_map.localized)
            .navigationBarTitleDisplayMode(.inline)
    }
}
