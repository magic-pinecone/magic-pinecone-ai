package org.mpc.domain.search

import java.text.Normalizer

internal actual fun normalizeCompatibility(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
