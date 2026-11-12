package org.mpc.presentation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mpc.presentation.translation.TextTranslator
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "OnDeviceTranslator"

/** Translates campus text, which is Chinese, into [target] with ML Kit on the device. */
internal class OnDeviceTranslator private constructor(
    private val target: String,
) : TextTranslator {
    /** Translates each line of [texts] and keeps blank lines, so paragraphs stay where they were. */
    override suspend fun translate(texts: List<String>): List<String> {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.CHINESE)
            .setTargetLanguage(target)
            .build()
        val client = Translation.getClient(options)
        try {
            // The first use downloads the language model, about 30 MB; later translations run offline.
            client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            return texts.map { text ->
                text.lines().map { line -> if (line.isBlank()) line else client.translate(line).await() }.joinToString("\n")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            Log.w(TAG, "On-device translation into $target failed", error)
            throw error
        } finally {
            client.close()
        }
    }

    companion object {
        /** A translator into [language], or null when that is Chinese or ML Kit cannot translate into it. */
        fun into(language: String): OnDeviceTranslator? {
            val target = TranslateLanguage.fromLanguageTag(language) ?: return null
            return OnDeviceTranslator(target).takeUnless { target == TranslateLanguage.CHINESE }
        }
    }
}

/** A translator into the in-app language, which the configuration follows. */
@Composable
internal fun rememberOnDeviceTranslator(): TextTranslator? {
    val language = LocalConfiguration.current.locales[0].language
    return remember(language) { OnDeviceTranslator.into(language) }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
