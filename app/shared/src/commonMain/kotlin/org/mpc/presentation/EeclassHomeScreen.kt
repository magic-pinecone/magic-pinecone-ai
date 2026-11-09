package org.mpc.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.viewModel.EeclassViewModel

enum class HomeFeedTab(val title: String) {
    ANNOUNCEMENTS("校園與系所公告"),
    EECLASS("ee-class 課程"),
}

@Composable
fun EeclassHomeScreen(
    onOpenCourse: (number: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenUrl: (title: String, url: String) -> Unit = { _, _ -> },
    viewModel: EeclassViewModel = metroViewModel(),
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeFeedTab.ANNOUNCEMENTS) }

    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MpcSpacing.space4, vertical = MpcSpacing.space2),
        ) {
            HomeFeedTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = HomeFeedTab.entries.size,
                    ),
                    label = { Text(tab.title) },
                )
            }
        }

        when (selectedTab) {
            HomeFeedTab.ANNOUNCEMENTS -> AnnouncementScreen(
                onOpenUrl = onOpenUrl,
                modifier = Modifier.weight(1f),
            )

            HomeFeedTab.EECLASS -> EeclassCoursesFeed(
                onOpenCourse = onOpenCourse,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EeclassCoursesFeed(
    onOpenCourse: (number: String, name: String) -> Unit,
    viewModel: EeclassViewModel,
    modifier: Modifier = Modifier,
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
