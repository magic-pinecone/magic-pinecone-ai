package org.mpc.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import org.mpc.domain.model.Degree
import org.mpc.domain.model.GraduationLanguage
import org.mpc.domain.model.GraduationLanguageRule
import org.mpc.domain.model.LocalizedText
import org.mpc.presentation.state.GraduationLanguageFilter
import org.mpc.presentation.state.GraduationLanguageUiState
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme

/** What the screen asks its owner to do. */
data class GraduationLanguageActions(
    val onSelectDegree: (Degree) -> Unit,
    val onSelectCollege: (String?) -> Unit,
    val onSelectCohort: (Int) -> Unit,
    val onRetry: () -> Unit,
)

/**
 * The language requirements for graduating, for international students. Labels follow the chosen
 * language; rules quoted from NCU's regulations stay in Chinese, the language they are written in.
 */
@Composable
fun GraduationLanguageScreen(
    state: GraduationLanguageUiState,
    actions: GraduationLanguageActions,
    modifier: Modifier = Modifier,
) {
    var english by rememberSaveable { mutableStateOf(true) }
    val text = if (english) EnglishLabels else ChineseLabels

    when (state) {
        GraduationLanguageUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        GraduationLanguageUiState.NeedsBackend -> Message(text.needsBackend, modifier)

        GraduationLanguageUiState.Failed -> Message(text.failed, modifier) {
            Button(onClick = actions.onRetry) { Text(text.retry) }
        }

        is GraduationLanguageUiState.Loaded -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(MpcSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
        ) {
            item {
                Header(state.requirements, text, english, onToggleLanguage = { english = !english })
            }
            item { Filters(state, actions, text, english) }
            if (state.rules.isEmpty()) {
                item { GraduationLanguageCard { Paragraph(text.noRule) } }
            }
            items(state.rules, key = { it.id }) { rule ->
                RuleCard(rule, state.requirements, text, english)
            }
            item { GeneralCards(state.requirements, state.filter.degree, text) }
            item {
                Text(
                    text = "${state.requirements.disclaimer.pick(english)}\n" +
                        "${text.verifiedOn} ${state.requirements.verifiedOn}",
                    style = MpcTheme.typography.caption,
                    color = MpcTheme.colors.labelSecondary,
                )
            }
        }
    }
}

@Composable
private fun Message(
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(MpcSpacing.space6),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MpcTheme.typography.body,
            color = MpcTheme.colors.labelSecondary,
            textAlign = TextAlign.Center,
        )
        action()
    }
}

@Composable
private fun Header(
    requirements: GraduationLanguage,
    text: GraduationLanguageLabels,
    english: Boolean,
    onToggleLanguage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text.title,
                style = MpcTheme.typography.screenTitle,
                color = MpcTheme.colors.label,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            FilterChip(selected = false, onClick = onToggleLanguage, label = { Text(text.otherLanguage) })
        }
        Paragraph(requirements.general.summary.pick(english))
    }
}

@Composable
private fun Filters(
    state: GraduationLanguageUiState.Loaded,
    actions: GraduationLanguageActions,
    text: GraduationLanguageLabels,
    english: Boolean,
) {
    val filter = state.filter
    Column(verticalArrangement = Arrangement.spacedBy(MpcSpacing.space1)) {
        ChipRow {
            Degree.entries.forEach { degree ->
                FilterChip(
                    selected = filter.degree == degree,
                    onClick = { actions.onSelectDegree(degree) },
                    label = { Text(text.degree(degree)) },
                )
            }
        }
        ChipRow {
            FilterChip(
                selected = filter.college == null,
                onClick = { actions.onSelectCollege(null) },
                label = { Text(text.allColleges) },
            )
            state.colleges.forEach { college ->
                FilterChip(
                    selected = filter.college == college.zh,
                    onClick = { actions.onSelectCollege(college.zh) },
                    label = { Text(college.pick(english)) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text.admissionYear,
                style = MpcTheme.typography.supporting,
                color = MpcTheme.colors.labelSecondary,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { actions.onSelectCohort(filter.cohort - 1) },
                enabled = filter.cohort > GraduationLanguageFilter.Cohorts.first,
            ) {
                Icon(Icons.Filled.Remove, contentDescription = text.earlierYear)
            }
            Text(
                text = text.cohort(filter.cohort),
                style = MpcTheme.typography.headline,
                color = MpcTheme.colors.label,
            )
            IconButton(
                onClick = { actions.onSelectCohort(filter.cohort + 1) },
                enabled = filter.cohort < GraduationLanguageFilter.Cohorts.last,
            ) {
                Icon(Icons.Filled.Add, contentDescription = text.laterYear)
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2),
    ) {
        content()
    }
}

@Composable
private fun RuleCard(
    rule: GraduationLanguageRule,
    requirements: GraduationLanguage,
    text: GraduationLanguageLabels,
    english: Boolean,
) {
    GraduationLanguageCard {
        Text(
            text = (rule.department ?: rule.college).pick(english),
            style = MpcTheme.typography.sectionTitle,
            color = MpcTheme.colors.label,
            modifier = Modifier.semantics { heading() },
        )
        if (rule.department != null) {
            Text(rule.college.pick(english), style = MpcTheme.typography.caption, color = MpcTheme.colors.labelSecondary)
        }
        rule.summary?.let { Paragraph(it.pick(english)) }
        rule.applyAt?.let { Paragraph("${text.applyAt} ${it.pick(english)}", secondary = true) }

        RuleScores(rule, requirements, text, english)
        Callout(text.internationalStudents, rule.internationalStudents)
        Bullets(text.alternatives, rule.alternatives)
        Bullets(text.notes, rule.notes)

        val sources = rule.sources.mapNotNull { id -> requirements.sources.firstOrNull { it.id == id } }
        if (sources.isNotEmpty()) {
            Subheading(text.sources)
            sources.forEach { SourceLink(it) }
        }
    }
}

@Composable
private fun RuleScores(
    rule: GraduationLanguageRule,
    requirements: GraduationLanguage,
    text: GraduationLanguageLabels,
    english: Boolean,
) {
    if (rule.english.isNotEmpty()) {
        Subheading(text.englishTests)
        rule.english.forEach { (test, thresholds) ->
            val name = requirements.tests[test]?.pick(english) ?: test
            thresholds.forEach { ScoreRow(name, listOfNotNull(it.value, it.note).joinToString("\n")) }
        }
    }
    if (rule.otherLanguages.isNotEmpty() || rule.otherLanguagesWithoutThreshold.isNotEmpty()) {
        Subheading(text.otherLanguages)
        rule.otherLanguages.forEach { ScoreRow("${it.language} · ${it.test}", it.requirement) }
        rule.otherLanguagesWithoutThreshold.forEach { Paragraph(it, secondary = true) }
    }
}

@Composable
private fun GeneralCards(
    requirements: GraduationLanguage,
    degree: Degree,
    text: GraduationLanguageLabels,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MpcSpacing.space3)) {
        val graduate = requirements.general.graduate
        if (degree == Degree.BACHELOR) {
            GraduationLanguageCard {
                Callout(text.internationalStudents, requirements.general.internationalStudents)
                Bullets(text.procedure, requirements.general.procedure)
                Bullets(text.alternatives, requirements.general.alternatives)
            }
        } else if (graduate != null) {
            GraduationLanguageCard {
                Callout(text.internationalStudents, graduate.internationalStudents)
                Bullets(text.noRuleFound, graduate.noRuleFound)
                Bullets(text.notChecked, graduate.notChecked)
            }
        }
        GraduationLanguageCard {
            Text(
                text = text.chinese,
                style = MpcTheme.typography.sectionTitle,
                color = MpcTheme.colors.label,
                modifier = Modifier.semantics { heading() },
            )
            Paragraph(requirements.chinese.summary.pick(text === EnglishLabels))
            Bullets(text.chineseRequirements, requirements.chinese.requirements)
            Bullets(text.notGraduationRequirements, requirements.chinese.notGraduationRequirements)
            Bullets(text.openQuestions, requirements.chinese.openQuestions)
        }
    }
}

internal fun LocalizedText.pick(english: Boolean): String = if (english) en else zh
