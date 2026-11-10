package org.mpc.domain.model

/**
 * A language the app ships UI text in. [tag] is the BCP 47 tag moko-resources and the backend take, null for the
 * device language. [nativeName] is written in the language itself, so a reader finds theirs whatever the UI shows.
 */
enum class AppLanguage(
    val tag: String?,
    val nativeName: String?,
) {
    SYSTEM(null, null),
    ENGLISH("en", "English"),
    TRADITIONAL_CHINESE("zh-TW", "繁體中文"),
    VIETNAMESE("vi", "Tiếng Việt"),
}
