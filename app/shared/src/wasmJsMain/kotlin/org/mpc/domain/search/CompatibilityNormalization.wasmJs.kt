package org.mpc.domain.search

internal actual fun normalizeCompatibility(text: String): String = normalizeNfkc(text)

private fun normalizeNfkc(text: String): String = js("text.normalize('NFKC')")
