package org.mpc.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.presentation.model.EeclassLoadStatus
import org.mpc.presentation.viewModel.EeclassViewModel

@Composable
fun EeclassHomeScreen(
    onOpenCourse: (number: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EeclassViewModel = metroViewModel(),
) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        if (viewModel.courses.value == null) viewModel.loadCourses()
    }

    EeclassPageContent(
        status = courses?.status,
        unavailableTitle = "無法載入 ee-class 課程",
        onRefresh = viewModel::loadCourses,
        modifier = modifier,
    ) {
        val rows = courses?.courses.orEmpty()
        if (rows.isEmpty()) {
            EeclassMessage(title = "沒有 ee-class 課程")
        } else {
            LazyColumn {
                item { EeclassSectionHeader("ee-class 課程") }
                items(rows, key = { it.number }) { course ->
                    ListItem(
                        headlineContent = { Text(course.name) },
                        modifier = Modifier.clickable { onOpenCourse(course.number, course.name) },
                    )
                }
            }
        }
    }
}
