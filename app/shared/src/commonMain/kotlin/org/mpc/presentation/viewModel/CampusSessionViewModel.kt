package org.mpc.presentation.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.mpc.domain.session.CampusSessions
import org.mpc.domain.session.PortalSignIn
import org.mpc.domain.session.PortalStatus

@Inject
@ViewModelKey
@ContributesIntoMap(
    scope = AppScope::class,
    binding = binding<ViewModel>(),
)
class CampusSessionViewModel(
    private val sessions: CampusSessions,
) : ViewModel() {
    val portal: StateFlow<PortalStatus> = sessions.portal

    fun refreshPortal() {
        viewModelScope.launch { sessions.refreshPortal() }
    }

    /** A user tap: lifts any block on Portal sign-in. Opens the returned page in the WebView. */
    suspend fun signIn(): PortalSignIn = sessions.signInToPortal()

    fun signOut() {
        viewModelScope.launch { sessions.signOutEverywhere() }
    }

    suspend fun browserPage(url: String, loading: Boolean) {
        sessions.browserPage(url, loading)
    }
}
