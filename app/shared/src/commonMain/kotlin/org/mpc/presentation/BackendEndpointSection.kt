package org.mpc.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import dev.icerock.moko.resources.compose.localized
import dev.icerock.moko.resources.compose.stringResource
import org.mpc.presentation.state.BackendEndpointUiState
import org.mpc.presentation.state.BackendSyncError
import org.mpc.presentation.state.BackendSyncState
import org.mpc.presentation.state.message
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.shared.MR
import org.mpc.shared.settings_backend_address
import org.mpc.shared.settings_connect_sync
import org.mpc.shared.settings_source_current
import org.mpc.shared.settings_source_static_current
import org.mpc.shared.settings_use_static

/** Lets the user point the course catalog at a Magic Pinecone backend, or back at the static catalog. */
@Composable
fun BackendEndpointSection(
    state: BackendEndpointUiState,
    onUseSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var address by rememberSaveable(state.baseUrl) { mutableStateOf(state.baseUrl.orEmpty()) }
    val syncing = state.sync == BackendSyncState.Syncing

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
    ) {
        Text(
            text = if (state.baseUrl == null) {
                stringResource(MR.strings.settings_source_static_current)
            } else {
                stringResource(MR.strings.settings_source_current, state.baseUrl)
            },
            style = MpcTheme.typography.supporting,
            color = MpcTheme.colors.label,
        )
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(MR.strings.settings_backend_address)) },
            placeholder = { Text("http://10.0.2.2:8000") },
            singleLine = true,
            enabled = !syncing,
            isError = state.sync == BackendSyncState.Failed(BackendSyncError.INVALID_ADDRESS),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2)) {
            Button(
                onClick = { onUseSource(address) },
                enabled = !syncing && address.isNotBlank(),
            ) {
                Text(stringResource(MR.strings.settings_connect_sync))
            }
            TextButton(
                onClick = { onUseSource("") },
                enabled = !syncing && state.baseUrl != null,
            ) {
                Text(stringResource(MR.strings.settings_use_static))
            }
        }
        state.sync.message()?.localized()?.let { message ->
            Text(
                text = message,
                style = MpcTheme.typography.caption,
                color = if (state.sync is BackendSyncState.Failed) MpcTheme.colors.destructive else MpcTheme.colors.labelSecondary,
            )
        }
    }
}
