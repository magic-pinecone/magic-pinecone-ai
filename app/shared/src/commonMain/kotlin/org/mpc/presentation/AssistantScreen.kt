package org.mpc.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.icerock.moko.resources.compose.stringResource
import dev.zacsweers.metrox.viewmodel.metroViewModel
import org.mpc.domain.model.AssistantAnswer
import org.mpc.domain.model.AssistantKind
import org.mpc.presentation.state.AssistantUiState
import org.mpc.presentation.theme.MpcSpacing
import org.mpc.presentation.theme.MpcTheme
import org.mpc.presentation.viewModel.AssistantViewModel
import org.mpc.shared.MR
import org.mpc.shared.assistant_answer
import org.mpc.shared.assistant_ask
import org.mpc.shared.assistant_asking
import org.mpc.shared.assistant_based_on
import org.mpc.shared.assistant_example_bus
import org.mpc.shared.assistant_example_insurance
import org.mpc.shared.assistant_example_place
import org.mpc.shared.assistant_failed
import org.mpc.shared.assistant_intro
import org.mpc.shared.assistant_model_failed
import org.mpc.shared.assistant_no_model
import org.mpc.shared.assistant_nothing_found
import org.mpc.shared.assistant_question_hint
import org.mpc.shared.assistant_unsupported_numbers
import org.mpc.shared.intl_needs_backend
import org.mpc.shared.settings_course_source

private const val MAX_QUESTION_CHARS = 500

/**
 * One question to the backend's assistant. Shows the model's answer when the backend has a model, and always
 * what the backend looked up: a building with its map link, live bus arrivals, or passages of the OIA pages.
 */
@Composable
fun AssistantScreen(
    state: AssistantUiState,
    onAsk: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var question by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { text: String ->
        question = text
        // The answer appears where the keyboard is. Focus stays in the field: clearing it lets a hardware Enter
        // travel on to another control.
        keyboard?.hide()
        onAsk(text)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(MpcSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
    ) {
        item {
            QuestionBox(
                question = question,
                onQuestionChange = { question = it.take(MAX_QUESTION_CHARS) },
                asking = state == AssistantUiState.Asking,
                onAsk = submit,
            )
        }
        when (state) {
            AssistantUiState.Idle -> Unit

            AssistantUiState.Asking -> item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    AssistantNotice(stringResource(MR.strings.assistant_asking))
                }
            }

            AssistantUiState.NeedsBackend -> item {
                AssistantNotice(stringResource(MR.strings.intl_needs_backend, stringResource(MR.strings.settings_course_source)))
            }

            AssistantUiState.Failed -> item { AssistantNotice(stringResource(MR.strings.assistant_failed)) }

            is AssistantUiState.Answered -> answerItems(state.answer, showQuestion = state.answer.question != question.trim())
        }
    }
}

/** The assistant of the backend set in Settings. */
@Composable
fun AssistantRoute(modifier: Modifier = Modifier) {
    val viewModel: AssistantViewModel = metroViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AssistantScreen(state = state, onAsk = viewModel::ask, modifier = modifier)
}

@Composable
private fun QuestionBox(
    question: String,
    onQuestionChange: (String) -> Unit,
    asking: Boolean,
    onAsk: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MpcSpacing.space2)) {
        AssistantNotice(stringResource(MR.strings.assistant_intro))
        OutlinedTextField(
            value = question,
            onValueChange = onQuestionChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(MR.strings.assistant_question_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (!asking && question.isNotBlank()) onAsk(question) }),
            singleLine = true,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MpcSpacing.space2)) {
            listOf(
                MR.strings.assistant_example_place,
                MR.strings.assistant_example_bus,
                MR.strings.assistant_example_insurance,
            ).forEach { example ->
                val text = stringResource(example)
                SuggestionChip(onClick = { onAsk(text) }, label = { Text(text) }, enabled = !asking)
            }
        }
        Button(onClick = { onAsk(question) }, enabled = !asking && question.isNotBlank()) {
            Text(stringResource(MR.strings.assistant_ask))
        }
    }
}

private fun LazyListScope.answerItems(
    answer: AssistantAnswer,
    showQuestion: Boolean,
) {
    // Live bus arrivals are shown as cards and never written up by the model, so a bus question often has no
    // written answer. That is not a failure: the cards, or the notice why there are none, say everything.
    val busOnly = answer.kind == AssistantKind.BUS && answer.passages.isEmpty() && answer.answer.isNullOrBlank() && answer.error == null
    if (!busOnly) item { AnswerCard(answer, showQuestion) }
    answer.busProblem?.let { problem -> item { AssistantNotice(assistantBusProblemText(problem)) } }
    items(answer.places, key = { "place-${it.id}" }) { AssistantPlaceCard(it) }
    items(answer.destinations, key = { "destination-${it.id}" }) { AssistantDestinationCard(it) }
    items(answer.bus, key = { "bus-${it.route}" }) { AssistantBusCard(it) }
    if (answer.passages.isNotEmpty()) {
        item { Subheading(stringResource(MR.strings.assistant_based_on)) }
        items(answer.passages.size) { index -> AssistantPassageCard(index + 1, answer.passages[index]) }
    }
}

@Composable
private fun AnswerCard(
    answer: AssistantAnswer,
    showQuestion: Boolean,
) {
    val text = answer.answer
    GraduationLanguageCard {
        // Once the question box has moved on, the answer says what it answers.
        if (showQuestion) AssistantNotice(answer.question)
        when {
            !text.isNullOrBlank() -> {
                AssistantCardTitle(stringResource(MR.strings.assistant_answer))
                SelectionContainer { Text(text.asBullets(), style = MpcTheme.typography.body, color = MpcTheme.colors.label) }
                val unsupported = answer.checks?.unsupportedNumbers.orEmpty()
                if (unsupported.isNotEmpty()) {
                    Text(
                        stringResource(MR.strings.assistant_unsupported_numbers, unsupported.joinToString(", ")),
                        style = MpcTheme.typography.caption,
                        color = MpcTheme.colors.destructive,
                    )
                }
            }

            answer.error != null -> AssistantNotice(stringResource(MR.strings.assistant_model_failed))

            answer.model == null -> AssistantNotice(stringResource(MR.strings.assistant_no_model))

            else -> AssistantNotice(stringResource(MR.strings.assistant_nothing_found))
        }
    }
}

/** The model answers in lines that start with "- "; shown with a bullet instead of the raw dash. */
private fun String.asBullets(): String = lines().joinToString("\n") { line ->
    val trimmed = line.trimStart()
    if (trimmed.startsWith("- ")) line.take(line.length - trimmed.length) + "• " + trimmed.drop(2) else line
}
