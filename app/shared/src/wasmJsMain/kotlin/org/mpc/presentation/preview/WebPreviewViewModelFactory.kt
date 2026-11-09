package org.mpc.presentation.preview

import androidx.lifecycle.ViewModel
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import kotlinx.browser.window
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.mpc.core.createHttpClient
import org.mpc.data.repository.DefaultClubRepository
import org.mpc.domain.repository.BackendEndpointRepository
import org.mpc.domain.repository.PortalFavoritesRepository
import org.mpc.domain.session.CampusSessions
import org.mpc.domain.session.CampusSite
import org.mpc.domain.session.PortalSignIn
import org.mpc.domain.session.PortalStatus
import org.mpc.domain.session.Renewal
import org.mpc.domain.session.SiteFile
import org.mpc.domain.session.SiteFollowUp
import org.mpc.domain.session.SitePath
import org.mpc.domain.session.SiteResult
import org.mpc.domain.session.SiteStatus
import org.mpc.domain.session.TransferProgress
import org.mpc.domain.session.Unavailability
import org.mpc.presentation.viewModel.CampusSessionViewModel
import org.mpc.presentation.viewModel.ClubViewModel
import org.mpc.presentation.viewModel.PortalViewModel
import kotlin.reflect.KClass

/** The ViewModels preview scenarios ask for, backed by in-memory state instead of the app graph. */
internal object WebPreviewViewModelFactory : MetroViewModelFactory() {
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel> = mapOf(
        PortalViewModel::class to { PortalViewModel(InMemoryPortalFavoritesRepository()) },
        CampusSessionViewModel::class to { CampusSessionViewModel(SignedOutCampusSessions) },
        ClubViewModel::class to { ClubViewModel(DefaultClubRepository(createHttpClient(), PreviewBackendEndpoint)) },
    )
}

/** The backend compose.yaml publishes on port 8000 of the host serving this page. */
private object PreviewBackendEndpoint : BackendEndpointRepository {
    override val baseUrl: Flow<String?> = MutableStateFlow("http://${window.location.hostname}:8000")

    override suspend fun setBaseUrl(baseUrl: String?) = Unit
}

private class InMemoryPortalFavoritesRepository : PortalFavoritesRepository {
    private val ids = MutableStateFlow(emptyList<String>())

    override val favoriteIds: Flow<List<String>> = ids

    override suspend fun updateFavoriteIds(transform: (List<String>) -> List<String>) = ids.update(transform)
}

/** Signed out of Portal with every site unreachable, since the browser cannot reach campus sites cross-origin. */
private object SignedOutCampusSessions : CampusSessions {
    override val portal: StateFlow<PortalStatus> = MutableStateFlow(PortalStatus.SignedOut)

    private val siteStatus = MutableStateFlow(SiteStatus.SignedOut(Renewal.PORTAL_SIGN_IN_BY_USER))

    override fun status(site: CampusSite): StateFlow<SiteStatus> = siteStatus

    override suspend fun refreshPortal() = Unit

    override suspend fun <T> fetch(site: CampusSite, path: SitePath, parse: (String) -> T): SiteResult<T> = unreachable

    override suspend fun fetchFile(site: CampusSite, path: SitePath, onProgress: TransferProgress?): SiteResult<SiteFile> = unreachable

    override suspend fun <T> fetchThen(
        site: CampusSite,
        path: SitePath,
        then: suspend SiteFollowUp.(html: String) -> T?,
    ): SiteResult<T> = unreachable

    override suspend fun refresh(site: CampusSite) = Unit

    override fun browserEntry(url: String): String? = null

    override suspend fun browserPage(url: String, loading: Boolean) = Unit

    override suspend fun signInToPortal(): PortalSignIn = PortalSignIn.OpenBrowser("about:blank")

    override suspend fun signOut(site: CampusSite) = Unit

    override suspend fun signOutEverywhere() = Unit

    private val unreachable = SiteResult.Unavailable(Unavailability.NETWORK)
}
