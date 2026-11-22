package org.mpc.presentation.translation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mpc.presentation.theme.MpcTheme
import org.mpc.shared.MR
import org.mpc.shared.common_show_original
import org.mpc.shared.common_translate
import org.mpc.shared.common_translated_on_device
import org.mpc.shared.common_translation_failed

/** Translates campus text, which is Chinese, into the reader's language. */
fun interface TextTranslator {
    /** Returns one translation per text, in order. */
    suspend fun translate(texts: List<String>): List<String>
}

/** The translator into the reader's language, or null when the reader reads Chinese or the platform cannot translate. */
val LocalTextTranslator = staticCompositionLocalOf<TextTranslator?> { null }

/** Translates a screen's texts when asked, and keeps the result so switching back and forth is instant. */
class TextTranslation internal constructor(
    private val translator: TextTranslator,
    private val texts: List<String>,
    private val scope: CoroutineScope,
) {
    private var translations by mutableStateOf<Map<String, String>?>(null)
    var showsTranslation by mutableStateOf(false)
        private set
    var isTranslating by mutableStateOf(false)
        private set

    /** Why the last attempt failed, or null when it succeeded or hasn't run. */
    var failure by mutableStateOf<Exception?>(null)
        private set

    /** [original] in the reader's language while the translation is switched on, otherwise [original] itself. */
    fun text(original: String): String = translations?.get(original)?.takeIf { showsTranslation } ?: original

    fun toggle() {
        when {
            showsTranslation -> showsTranslation = false
            translations != null -> showsTranslation = true
            else -> scope.launch { translate() }
        }
    }

    private suspend fun translate() {
        isTranslating = true
        failure = null
        try {
            translations = texts.zip(translator.translate(texts)).toMap()
            showsTranslation = true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            // Platform translators report a failed model download or translation as a plain Exception.
            failure = error
        } finally {
            isTranslating = false
        }
    }
}

/**
 * Translation for [texts], or null when there is no translator into the reader's language or no Chinese to translate.
 * Texts without Chinese, such as ones the backend already translated, are skipped; [TextTranslation.text] returns them unchanged.
 */
@Composable
fun rememberTextTranslation(
    texts: List<String>,
): TextTranslation? {
    val translator = LocalTextTranslator.current ?: return null
    val scope = rememberCoroutineScope()
    return remember(translator, texts) {
        texts.filter(::hasChinese).distinct().takeIf { it.isNotEmpty() }?.let { TextTranslation(translator, it, scope) }
    }
}

@Composable
fun TranslateButton(
    translation: TextTranslation,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = translation::toggle, enabled = !translation.isTranslating, modifier = modifier) {
        if (translation.isTranslating) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            text = stringResource(if (translation.showsTranslation) MR.strings.common_show_original else MR.strings.common_translate),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Says the text was machine translated, or that translating failed. */
@Composable
fun TranslationNote(
    translation: TextTranslation,
    modifier: Modifier = Modifier,
) {
    val note = when {
        translation.showsTranslation -> MR.strings.common_translated_on_device
        translation.failure != null -> MR.strings.common_translation_failed
        else -> return
    }
    Text(
        text = stringResource(note),
        style = MpcTheme.typography.supporting,
        color = MpcTheme.colors.labelSecondary,
        modifier = modifier,
    )
}

private val chineseCharacter = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]")

private fun hasChinese(text: String): Boolean = chineseCharacter.containsMatchIn(text)
