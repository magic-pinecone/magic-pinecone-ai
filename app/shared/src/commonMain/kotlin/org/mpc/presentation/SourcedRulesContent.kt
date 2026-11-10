package org.mpc.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.stringResource
import org.mpc.domain.model.RuleContact
import org.mpc.domain.model.RuleDegree
import org.mpc.domain.model.RuleSource
import org.mpc.domain.model.SourcedRule
import org.mpc.domain.model.SourcedRules
import org.mpc.domain.model.degrees
import org.mpc.domain.model.forDegree
import org.mpc.domain.model.formatCredits
import org.mpc.presentation.state.SourcedRulesUiState
import org.mpc.presentation.theme.MpcShapes
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.shared.MR
import org.mpc.shared.intl_needs_backend
import org.mpc.shared.intl_retry
import org.mpc.shared.rules_all_degrees
import org.mpc.shared.rules_contact
import org.mpc.shared.rules_credits
import org.mpc.shared.rules_credits_none
import org.mpc.shared.rules_degree_bachelor
import org.mpc.shared.rules_degree_graduate
import org.mpc.shared.rules_failed
import org.mpc.shared.rules_fetched_on
import org.mpc.shared.rules_hide
import org.mpc.shared.rules_not_confirmed
import org.mpc.shared.rules_not_confirmed_note
import org.mpc.shared.rules_show
import org.mpc.shared.rules_sources
import org.mpc.shared.settings_course_source

/** Chinese-language phones read the Chinese the university wrote; every other language reads the English. */
@Composable
internal fun prefersEnglish(): Boolean = Locale.current.language != "zh"

/** Loading, "needs a backend" and failure around [SourcedRulesContent]; a rule-set screen only adds its [title] and [intro]. */
@Composable
fun SourcedRulesScreen(
    state: SourcedRulesUiState,
    title: String,
    intro: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        SourcedRulesUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        SourcedRulesUiState.NeedsBackend -> Message(
            stringResource(MR.strings.intl_needs_backend, stringResource(MR.strings.settings_course_source)),
            modifier,
        )

        SourcedRulesUiState.Failed -> Message(stringResource(MR.strings.rules_failed), modifier) {
            Button(onClick = onRetry) { Text(stringResource(MR.strings.intl_retry)) }
        }

        is SourcedRulesUiState.Loaded -> SourcedRulesContent(state.rules, title, intro, modifier)
    }
}

/**
 * The rules with their official sources, the office to ask, and what is still unconfirmed. The degree filter
 * appears only when some rule names a degree.
 */
@Composable
fun SourcedRulesContent(
    rules: SourcedRules,
    title: String,
    intro: String,
    modifier: Modifier = Modifier,
    english: Boolean = prefersEnglish(),
) {
    var degree by rememberSaveable { mutableStateOf<RuleDegree?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(MpcSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
    ) {
        item {
            Text(
                text = title,
                style = MpcTheme.typography.screenTitle,
                color = MpcTheme.colors.label,
                modifier = Modifier.semantics { heading() },
            )
        }
        item { Paragraph(intro) }
        if (rules.degrees.isNotEmpty()) {
            item { DegreeFilter(rules.degrees, degree, onSelect = { degree = it }) }
        }
        items(rules.forDegree(degree), key = { it.id }) { rule -> SourcedRuleCard(rule, english) }
        item { RuleContactCard(rules.contact, english) }
        if (rules.openQuestions.isNotEmpty()) {
            item { OpenQuestionsCard(rules.openQuestions) }
        }
    }
}

@Composable
private fun DegreeFilter(
    degrees: List<RuleDegree>,
    selected: RuleDegree?,
    onSelect: (RuleDegree?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(MR.strings.rules_all_degrees)) },
        )
        degrees.forEach { degree ->
            FilterChip(
                selected = selected == degree,
                onClick = { onSelect(degree) },
                label = { Text(degree.label()) },
            )
        }
    }
}

@Composable
fun SourcedRuleCard(
    rule: SourcedRule,
    english: Boolean,
) {
    GraduationLanguageCard {
        Text(
            text = rule.title.pick(english),
            style = MpcTheme.typography.sectionTitle,
            color = MpcTheme.colors.label,
            modifier = Modifier.semantics { heading() },
        )
        val badges = rule.degrees.map { it.label() } + listOfNotNull(
            rule.credits?.let { credits ->
                if (credits == 0.0) {
                    stringResource(MR.strings.rules_credits_none)
                } else {
                    stringResource(MR.strings.rules_credits, formatCredits(credits))
                }
            },
        )
        if (badges.isNotEmpty()) {
            Text(badges.joinToString(" · "), style = MpcTheme.typography.caption, color = MpcTheme.colors.labelSecondary)
        }
        Paragraph(rule.summary.pick(english))
        RuleSources(rule.sources)
    }
}

@Composable
private fun RuleContactCard(
    contact: RuleContact,
    english: Boolean,
) {
    GraduationLanguageCard {
        Subheading(stringResource(MR.strings.rules_contact))
        Text(contact.name.pick(english), style = MpcTheme.typography.headline, color = MpcTheme.colors.label)
        LinkRow(contact.website, contact.website)
        RuleSources(contact.sources)
    }
}

@Composable
private fun OpenQuestionsCard(questions: List<String>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    GraduationLanguageCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(MR.strings.rules_not_confirmed),
                style = MpcTheme.typography.sectionTitle,
                color = MpcTheme.colors.label,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) MR.strings.rules_hide else MR.strings.rules_show),
                tint = MpcTheme.colors.labelSecondary,
            )
        }
        if (expanded) {
            Paragraph(stringResource(MR.strings.rules_not_confirmed_note), secondary = true)
            questions.forEach { Paragraph("• $it") }
        }
    }
}

@Composable
private fun RuleSources(sources: List<RuleSource>) {
    if (sources.isEmpty()) return
    Subheading(stringResource(MR.strings.rules_sources))
    sources.forEach { source ->
        // Quotes are copied from the official page, so they stay in the language it is written in.
        Paragraph("「${source.quote}」", secondary = true)
        LinkRow(source.title, source.url, detail = stringResource(MR.strings.rules_fetched_on, source.fetched))
    }
}

@Composable
private fun LinkRow(
    label: String,
    url: String,
    detail: String? = null,
) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MpcShapes.badge)
            .clickable { uriHandler.openUri(url) }
            .padding(vertical = MpcSpacing.space1),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = listOfNotNull(label, detail).joinToString(" · "),
            style = MpcTheme.typography.caption,
            color = MpcTheme.colors.accent,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MpcTheme.colors.accent)
    }
}

@Composable
private fun RuleDegree.label(): String = stringResource(degreeLabel(this))

private fun degreeLabel(degree: RuleDegree): StringResource = when (degree) {
    RuleDegree.BACHELOR -> MR.strings.rules_degree_bachelor
    RuleDegree.GRADUATE -> MR.strings.rules_degree_graduate
}
