package org.mpc.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.icerock.moko.resources.compose.stringResource
import org.mpc.domain.model.GovGuideArticle
import org.mpc.domain.model.GraduationLanguageSource
import org.mpc.presentation.state.GovGuidesUiState
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.shared.MR
import org.mpc.shared.intl_guides_back
import org.mpc.shared.intl_guides_failed
import org.mpc.shared.intl_guides_fetched_on
import org.mpc.shared.intl_guides_intro
import org.mpc.shared.intl_guides_open_original
import org.mpc.shared.intl_guides_service
import org.mpc.shared.intl_guides_topic_help
import org.mpc.shared.intl_guides_topic_insurance
import org.mpc.shared.intl_guides_topic_residence
import org.mpc.shared.intl_guides_topic_work
import org.mpc.shared.intl_needs_backend
import org.mpc.shared.intl_retry
import org.mpc.shared.intl_section_guides
import org.mpc.shared.settings_course_source

/**
 * A small library of government pages for international students: the guides grouped by topic,
 * and one guide's text with a link to the agency's page. Online services open in the browser.
 */
@Composable
fun GovGuidesScreen(
    state: GovGuidesUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        GovGuidesUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        GovGuidesUiState.NeedsBackend -> Message(
            stringResource(MR.strings.intl_needs_backend, stringResource(MR.strings.settings_course_source)),
            modifier,
        )

        GovGuidesUiState.Failed -> Message(stringResource(MR.strings.intl_guides_failed), modifier) {
            Button(onClick = onRetry) { Text(stringResource(MR.strings.intl_retry)) }
        }

        is GovGuidesUiState.Loaded -> {
            var openId by rememberSaveable { mutableStateOf<String?>(null) }
            val open = state.guides.articles.firstOrNull { it.id == openId }
            if (open == null) {
                GuideList(state, onOpen = { openId = it.id }, modifier)
            } else {
                GuideArticle(open, onBack = { openId = null }, modifier)
            }
        }
    }
}

@Composable
private fun GuideList(
    state: GovGuidesUiState.Loaded,
    onOpen: (GovGuideArticle) -> Unit,
    modifier: Modifier,
) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(MpcSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2)) {
                Text(
                    text = stringResource(MR.strings.intl_section_guides),
                    style = MpcTheme.typography.screenTitle,
                    color = MpcTheme.colors.label,
                    modifier = Modifier.semantics { heading() },
                )
                Paragraph(stringResource(MR.strings.intl_guides_intro), secondary = true)
            }
        }
        state.guides.topics.forEach { topic ->
            item(key = "topic-$topic") { Subheading(topicLabel(topic)) }
            items(state.guides.articles.filter { it.topic == topic }, key = { it.id }) { article ->
                GuideRow(article, onClick = { if (article.isService) uriHandler.openUri(article.url) else onOpen(article) })
            }
        }
    }
}

@Composable
private fun GuideRow(
    article: GovGuideArticle,
    onClick: () -> Unit,
) {
    GraduationLanguageCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MpcSpacing.space1)) {
                Text(article.title, style = MpcTheme.typography.headline, color = MpcTheme.colors.label)
                Text(
                    text = listOfNotNull(article.org, stringResource(MR.strings.intl_guides_service).takeIf { article.isService })
                        .joinToString(" · "),
                    style = MpcTheme.typography.caption,
                    color = MpcTheme.colors.labelSecondary,
                )
            }
            if (article.isService) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MpcTheme.colors.accent)
            }
        }
    }
}

@Composable
private fun GuideArticle(
    article: GovGuideArticle,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    // System back (and the iOS edge swipe) returns to the list instead of leaving the tab.
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(MpcSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
    ) {
        item {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Text(stringResource(MR.strings.intl_guides_back), modifier = Modifier.padding(start = MpcSpacing.space1))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(MpcSpacing.space1)) {
                Text(
                    text = article.title,
                    style = MpcTheme.typography.screenTitle,
                    color = MpcTheme.colors.label,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "${article.org} · ${stringResource(MR.strings.intl_guides_fetched_on, article.fetchedOn)}",
                    style = MpcTheme.typography.caption,
                    color = MpcTheme.colors.labelSecondary,
                )
            }
        }
        item {
            GraduationLanguageCard {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2)) {
                        article.body.split("\n").forEach { Paragraph(it) }
                    }
                }
            }
        }
        item {
            SourceLink(
                GraduationLanguageSource(
                    id = article.id,
                    title = stringResource(MR.strings.intl_guides_open_original),
                    url = article.url,
                    org = article.org,
                ),
            )
        }
    }
}

@Composable
private fun topicLabel(topic: String): String = when (topic) {
    "work" -> stringResource(MR.strings.intl_guides_topic_work)

    "insurance" -> stringResource(MR.strings.intl_guides_topic_insurance)

    "residence" -> stringResource(MR.strings.intl_guides_topic_residence)

    "help" -> stringResource(MR.strings.intl_guides_topic_help)

    // A topic the app does not know yet still shows, under its id.
    else -> topic
}
