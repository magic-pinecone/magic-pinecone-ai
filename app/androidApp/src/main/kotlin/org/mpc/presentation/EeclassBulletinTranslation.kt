package org.mpc.presentation

import android.util.Log
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mpc.presentation.theme.MpcTheme
import org.mpc.shared.MR
import org.mpc.shared.eeclass_show_original
import org.mpc.shared.eeclass_translate
import org.mpc.shared.eeclass_translated_on_device
import org.mpc.shared.eeclass_translation_failed

private const val TAG = "BulletinTranslation"

/** An announcement's title and body in the reader's language. */
internal class TranslatedBulletin(
    val title: String,
    val body: String,
)

/** Translates one announcement on the device when asked, and keeps the result so switching back and forth is instant. */
internal class BulletinTranslation(
    private val translator: OnDeviceTranslator,
    private val title: String,
    private val body: String,
    private val scope: CoroutineScope,
) {
    private var translated by mutableStateOf<TranslatedBulletin?>(null)
    var showsTranslation by mutableStateOf(false)
        private set
    var isTranslating by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    /** The translation while the reader has it switched on. */
    val shown: TranslatedBulletin? get() = translated?.takeIf { showsTranslation }

    fun toggle() {
        when {
            showsTranslation -> showsTranslation = false
            translated != null -> showsTranslation = true
            else -> scope.launch { translate() }
        }
    }

    private suspend fun translate() {
        isTranslating = true
        failed = false
        try {
            val (translatedTitle, translatedBody) = translator.translate(listOf(title, body))
            translated = TranslatedBulletin(translatedTitle, translatedBody)
            showsTranslation = true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            // ML Kit reports a failed model download or translation as a plain Exception.
            Log.w(TAG, "On-device translation failed", error)
            failed = true
        } finally {
            isTranslating = false
        }
    }
}

/** Translation for this announcement, or null when the reader reads Chinese or the device cannot translate into their language. */
@Composable
internal fun rememberBulletinTranslation(title: String, body: String): BulletinTranslation? {
    // The configuration follows the in-app language, so this is the language the reader chose.
    val language = LocalConfiguration.current.locales[0].language
    val scope = rememberCoroutineScope()
    return remember(language, title, body) {
        OnDeviceTranslator.into(language)?.let { BulletinTranslation(it, title, body, scope) }
    }
}

@Composable
internal fun BulletinTranslateButton(
    translation: BulletinTranslation,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = translation::toggle, enabled = !translation.isTranslating, modifier = modifier) {
        if (translation.isTranslating) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            text = stringResource(if (translation.showsTranslation) MR.strings.eeclass_show_original else MR.strings.eeclass_translate),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Says the text was machine translated, or that translating failed. */
@Composable
internal fun BulletinTranslationNote(
    translation: BulletinTranslation,
    modifier: Modifier = Modifier,
) {
    val note = when {
        translation.shown != null -> MR.strings.eeclass_translated_on_device
        translation.failed -> MR.strings.eeclass_translation_failed
        else -> return
    }
    Text(
        text = stringResource(note),
        style = MpcTheme.typography.supporting,
        color = MpcTheme.colors.labelSecondary,
        modifier = modifier,
    )
}
