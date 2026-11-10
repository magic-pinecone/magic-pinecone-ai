package org.mpc

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.mpc.presentation.preview.WebPreviewApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { WebPreviewApp() }
}
