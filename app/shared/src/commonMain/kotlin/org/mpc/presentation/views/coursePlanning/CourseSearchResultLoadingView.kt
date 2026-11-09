package org.mpc.presentation.views.coursePlanning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme

@Composable
fun CourseSearchResultLoadingView(modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Text("正在載入課程…", style = MpcTheme.typography.supporting)
        }
    }
}
