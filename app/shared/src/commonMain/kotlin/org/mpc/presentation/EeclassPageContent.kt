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
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.launch
import org.mpc.presentation.model.EeclassLoadStatus
import org.mpc.presentation.theme.MpcTheme
import org.mpc.shared.MR
import org.mpc.shared.eeclass_needs_portal_description
import org.mpc.shared.eeclass_needs_portal_title
import org.mpc.shared.eeclass_refresh
import org.mpc.shared.eeclass_try_again_later

/**
 * Shows a spinner until [status] is known, then [content] for a loaded page or the reason it is missing.
 * Pulling down and the retry button both call [onRefresh].
 */
@Composable
fun EeclassPageContent(
    status: EeclassLoadStatus?,
    unavailableTitle: StringResource,
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
                title = MR.strings.eeclass_needs_portal_title,
                description = MR.strings.eeclass_needs_portal_description,
                onRetry = refresh,
            )

            EeclassLoadStatus.UNAVAILABLE -> EeclassMessage(
                title = unavailableTitle,
                description = MR.strings.eeclass_try_again_later,
                onRetry = refresh,
            )
        }
    }
}

/** A centered message. It scrolls so that pull to refresh still works. */
@Composable
internal fun EeclassMessage(
    title: StringResource,
    description: StringResource? = null,
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
        Text(text = stringResource(title), style = MpcTheme.typography.headline, textAlign = TextAlign.Center)
        if (description != null) {
            Text(
                text = stringResource(description),
                style = MpcTheme.typography.supporting,
                color = MpcTheme.colors.labelSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text(stringResource(MR.strings.eeclass_refresh)) }
        }
    }
}
