package org.mpc.data.portal

import org.mpc.domain.model.PortalShortcutItem
import org.mpc.domain.model.PortalShortcutSection

/** What the Portal tab lists: the user's favorites in their order, then the whole catalog. */
data class PortalHome(
    val favorites: List<PortalShortcutItem>,
    val sections: List<PortalShortcutSection>,
) {
    val isEmpty: Boolean get() = favorites.isEmpty() && sections.isEmpty()
}

/** Resolves [favoriteIds] against [catalog], dropping ids it no longer lists, and filters both by [query]. */
fun portalHome(
    favoriteIds: List<String>,
    query: String,
    catalog: List<PortalShortcutSection> = defaultPortalShortcutSections,
): PortalHome {
    val itemsById = catalog.flatMap { it.items }.associateBy { it.id }
    val normalizedQuery = query.trim()
    return PortalHome(
        favorites = favoriteIds
            .mapNotNull(itemsById::get)
            .filter { it.label.contains(normalizedQuery, ignoreCase = true) },
        sections = filterPortalShortcutSections(catalog, normalizedQuery),
    )
}

fun List<String>.addingFavorite(id: String): List<String> = if (id in this) this else this + id

fun List<String>.removingFavorite(id: String): List<String> = this - id

/** Moves [id] by [offset] places, stopping at either end. */
fun List<String>.movingFavorite(id: String, offset: Int): List<String> {
    val from = indexOf(id)
    if (from < 0) return this
    val to = (from + offset).coerceIn(indices)
    return toMutableList().apply { add(to, removeAt(from)) }
}
