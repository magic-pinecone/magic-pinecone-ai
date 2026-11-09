package org.mpc.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.viewModel.EeclassViewModel

/** One announcement: its title, ee-class's summary line, the body as rich text, and its attachments. */
@Composable
fun EeclassBulletinScreen(
    course: String,
    number: String,
    title: String,
    onOpenPage: (title: String, path: String) -> Unit,
    onOpenLink: (url: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EeclassViewModel = metroViewModel(),
) {
    val page by viewModel.bulletin.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, course, number) {
        if (viewModel.bulletin.value == null) viewModel.loadBulletin(course, number)
    }

    EeclassPageContent(
        status = page?.status,
        unavailableTitle = "無法載入公告",
        onRefresh = { viewModel.loadBulletin(course, number) },
        modifier = modifier,
    ) {
        val loaded = page ?: return@EeclassPageContent
        val openLink by rememberUpdatedState(onOpenLink)
        val body = remember(loaded.bodyHtml) {
            AnnotatedString.fromHtml(loaded.bodyHtml) { link -> (link as? LinkAnnotation.Url)?.let { openLink(it.url) } }
        }
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = title,
                    style = MpcTheme.typography.sectionTitle,
                    modifier = Modifier.semantics { heading() },
                )
                if (loaded.info.isNotEmpty()) {
                    Text(
                        text = loaded.info,
                        style = MpcTheme.typography.supporting,
                        color = MpcTheme.colors.labelSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (body.isNotBlank()) {
                    SelectionContainer {
                        Text(text = body, style = MpcTheme.typography.body, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
            if (loaded.attachments.isNotEmpty()) {
                EeclassSectionHeader("附件")
                loaded.attachments.forEach { attachment ->
                    ListItem(
                        headlineContent = { Text(attachment.title) },
                        modifier = Modifier.clickable { onOpenPage(attachment.title, attachment.path) },
                    )
                }
            }
        }
    }
}
