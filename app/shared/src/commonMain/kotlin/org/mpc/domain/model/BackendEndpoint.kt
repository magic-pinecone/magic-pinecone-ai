package org.mpc.domain.model

private val backendBaseUrlPattern = Regex("""https?://[^\s/?#]+(/[^\s?#]*)?""", RegexOption.IGNORE_CASE)

/**
 * Returns the canonical base URL of a Magic Pinecone backend, without a trailing slash,
 * or null when [input] is not an http(s) address.
 */
fun normalizeBackendBaseUrl(input: String): String? = input
    .trim()
    .trimEnd('/')
    .takeIf(backendBaseUrlPattern::matches)
