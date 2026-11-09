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
import org.mpc.presentation.state.BackendEndpointUiState
import org.mpc.presentation.state.BackendSyncError
import org.mpc.presentation.state.BackendSyncState
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme

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
            text = if (state.baseUrl == null) "目前使用：公開的靜態課程資料" else "目前使用：${state.baseUrl}",
            style = MpcTheme.typography.supporting,
            color = MpcTheme.colors.label,
        )
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("後端位址") },
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
                Text("連線並同步")
            }
            TextButton(
                onClick = { onUseSource("") },
                enabled = !syncing && state.baseUrl != null,
            ) {
                Text("改用靜態資料")
            }
        }
        state.sync.message()?.let { message ->
            Text(
                text = message,
                style = MpcTheme.typography.caption,
                color = if (state.sync is BackendSyncState.Failed) MpcTheme.colors.destructive else MpcTheme.colors.labelSecondary,
            )
        }
    }
}

private fun BackendSyncState.message(): String? = when (this) {
    BackendSyncState.Idle -> null
    BackendSyncState.Syncing -> "正在同步課程資料…"
    is BackendSyncState.Synced -> "已同步 $courseCount 門課程。已開啟的選課畫面會在重新啟動 App 後更新。"
    is BackendSyncState.Failed -> when (error) {
        BackendSyncError.INVALID_ADDRESS -> "請輸入以 http:// 或 https:// 開頭的位址。"
        BackendSyncError.SOURCE_UNAVAILABLE -> "無法從這個來源取得課程資料，已維持原本的資料來源。"
    }
}
