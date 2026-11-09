package org.mpc

import androidx.compose.ui.window.ComposeUIViewController
import org.mpc.presentation.AnnouncementScreen
import platform.UIKit.UIViewController

fun IosSharedHost.announcementScreenController(
    onOpenUrl: (title: String, url: String) -> Unit,
): UIViewController = ComposeUIViewController {
    ProvideAppDependencies(appGraph) {
        ThemedContent {
            AnnouncementScreen(onOpenUrl = onOpenUrl)
        }
    }
}
