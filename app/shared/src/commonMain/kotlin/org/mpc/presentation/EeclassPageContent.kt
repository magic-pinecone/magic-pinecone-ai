package org.mpc.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mpc.presentation.model.EeclassLoadStatus
import org.mpc.presentation.theme.MpcTheme

/**
 * Shows a spinner until [status] is known, then [content] for a loaded page or the reason it is missing.
 * Pulling down and the retry button both call [onRefresh].
 */
@Composable
fun EeclassPageContent(
    status: EeclassLoadStatus?,
    unavailableTitle: String,
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (status == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val refresh: () -> Unit = {
        scope.launch {
            isRefreshing = true
            try {
                onRefresh()
            } finally {
                isRefreshing = false
            }
        }
    }
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = refresh, modifier = modifier.fillMaxSize()) {
        when (status) {
            EeclassLoadStatus.LOADED -> content()

            EeclassLoadStatus.NEEDS_PORTAL -> EeclassMessage(
                title = "需要登入 Portal",
                description = "在 Portal 分頁登入後再重新整理。",
                onRetry = refresh,
            )

            EeclassLoadStatus.UNAVAILABLE -> EeclassMessage(
                title = unavailableTitle,
                description = "請稍後再試。",
                onRetry = refresh,
            )
        }
    }
}

/** A centered message. It scrolls so that pull to refresh still works. */
@Composable
internal fun EeclassMessage(
    title: String,
    description: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MpcTheme.typography.headline, textAlign = TextAlign.Center)
        if (description != null) {
            Text(
                text = description,
                style = MpcTheme.typography.supporting,
                color = MpcTheme.colors.labelSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text("重新整理") }
        }
    }
}
