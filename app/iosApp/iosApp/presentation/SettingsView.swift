import Shared
import SwiftUI

struct SettingsView: View {
    let sharedHost: IosSharedHost
    let appLanguageTag: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SettingsComposeView(sharedHost: sharedHost)
            .id(appLanguageTag)
            .ignoresSafeArea(.container, edges: .bottom)
            .navigationTitle(MR.strings().shell_settings.localized)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(MR.strings().common_close.localized, systemImage: "xmark") {
                        dismiss()
                    }
                }
            }
    }
}

private struct SettingsComposeView: UIViewControllerRepresentable {
    let sharedHost: IosSharedHost

    func makeUIViewController(context: Context) -> some UIViewController {
        sharedHost.settingsScreenController()
    }

    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
    }
}
