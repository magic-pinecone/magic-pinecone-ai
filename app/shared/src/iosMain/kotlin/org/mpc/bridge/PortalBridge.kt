package org.mpc.bridge

import androidx.datastore.core.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.mpc.data.portal.PortalHome
import org.mpc.data.portal.addingFavorite
import org.mpc.data.portal.portalHome
import org.mpc.data.portal.removingFavorite
import org.mpc.domain.repository.PortalFavoritesRepository
import org.mpc.domain.session.CampusSessions
import org.mpc.domain.session.PortalSignIn
import org.mpc.presentation.model.PortalAccount
import org.mpc.presentation.model.toPortalAccount

/** What the native Portal tab reads and does: the account row, favorites, and the catalog. */
class PortalBridge internal constructor(
    private val sessions: CampusSessions,
    private val favorites: PortalFavoritesRepository,
    private val scope: CoroutineScope = MainScope(),
) {
    val account: Flow<PortalAccount> = sessions.portal.map { it.toPortalAccount() }.distinctUntilChanged()

    private val failedWrites = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // The list shows edits before they are stored, so a failed write resends the stored favorites to undo it.
    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteIds: Flow<List<String>> = failedWrites
        .onStart { emit(Unit) }
        .flatMapLatest { favorites.favoriteIds }

    fun home(favoriteIds: List<String>, query: String): PortalHome = portalHome(favoriteIds, query)

    fun refresh() {
        scope.launch { sessions.refreshPortal() }
    }

    /** A user tap. Returns the page to open in the in-app browser. */
    @Throws(CancellationException::class)
    suspend fun signInUrl(): String = when (val signIn = sessions.signInToPortal()) {
        is PortalSignIn.OpenBrowser -> signIn.url
    }

    fun signOut() {
        scope.launch { sessions.signOutEverywhere() }
    }

    fun addFavorite(id: String) = update { it.addingFavorite(id) }

    fun removeFavorite(id: String) = update { it.removingFavorite(id) }

    /** Saves a reordering the list made natively. */
    fun setFavoriteIds(ids: List<String>) = update { ids }

    private fun update(transform: (List<String>) -> List<String>) {
        scope.launch {
            try {
                favorites.updateFavoriteIds(transform)
            } catch (_: IOException) {
                failedWrites.tryEmit(Unit)
            }
        }
    }
}
