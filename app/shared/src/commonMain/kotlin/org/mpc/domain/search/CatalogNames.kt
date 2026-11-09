package org.mpc.domain.search

/**
 * The catalog names a spoken or typed [name] stands for: the one it equals once normalized, or else every name it is
 * part of ("王") or that is part of it ("王小明老師"). Empty when the catalog has no such name.
 */
fun matchCatalogNames(
    name: String,
    choices: List<String>,
): List<String> {
    val spoken = normalizeForSearch(name)
    if (spoken.isEmpty()) return emptyList()
    val catalog = choices.map { it to normalizeForSearch(it) }
    val exact = catalog.filter { (_, normalized) -> normalized == spoken }
    // A one-character catalog name would sit inside too many spoken names to mean any of them.
    val matches = exact.ifEmpty {
        catalog.filter { (_, normalized) -> spoken in normalized || (normalized.length > 1 && normalized in spoken) }
    }
    return matches.map { it.first }
}
