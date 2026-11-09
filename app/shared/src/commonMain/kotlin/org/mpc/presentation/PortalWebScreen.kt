package org.mpc.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mohamedrejeb.calf.ui.web.WebView
import com.mohamedrejeb.calf.ui.web.WebViewNavigator
import com.mohamedrejeb.calf.ui.web.rememberWebViewState
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.domain.session.CampusSites
import org.mpc.domain.session.portalLaunchUrl
import org.mpc.presentation.viewModel.CampusSessionViewModel

/**
 * The in-app browser page. Back gestures leave the page; the host's toolbar uses [navigator]
 * for going back within it.
 */
@Composable
fun PortalWebScreen(
    url: String,
    navigator: WebViewNavigator,
) {
    val sessionViewModel: CampusSessionViewModel = metroViewModel()
    val webViewState = rememberWebViewState(url = url)
    val pageUrl = webViewState.lastLoadedUrl ?: url
    val isLoading = webViewState.isLoading
    val javascriptEnabled = CampusSites.allowsJavascript(url)
    if (webViewState.settings.javaScriptEnabled != javascriptEnabled) {
        webViewState.settings.javaScriptEnabled = javascriptEnabled
    }

    // Continue each launch once, so going back shows the confirmation instead of reopening the app.
    val continuedLaunches = remember { mutableSetOf<String>() }

    LaunchedEffect(isLoading, pageUrl) {
        sessionViewModel.browserPage(pageUrl, isLoading)
        val loadedUrl = webViewState.lastLoadedUrl
        if (!isLoading && loadedUrl != null) {
            portalLaunchUrl(loadedUrl)?.takeIf(continuedLaunches::add)?.let { navigator.loadUrl(it) }
        }
    }

    WebView(
        state = webViewState,
        modifier = Modifier.fillMaxSize(),
        captureBackPresses = false,
        navigator = navigator,
    )
}
