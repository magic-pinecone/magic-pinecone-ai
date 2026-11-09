package org.mpc.presentation.views.coursePlanning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.mpc.presentation.state.CourseSearchError
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme

@Composable
fun CourseSearchResultFailureView(
    modifier: Modifier,
    error: CourseSearchError,
    onRetry: () -> Unit = {},
) {
    Box(
        modifier = modifier.padding(MpcSpacing.space6),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = when (error) {
                    CourseSearchError.NETWORK -> "無法連線載入課程，請檢查網路後重新搜尋。"
                    CourseSearchError.INVALID_SEMESTER -> "目前尚未提供此學期的課程。"
                    CourseSearchError.UNKNOWN -> "課程載入失敗，請重新搜尋。"
                },
                style = MpcTheme.typography.supporting,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(MpcSpacing.space3))
            Button(onClick = onRetry) {
                Text("重新載入")
            }
        }
    }
}
