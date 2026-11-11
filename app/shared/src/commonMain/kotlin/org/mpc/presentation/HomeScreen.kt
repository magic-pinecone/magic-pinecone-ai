package org.mpc.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.stringResource
import dev.zacsweers.metrox.viewmodel.metroViewModel
import kotlinx.coroutines.launch
import org.mpc.presentation.model.EeclassLoadStatus
import org.mpc.presentation.model.InternationalPage
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.viewModel.EeclassViewModel
import org.mpc.shared.MR
import org.mpc.shared.eeclass_courses_empty
import org.mpc.shared.eeclass_courses_header
import org.mpc.shared.eeclass_courses_unavailable
import org.mpc.shared.eeclass_needs_portal_description
import org.mpc.shared.eeclass_needs_portal_title
import org.mpc.shared.eeclass_refresh
import org.mpc.shared.eeclass_try_again_later
import org.mpc.shared.intl_home_header

/** The ee-class courses, then the pages for international students, which stay reachable without signing in. */
@Composable
fun HomeScreen(
    onOpenCourse: (number: String, name: String) -> Unit,
    onOpenInternational: (InternationalPage) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EeclassViewModel = metroViewModel(),
) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        if (viewModel.courses.value == null) viewModel.loadCourses()
    }
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val refresh: () -> Unit = {
        scope.launch {
            isRefreshing = true
            try {
                viewModel.loadCourses()
            } finally {
                isRefreshing = false
            }
        }
    }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = refresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { EeclassSectionHeader(stringResource(MR.strings.eeclass_courses_header)) }
            val rows = courses?.courses.orEmpty()
            when (courses?.status) {
                null -> item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                EeclassLoadStatus.LOADED if rows.isEmpty() -> item {
                    ListItem(headlineContent = { Text(stringResource(MR.strings.eeclass_courses_empty)) })
                }

                EeclassLoadStatus.LOADED -> items(rows, key = { it.number }) { course ->
                    ListItem(
                        headlineContent = { Text(course.name) },
                        modifier = Modifier.clickable { onOpenCourse(course.number, course.name) },
                    )
                }

                EeclassLoadStatus.NEEDS_PORTAL -> item {
                    EeclassStatusItem(
                        title = MR.strings.eeclass_needs_portal_title,
                        description = MR.strings.eeclass_needs_portal_description,
                        onRetry = refresh,
                    )
                }

                EeclassLoadStatus.UNAVAILABLE -> item {
                    EeclassStatusItem(
                        title = MR.strings.eeclass_courses_unavailable,
                        description = MR.strings.eeclass_try_again_later,
                        onRetry = refresh,
                    )
                }
            }
            item { EeclassSectionHeader(stringResource(MR.strings.intl_home_header)) }
            items(InternationalPage.entries) { page ->
                ListItem(
                    headlineContent = { Text(stringResource(page.title)) },
                    leadingContent = { Icon(page.icon, contentDescription = null) },
                    modifier = Modifier.clickable { onOpenInternational(page) },
                )
            }
        }
    }
}

@Composable
private fun EeclassStatusItem(
    title: StringResource,
    description: StringResource,
    onRetry: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(description), color = MpcTheme.colors.labelSecondary) },
        trailingContent = { TextButton(onClick = onRetry) { Text(stringResource(MR.strings.eeclass_refresh)) } },
    )
}

private val InternationalPage.icon: ImageVector
    get() = when (this) {
        InternationalPage.GRADUATION_LANGUAGE -> Icons.Default.Translate
        InternationalPage.GOV_GUIDES -> Icons.Default.AccountBalance
        InternationalPage.SERVICE_LEARNING -> Icons.Default.VolunteerActivism
        InternationalPage.CREDITS -> Icons.Default.School
    }
