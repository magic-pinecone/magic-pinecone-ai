package org.mpc.bridge

import androidx.compose.runtime.snapshotFlow
import com.mohamedrejeb.calf.ui.web.WebViewNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import platform.UIKit.UIViewController

/** One in-app browser page: the page itself, and its history for the native toolbar. */
class PortalWebPageBridge internal constructor(
    scope: CoroutineScope = MainScope(),
    createController: (WebViewNavigator) -> UIViewController,
) {
    private val navigator = WebViewNavigator(scope)

    // SwiftUI may build and drop bridges while it diffs views, so the page is created on first use.
    val controller: UIViewController by lazy { createController(navigator) }

    val canGoBack: Flow<Boolean> = snapshotFlow { navigator.canGoBack }

    fun goBack() = navigator.navigateBack()
}
