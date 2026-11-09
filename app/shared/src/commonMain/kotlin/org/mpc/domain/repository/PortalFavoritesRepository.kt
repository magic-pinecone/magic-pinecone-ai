package org.mpc.domain.repository

import kotlinx.coroutines.flow.Flow

interface PortalFavoritesRepository {
    /** Favorite Portal shortcut ids in the user's order. */
    val favoriteIds: Flow<List<String>>

    /** Replaces the favorites with [transform] applied to the stored ones, atomically. */
    suspend fun updateFavoriteIds(transform: (List<String>) -> List<String>)
}
