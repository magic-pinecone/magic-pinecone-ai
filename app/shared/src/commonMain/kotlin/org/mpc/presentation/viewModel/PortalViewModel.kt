package org.mpc.presentation.viewModel

import androidx.datastore.core.IOException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mpc.data.portal.addingFavorite
import org.mpc.data.portal.movingFavorite
import org.mpc.data.portal.removingFavorite
import org.mpc.domain.repository.PortalFavoritesRepository

@Inject
@ViewModelKey
@ContributesIntoMap(
    scope = AppScope::class,
    binding = binding<ViewModel>(),
)
class PortalViewModel(
    private val favorites: PortalFavoritesRepository,
) : ViewModel() {
    /** Null until the stored favorites load. */
    val favoriteIds: StateFlow<List<String>?> = favorites.favoriteIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun addFavorite(id: String) = update { it.addingFavorite(id) }

    fun removeFavorite(id: String) = update { it.removingFavorite(id) }

    fun moveFavorite(id: String, offset: Int) = update { it.movingFavorite(id, offset) }

    private fun update(transform: (List<String>) -> List<String>) {
        viewModelScope.launch {
            // A failed write leaves the stored favorites, which the screen already shows, unchanged.
            try {
                favorites.updateFavoriteIds(transform)
            } catch (_: IOException) {
            }
        }
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
