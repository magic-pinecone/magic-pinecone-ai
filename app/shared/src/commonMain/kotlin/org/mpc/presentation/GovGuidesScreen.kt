package org.mpc.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import dev.icerock.moko.resources.compose.stringResource
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.domain.model.GovGuideArticle
import org.mpc.domain.model.GovGuideShelf
import org.mpc.domain.model.GovGuides
import org.mpc.domain.model.GraduationLanguageSource
import org.mpc.domain.model.neighbours
import org.mpc.domain.model.readable
import org.mpc.domain.model.shelves
import org.mpc.presentation.state.GovGuidesUiState
import org.mpc.presentation.theme.MpcShapes
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.viewModel.AppSettingsViewModel
import org.mpc.presentation.viewModel.GovGuidesViewModel
import org.mpc.shared.MR
import org.mpc.shared.intl_guides_back
import org.mpc.shared.intl_guides_clear_search
import org.mpc.shared.intl_guides_failed
import org.mpc.shared.intl_guides_fetched_on
import org.mpc.shared.intl_guides_intro
import org.mpc.shared.intl_guides_machine_translated
import org.mpc.shared.intl_guides_next
import org.mpc.shared.intl_guides_no_match
import org.mpc.shared.intl_guides_open_original
import org.mpc.shared.intl_guides_previous
import org.mpc.shared.intl_guides_search
import org.mpc.shared.intl_guides_service
import org.mpc.shared.intl_guides_show_original
import org.mpc.shared.intl_guides_show_translation
import org.mpc.shared.intl_guides_topic_help
import org.mpc.shared.intl_guides_topic_insurance
import org.mpc.shared.intl_guides_topic_residence
import org.mpc.shared.intl_guides_topic_work
import org.mpc.shared.intl_needs_backend
import org.mpc.shared.intl_retry
import org.mpc.shared.intl_section_guides
import org.mpc.shared.settings_course_source

/** Widths from which the library shows its index beside the open article instead of one at a time. */
private val TWO_PANE_MIN_WIDTH = 720.dp
private val INDEX_PANE_WIDTH = 320.dp

/**
 * A small library of government pages for international students. An index groups the guides by topic, with
 * search and collapsible topics; the reader shows one guide's text with its source and Previous / Next. Wide
 * windows show both side by side; narrow ones show the index, then the guide. Online services open in the browser.
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

        is GovGuidesUiState.Loaded -> GuideLibrary(state.guides, modifier)
    }
}

/** The guides from the backend, in the language chosen in Settings. */
@Composable
fun GovGuidesRoute(modifier: Modifier = Modifier) {
    val viewModel: GovGuidesViewModel = metroViewModel()
    val settingsViewModel: AppSettingsViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    // SYSTEM has an empty tag and means the device language.
    LaunchedEffect(settings.language) { viewModel.setLanguage(settings.language.tag.ifEmpty { null }) }
    GovGuidesScreen(state = state, onRetry = viewModel::refresh, modifier = modifier)
}

@Composable
private fun GuideLibrary(
    guides: GovGuides,
    modifier: Modifier,
) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    // Collapsing is a convenience of the moment, so it is not kept across configuration changes.
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    val shelves = remember(guides, query) { guides.shelves(query) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val twoPane = maxWidth >= TWO_PANE_MIN_WIDTH
        // Side by side there is always room for a guide, so the first one opens with the library.
        val open = guides.articles.firstOrNull { it.id == openId } ?: guides.readable().firstOrNull().takeIf { twoPane }
        val index = @Composable { indexModifier: Modifier ->
            GuideIndex(
                shelves = shelves,
                query = query,
                onQueryChange = { query = it },
                collapsed = collapsed,
                onToggleTopic = { topic -> collapsed = if (topic in collapsed) collapsed - topic else collapsed + topic },
                selectedId = open?.id.takeIf { twoPane },
                onOpen = { openId = it.id },
                modifier = indexModifier,
            )
        }
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                index(Modifier.width(INDEX_PANE_WIDTH).fillMaxHeight())
                VerticalDivider(color = MpcTheme.colors.separator)
                open?.let { GuideReader(guides, it, onOpen = { next -> openId = next.id }, onBack = null, Modifier.weight(1f)) }
            }
        } else if (open == null) {
            index(Modifier.fillMaxSize())
        } else {
            // System back (and the iOS edge swipe) returns to the index instead of leaving the tab.
            NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = { openId = null })
            GuideReader(guides, open, onOpen = { next -> openId = next.id }, onBack = { openId = null }, Modifier.fillMaxSize())
        }
    }
}

// Stateless: the library owns search, collapse and selection, and passes each in with its callback.
@Suppress("LongParameterList")
@Composable
private fun GuideIndex(
    shelves: List<GovGuideShelf>,
    query: String,
    onQueryChange: (String) -> Unit,
    collapsed: Set<String>,
    onToggleTopic: (String) -> Unit,
    selectedId: String?,
    onOpen: (GovGuideArticle) -> Unit,
    modifier: Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val searching = query.isNotBlank()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(MpcSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space1),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2)) {
                Text(
                    text = stringResource(MR.strings.intl_section_guides),
                    style = MpcTheme.typography.screenTitle,
                    color = MpcTheme.colors.label,
                    modifier = Modifier.semantics { heading() },
                )
                Paragraph(stringResource(MR.strings.intl_guides_intro), secondary = true)
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(bottom = MpcSpacing.space2),
                    placeholder = { Text(stringResource(MR.strings.intl_guides_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searching) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(MR.strings.intl_guides_clear_search))
                            }
                        }
                    },
                    singleLine = true,
                )
            }
        }
        if (shelves.isEmpty()) {
            item(key = "no-match") { Paragraph(stringResource(MR.strings.intl_guides_no_match, query.trim()), secondary = true) }
        }
        shelves.forEach { shelf ->
            // A search shows every match, whatever was collapsed before.
            val expanded = searching || shelf.topic !in collapsed
            item(key = "topic-${shelf.topic}") {
                TopicRow(topicLabel(shelf.topic), shelf.articles.size, expanded, onClick = { onToggleTopic(shelf.topic) })
            }
            if (expanded) {
                items(shelf.articles, key = { it.id }) { article ->
                    ArticleRow(
                        article = article,
                        selected = article.id == selectedId,
                        onClick = { if (article.isService) uriHandler.openUri(article.url) else onOpen(article) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicRow(
    label: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MpcShapes.badge)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = MpcSpacing.space2)
            .semantics { heading() },
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MpcTheme.colors.labelSecondary,
        )
        Text(label, style = MpcTheme.typography.label, color = MpcTheme.colors.label, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MpcTheme.typography.caption, color = MpcTheme.colors.labelSecondary)
    }
}

@Composable
private fun ArticleRow(
    article: GovGuideArticle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MpcShapes.badge)
            .background(if (selected) MpcTheme.colors.accentContainer else MpcTheme.colors.background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = MpcSpacing.space6, end = MpcSpacing.space2, top = MpcSpacing.space2, bottom = MpcSpacing.space2),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val textColor = if (selected) MpcTheme.colors.onAccentContainer else MpcTheme.colors.label
        Column(Modifier.weight(1f)) {
            Text(article.title, style = MpcTheme.typography.supporting, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(article.org, stringResource(MR.strings.intl_guides_service).takeIf { article.isService }).joinToString(" · "),
                style = MpcTheme.typography.caption,
                color = if (selected) MpcTheme.colors.onAccentContainer else MpcTheme.colors.labelSecondary,
            )
        }
        if (article.isService) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MpcTheme.colors.accent)
        }
    }
}

@Composable
private fun GuideReader(
    guides: GovGuides,
    article: GovGuideArticle,
    onOpen: (GovGuideArticle) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier,
) {
    val (previous, next) = remember(guides, article.id) { guides.neighbours(article.id) }
    // A new guide starts at its top, not at the previous guide's scroll position.
    key(article.id) {
        var showOriginal by rememberSaveable { mutableStateOf(false) }
        val original = article.original?.takeIf { showOriginal }
        val title = original?.title ?: article.title
        val org = original?.org ?: article.org
        val body = original?.body ?: article.body
        LazyColumn(
            modifier = modifier,
            state = rememberLazyListState(),
            contentPadding = PaddingValues(MpcSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
        ) {
            if (onBack != null) {
                item {
                    TextButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Text(stringResource(MR.strings.intl_guides_back), modifier = Modifier.padding(start = MpcSpacing.space1))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(MpcSpacing.space1)) {
                    Text(
                        text = topicLabel(article.topic),
                        style = MpcTheme.typography.caption,
                        color = MpcTheme.colors.accent,
                    )
                    Text(
                        text = title,
                        style = MpcTheme.typography.screenTitle,
                        color = MpcTheme.colors.label,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "$org · ${stringResource(MR.strings.intl_guides_fetched_on, article.fetchedOn)}",
                        style = MpcTheme.typography.caption,
                        color = MpcTheme.colors.labelSecondary,
                    )
                }
            }
            if (article.original != null) {
                item { TranslationNotice(showOriginal, onToggle = { showOriginal = !showOriginal }) }
            }
            item {
                GraduationLanguageCard {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2)) {
                            body.split("\n").forEach { Paragraph(it) }
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
            item { PreviousNext(previous, next, onOpen) }
        }
    }
}

/** Says the guide is a machine translation and switches between it and the agency's own wording. */
@Composable
private fun TranslationNotice(
    showOriginal: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MpcShapes.badge)
            .background(MpcTheme.colors.accentContainer)
            .padding(start = MpcSpacing.space3),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Translate, contentDescription = null, tint = MpcTheme.colors.onAccentContainer)
        Text(
            text = stringResource(MR.strings.intl_guides_machine_translated),
            style = MpcTheme.typography.caption,
            color = MpcTheme.colors.onAccentContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onToggle) {
            Text(stringResource(if (showOriginal) MR.strings.intl_guides_show_translation else MR.strings.intl_guides_show_original))
        }
    }
}

@Composable
private fun PreviousNext(
    previous: GovGuideArticle?,
    next: GovGuideArticle?,
    onOpen: (GovGuideArticle) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space3)) {
        Box(Modifier.weight(1f)) {
            previous?.let { NeighbourButton(stringResource(MR.strings.intl_guides_previous), it, forward = false, onOpen) }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            next?.let { NeighbourButton(stringResource(MR.strings.intl_guides_next), it, forward = true, onOpen) }
        }
    }
}

@Composable
private fun NeighbourButton(
    label: String,
    article: GovGuideArticle,
    forward: Boolean,
    onOpen: (GovGuideArticle) -> Unit,
) {
    OutlinedButton(onClick = { onOpen(article) }, modifier = Modifier.fillMaxWidth()) {
        if (!forward) Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = MpcSpacing.space2),
            horizontalAlignment = if (forward) Alignment.End else Alignment.Start,
        ) {
            Text(label, style = MpcTheme.typography.caption)
            Text(article.title, style = MpcTheme.typography.supporting, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (forward) Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
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
