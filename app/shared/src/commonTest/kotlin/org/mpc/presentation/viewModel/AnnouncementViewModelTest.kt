package org.mpc.presentation.viewModel

import de.infix.testBalloon.framework.core.testSuite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mpc.data.repository.BackendNotConfiguredException
import org.mpc.domain.model.AnnouncementFeed
import org.mpc.domain.model.AnnouncementScope
import org.mpc.domain.model.AnnouncementSummary
import org.mpc.domain.model.AppSettings
import org.mpc.domain.model.AppThemeMode
import org.mpc.domain.model.PortalAuthenticationMode
import org.mpc.domain.model.TimetableColorMode
import org.mpc.domain.model.UserAffiliation
import org.mpc.domain.repository.AnnouncementRepository
import org.mpc.domain.repository.AppSettingsRepository
import org.mpc.domain.repository.BackendEndpointRepository
import org.mpc.presentation.state.AnnouncementLoadStatus
import kotlin.test.assertEquals
import kotlin.time.Instant

private val sampleAnnouncements = listOf(
    AnnouncementSummary(
        announcementId = "scholarship:1",
        scope = AnnouncementScope.AFFILIATION,
        sourceId = "scholarship",
        category = "獎助學金",
        unitName = "學務處生活輔導組",
        affiliations = listOf(UserAffiliation.UNDERGRADUATE),
        title = "大學部清寒獎學金",
        summary = mapOf("獎助金額" to "10000 元"),
        publishedDate = "2026-10-30",
        url = "https://cis.ncu.edu.tw/Scholarship/1",
        isPinned = false,
    ),
    AnnouncementSummary(
        announcementId = "office_oaa:2",
        scope = AnnouncementScope.AFFILIATION,
        sourceId = "office_oaa",
        category = "教務訊息",
        unitName = "教務處",
        affiliations = listOf(UserAffiliation.ALL),
        title = "【置頂】115學年度選課時程公告",
        publishedDate = "2026-10-25",
        url = "https://www.oaa.ncu.edu.tw/2",
        isPinned = true,
    ),
    AnnouncementSummary(
        announcementId = "dept_csie:3",
        scope = AnnouncementScope.DEPARTMENT,
        sourceId = "dept_csie",
        category = "系所公告",
        unitName = "資訊工程學系",
        collegeName = "資訊電機學院",
        departmentName = "資訊工程學系",
        title = "資工系碩士班口試辦法",
        publishedDate = "2026-11-01",
        url = "https://www.csie.ncu.edu.tw/3",
    ),
    AnnouncementSummary(
        announcementId = "dept_ee:4",
        scope = AnnouncementScope.DEPARTMENT,
        sourceId = "dept_ee",
        category = "系所公告",
        unitName = "電機工程學系",
        collegeName = "資訊電機學院",
        departmentName = "電機工程學系",
        title = "電機系專題競賽公告",
        publishedDate = "2026-11-02",
        url = "https://www.ee.ncu.edu.tw/4",
    ),
)

private class FakeAnnouncementRepository(
    private val endpoint: FakeBackendEndpointRepository,
    var items: List<AnnouncementSummary> = sampleAnnouncements,
) : AnnouncementRepository {
    override suspend fun fetchAnnouncements(forceRefresh: Boolean): AnnouncementFeed {
        endpoint.baseUrl.value ?: throw BackendNotConfiguredException()
        return AnnouncementFeed(
            lastUpdated = Instant.parse("2026-11-10T04:00:00Z"),
            announcements = items,
        )
    }
}

private class FakeAppSettingsRepository(
    initial: AppSettings = AppSettings(),
) : AppSettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun setThemeMode(themeMode: AppThemeMode) {
        settings.value = settings.value.copy(themeMode = themeMode)
    }

    override suspend fun setPortalAuthenticationMode(authenticationMode: PortalAuthenticationMode) {
        settings.value = settings.value.copy(portalAuthenticationMode = authenticationMode)
    }

    override suspend fun setTimetableColorMode(colorMode: TimetableColorMode) {
        settings.value = settings.value.copy(timetableColorMode = colorMode)
    }

    override suspend fun setUserAffiliation(affiliation: UserAffiliation) {
        settings.value = settings.value.copy(userAffiliation = affiliation)
    }

    override suspend fun setUserDepartment(department: String) {
        settings.value = settings.value.copy(userDepartment = department)
    }
}

private class FakeBackendEndpointRepository(
    baseUrl: String? = "http://10.0.2.2:8000",
) : BackendEndpointRepository {
    override val baseUrl = MutableStateFlow(baseUrl)

    override suspend fun setBaseUrl(baseUrl: String?) {
        this.baseUrl.value = baseUrl
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun announcementViewModelTest(
    baseUrl: String? = "http://10.0.2.2:8000",
    body: suspend TestScope.(AnnouncementViewModel, FakeBackendEndpointRepository) -> Unit,
) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    try {
        val endpoint = FakeBackendEndpointRepository(baseUrl)
        val repository = FakeAnnouncementRepository(endpoint)
        val settings = FakeAppSettingsRepository()
        val viewModel = AnnouncementViewModel(repository, settings, endpoint)
        testScheduler.advanceUntilIdle()
        body(viewModel, endpoint)
    } finally {
        Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
val announcementViewModelTests by testSuite {
    test("reports NEEDS_BACKEND until a backend address is configured") {
        announcementViewModelTest(baseUrl = null) { viewModel, endpoint ->
            assertEquals(AnnouncementLoadStatus.NEEDS_BACKEND, viewModel.uiState.value.status)

            endpoint.setBaseUrl("http://10.0.2.2:8000")
            testScheduler.advanceUntilIdle()

            assertEquals(AnnouncementLoadStatus.LOADED, viewModel.uiState.value.status)
            assertEquals(2, viewModel.uiState.value.filteredAnnouncements.size)
        }
    }

    test("filters affiliation announcements by user affiliation and sorts pinned items first") {
        announcementViewModelTest { viewModel, _ ->
            assertEquals(
                listOf("office_oaa:2", "scholarship:1"),
                viewModel.uiState.value.filteredAnnouncements.map { it.announcementId },
            )

            viewModel.selectAffiliation(UserAffiliation.GRADUATE)
            testScheduler.advanceUntilIdle()

            assertEquals(
                listOf("office_oaa:2"),
                viewModel.uiState.value.filteredAnnouncements.map { it.announcementId },
            )
        }
    }

    test("switches to department scope and filters by selected department and search query") {
        announcementViewModelTest { viewModel, _ ->
            viewModel.selectScope(AnnouncementScope.DEPARTMENT)
            testScheduler.advanceUntilIdle()

            assertEquals(
                listOf("dept_ee:4", "dept_csie:3"),
                viewModel.uiState.value.filteredAnnouncements.map { it.announcementId },
            )

            viewModel.selectDepartment("資訊工程學系")
            testScheduler.advanceUntilIdle()

            assertEquals(
                listOf("dept_csie:3"),
                viewModel.uiState.value.filteredAnnouncements.map { it.announcementId },
            )

            viewModel.selectDepartment("")
            viewModel.updateSearchQuery("專題")
            testScheduler.advanceUntilIdle()

            assertEquals(
                listOf("dept_ee:4"),
                viewModel.uiState.value.filteredAnnouncements.map { it.announcementId },
            )
        }
    }
}
