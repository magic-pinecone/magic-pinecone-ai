package org.mpc

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.mpc.domain.model.AppLanguage
import java.util.Locale

internal fun setAndroidAppLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
}

internal fun androidAppLanguage(): AppLanguage? {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return AppLanguage.SYSTEM

    return when (locales.get(0)?.toLanguageTag()?.lowercase(Locale.ROOT)) {
        "en", "en-us" -> AppLanguage.ENGLISH
        "zh-tw", "zh-hant-tw" -> AppLanguage.TRADITIONAL_CHINESE
        "vi", "vi-vn" -> AppLanguage.VIETNAMESE
        else -> null
    }
}
