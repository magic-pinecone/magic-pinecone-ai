package org.mpc.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch
import org.mpc.data.portal.portalLoginDestination
import org.mpc.domain.model.PortalShortcutDestination
import org.mpc.domain.session.PortalSignIn
import org.mpc.presentation.model.PortalAccount
import org.mpc.presentation.model.PortalAccountAction
import org.mpc.presentation.model.PortalAccountTone
import org.mpc.presentation.model.toPortalAccount
import org.mpc.presentation.theme.MpcShapes
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.viewModel.CampusSessionViewModel

@Composable
internal fun PortalAccountCard(
    onOpenDestination: (PortalShortcutDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CampusSessionViewModel = metroViewModel(),
) {
    val status by viewModel.portal.collectAsStateWithLifecycle()
    val account = remember(status) { status.toPortalAccount() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.refreshPortal()
    }
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPortal()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PortalAccountRow(
        account = account,
        onAction = { action ->
            when (action) {
                PortalAccountAction.SIGN_IN -> scope.launch {
                    when (val signIn = viewModel.signIn()) {
                        is PortalSignIn.OpenBrowser -> onOpenDestination(portalLoginDestination(signIn.url))
                    }
                }

                PortalAccountAction.SIGN_OUT -> viewModel.signOut()

                PortalAccountAction.RETRY -> viewModel.refreshPortal()
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun PortalAccountRow(
    account: PortalAccount,
    onAction: (PortalAccountAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(account.title) },
        supportingContent = account.detail?.let { detail -> { Text(detail) } },
        leadingContent = { PortalAccountIcon(account.tone) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space1)) {
                account.actions.forEachIndexed { index, action ->
                    if (index == 0 && action == PortalAccountAction.SIGN_IN) {
                        Button(onClick = { onAction(action) }) { Text(action.label()) }
                    } else {
                        TextButton(onClick = { onAction(action) }) { Text(action.label()) }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MpcTheme.colors.surface),
        modifier = modifier.clip(MpcShapes.card),
    )
}

@Composable
private fun PortalAccountIcon(tone: PortalAccountTone) {
    val iconModifier = Modifier.size(28.dp)
    when (tone) {
        PortalAccountTone.CHECKING -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)

        PortalAccountTone.SIGNED_IN ->
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MpcTheme.colors.accent, modifier = iconModifier)

        PortalAccountTone.SIGNED_OUT ->
            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MpcTheme.colors.labelSecondary, modifier = iconModifier)

        PortalAccountTone.ATTENTION ->
            Icon(Icons.Default.Error, contentDescription = null, tint = MpcTheme.colors.destructive, modifier = iconModifier)
    }
}

private fun PortalAccountAction.label(): String = when (this) {
    PortalAccountAction.SIGN_IN -> "登入"
    PortalAccountAction.SIGN_OUT -> "登出"
    PortalAccountAction.RETRY -> "重試"
}
