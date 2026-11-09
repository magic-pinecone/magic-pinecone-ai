package org.mpc.domain.search

import platform.Foundation.NSString
import platform.Foundation.precomposedStringWithCompatibilityMapping

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun normalizeCompatibility(text: String): String = (text as NSString).precomposedStringWithCompatibilityMapping
