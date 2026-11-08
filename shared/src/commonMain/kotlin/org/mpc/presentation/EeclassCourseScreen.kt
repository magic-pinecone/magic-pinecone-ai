package org.mpc.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.presentation.model.EeclassCoursePage
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.viewModel.EeclassViewModel

/** How many items each section of the course overview shows before "see all". */
private const val OVERVIEW_LIMIT = 5

enum class EeclassCategory(val title: String, val emptyText: String) {
    BULLETINS("公告", "沒有公告"),
    MATERIALS("教材", "沒有教材"),
    HOMEWORK("作業", "沒有作業"),
}

/** One course's announcements, materials, and homework, up to a few of each. */
@Composable
fun EeclassCourseScreen(
    course: String,
    onOpenCategory: (EeclassCategory) -> Unit,
    onOpenBulletin: (number: String, title: String) -> Unit,
    onOpenPage: (title: String, path: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EeclassViewModel = metroViewModel(),
) {
    val page = rememberCoursePage(viewModel, course)
    EeclassPageContent(
        status = page?.status,
        unavailableTitle = "無法載入課程內容",
        onRefresh = { viewModel.loadCoursePage(course) },
        modifier = modifier,
    ) {
        val loaded = page ?: return@EeclassPageContent
        LazyColumn {
            EeclassCategory.entries.forEach { category ->
                val rows = loaded.rows(category, onOpenBulletin, onOpenPage)
                item(key = category) { EeclassSectionHeader(category.title) }
                categoryRows(category, rows.take(OVERVIEW_LIMIT))
                if (rows.size > OVERVIEW_LIMIT) {
                    item(key = "all-$category") {
                        ListItem(
                            headlineContent = {
                                Text("查看全部 ${rows.size} 項", color = MpcTheme.colors.accent)
                            },
                            modifier = Modifier.clickable { onOpenCategory(category) },
                        )
                    }
                }
            }
        }
    }
}

/** Every item ee-class lists in one category of a course. */
@Composable
fun EeclassCategoryScreen(
    course: String,
    category: EeclassCategory,
    onOpenBulletin: (number: String, title: String) -> Unit,
    onOpenPage: (title: String, path: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EeclassViewModel = metroViewModel(),
) {
    val page = rememberCoursePage(viewModel, course)
    EeclassPageContent(
        status = page?.status,
        unavailableTitle = "無法載入課程內容",
        onRefresh = { viewModel.loadCoursePage(course) },
        modifier = modifier,
    ) {
        val loaded = page ?: return@EeclassPageContent
        LazyColumn {
            categoryRows(category, loaded.rows(category, onOpenBulletin, onOpenPage))
        }
    }
}

@Composable
private fun rememberCoursePage(viewModel: EeclassViewModel, course: String): EeclassCoursePage? {
    val page by viewModel.coursePage.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, course) {
        if (viewModel.coursePage.value == null) viewModel.loadCoursePage(course)
    }
    return page
}

private class EeclassListRow(
    val title: String,
    val detail: String,
    val onClick: () -> Unit,
)

private fun EeclassCoursePage.rows(
    category: EeclassCategory,
    onOpenBulletin: (number: String, title: String) -> Unit,
    onOpenPage: (title: String, path: String) -> Unit,
): List<EeclassListRow> = when (category) {
    EeclassCategory.BULLETINS -> bulletins.map { EeclassListRow(it.title, it.detail) { onOpenBulletin(it.number, it.title) } }
    EeclassCategory.MATERIALS -> materials.map { EeclassListRow(it.title, it.detail) { onOpenPage(it.title, it.path) } }
    EeclassCategory.HOMEWORK -> homework.map { EeclassListRow(it.title, it.detail) { onOpenPage(it.title, it.path) } }
}

private fun LazyListScope.categoryRows(category: EeclassCategory, rows: List<EeclassListRow>) {
    if (rows.isEmpty()) {
        item(key = "empty-$category") {
            Text(
                text = category.emptyText,
                style = MpcTheme.typography.supporting,
                color = MpcTheme.colors.labelSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
    itemsIndexed(rows, key = { index, _ -> "$category-$index" }) { _, row ->
        ListItem(
            headlineContent = { Text(row.title) },
            supportingContent = row.detail.takeIf(String::isNotEmpty)?.let { { Text(it) } },
            modifier = Modifier.clickable(onClick = row.onClick),
        )
    }
}

@Composable
fun EeclassSectionHeader(title: String) {
    Text(
        text = title,
        style = MpcTheme.typography.label,
        color = MpcTheme.colors.accent,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
