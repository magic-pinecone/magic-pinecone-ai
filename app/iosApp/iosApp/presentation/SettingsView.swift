import Shared
import SwiftUI

struct SettingsView: View {
    let sharedHost: IosSharedHost
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        SettingsComposeView(sharedHost: sharedHost)
            .ignoresSafeArea(.container, edges: .bottom)
            .navigationTitle("設定")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("關閉", systemImage: "xmark") {
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
