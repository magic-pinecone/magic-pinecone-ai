package org.mpc.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import dev.icerock.moko.resources.desc.StringDesc
import org.mpc.domain.model.AppLanguage

/** The language [ProvideAppLanguage] shows the UI in; screens with backend text ask for it in this language too. */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.SYSTEM }

/**
 * Shows [content] in [language]. moko resolves each string once, so a new language re-keys the tree to resolve
 * them again. This works where moko reads [StringDesc.localeType], the browser and iOS; Android Compose reads the
 * Configuration instead and needs AppCompatDelegate (docs/i18n-plan.md, IL).
 */
@Composable
fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    remember(language) {
        StringDesc.localeType = language.tag?.let(StringDesc.LocaleType::Custom) ?: StringDesc.LocaleType.System
    }
    key(language) {
        CompositionLocalProvider(LocalAppLanguage provides language, content = content)
    }
}
